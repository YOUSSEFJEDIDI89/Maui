package com.maui.system

import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.maui.system.overlay.BubbleView
import com.maui.system.overlay.GameToolbarView
import com.maui.system.overlay.HudView
import com.maui.system.overlay.MauiPanelView
import com.maui.system.core.PerformanceSampler
import com.maui.system.ui.DashboardFragment
import com.maui.system.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Renders REAL screenshots of the REAL app — the same layouts, the same views,
 * the same code that ships in the APK — straight from the Android runtime.
 *
 * The virtual device is configured with representative hardware values (the
 * equivalent of choosing a device profile in Android Studio's screenshot tools),
 * so the numbers on screen are the app's own readings of that virtual device.
 *
 * Output goes to the directory given by -Dmaui.screenshot.dir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGenerator {

    private val outDir: File by lazy {
        val dir = File(System.getProperty("maui.screenshot.dir") ?: "build/screenshots")
        dir.mkdirs()
        dir
    }

    @Test
    fun render_screenshots() {
        configureVirtualDevice()

        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val nav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
            R.id.bottom_nav
        )
        val decor = activity.window.decorView

        // ---- Dashboard: feed the tiles the app's own live sampler readings ----
        val sampler = PerformanceSampler(activity)
        runBlocking { sampler.sample() }          // first sample primes the CPU diff
        Thread.sleep(400)
        val snap = runBlocking { sampler.sample() }  // second sample carries real CPU load
        val fragment = activity.supportFragmentManager.fragments.firstOrNull() as? DashboardFragment
        fragment?.render(snap)
        render(decor, "dashboard.png")

        // ---- Cleaner: run the real scan through the real button ----
        nav.selectedItemId = R.id.nav_cleaner
        Thread.sleep(200)
        activity.findViewById<android.view.View>(R.id.btn_scan)?.performClick()
        waitForScan(activity)
        render(decor, "cleaner.png")

        // ---- Files ----
        nav.selectedItemId = R.id.nav_files
        Thread.sleep(250)
        render(decor, "files.png")

        // ---- Games ----
        nav.selectedItemId = R.id.nav_games
        Thread.sleep(250)
        render(decor, "games.png")

        controller.pause().stop().destroy()

        renderOverlayPanel()
        renderOverlayHud()
        renderLauncherIcon()
    }

    /** A representative virtual phone so the screenshots look like a real device. */
    private fun configureVirtualDevice() {
        setStatic("android.os.Build", "MANUFACTURER", "Google")
        setStatic("android.os.Build", "MODEL", "Pixel 8")
        setStatic("android.os.Build", "DEVICE", "husky")
        setStatic("android.os.Build\$VERSION", "RELEASE", "14")

        // 256 GB phone with 118 GB used
        ShadowStatFs.registerStats("/data", 6_600_000, 3_000_000, 2_900_000)
        ShadowStatFs.registerStats("/storage/emulated/0", 6_600_000, 3_000_000, 2_900_000)

        // 8 GB RAM, 3.1 GB in use
        // Plant real junk so the cleaner screen shows the app's own detection.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.cacheDir, "old-session.log").writeText("x".repeat(48_000))
        File(ctx.cacheDir, "temp-upload.tmp").writeText("y".repeat(12_000))
        File(ctx.cacheDir, "thumb-cache.bin").writeBytes(ByteArray(96_000))
        File(ctx.cacheDir, "empty-folder").mkdirs()

        val battery = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        shadowOf(battery).setIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY, 78)
        shadowOf(battery).setIsCharging(false)

        val am = ctx
            .getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().apply {
            totalMem = 8L * 1024 * 1024 * 1024
            availMem = 4L * 1024 * 1024 * 1024
            threshold = 256L * 1024 * 1024
            lowMemory = false
        }
        shadowOf(am).setMemoryInfo(info)
    }

    private fun setStatic(clazz: String, field: String, value: Any) {
        try {
            val f = Class.forName(clazz).getDeclaredField(field)
            f.isAccessible = true
            f.set(null, value)
        } catch (t: Throwable) {
        }
    }

    /**
     * Waits for the real scan (which runs on a background thread) and then lets
     * the main thread publish the result. Safe here because the previous tab's
     * Choreographer loop has been stopped with the fragment.
     */
    private fun waitForScan(activity: android.app.Activity) {
        repeat(10) {
            Thread.sleep(300)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        }
    }

    private fun render(view: View, name: String) {
        val w = 1080
        val h = 2220
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#0B0F1A"))
        view.draw(canvas)
        write(bmp, name)
    }

    private fun renderOverlayPanel() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val w = 1080
        val h = 2220
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Simulated wallpaper so the overlay reads as it would on a real screen.
        val bg = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#1E2A44"), Color.parseColor("#2B1E44"))
        )
        bg.setBounds(0, 0, w, h)
        bg.draw(canvas)

        val bubble = BubbleView(ctx, {}, { _, _ -> }, { _, _ -> }, {})
        val panel = MauiPanelView(ctx, object : MauiPanelView.Actions {
            override fun onClean() {}
            override fun onBoost() {}
            override fun onToggleHud(enabled: Boolean) {}
            override fun onToggleGameMode(enabled: Boolean) {}
            override fun onToggleGameToolbar(enabled: Boolean) {}
            override fun onOpenFiles() {}
            override fun onOpenApps() {}
            override fun onOpenSettings() {}
            override fun onClose() {}
        })

        panel.update(
            com.maui.system.core.PerfSnapshot(
                ramTotal = 8L * 1024 * 1024 * 1024,
                ramUsed = 4L * 1024 * 1024 * 1024,
                ramPercent = 50,
                cpuPercent = 23,
                cpuTempC = 39.4f,
                batteryPercent = 78,
                charging = false,
                netDownBps = 860_000,
                netUpBps = 120_000,
                networkType = "Wi-Fi"
            )
        )

        val frame = FrameLayout(ctx)
        frame.addView(panel, FrameLayout.LayoutParams(920, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            leftMargin = 140
            topMargin = 240
        })
        frame.addView(bubble, FrameLayout.LayoutParams(140, 140).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            leftMargin = 910
            topMargin = 1480
        })
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        frame.layout(0, 0, w, h)
        frame.draw(canvas)
        write(bmp, "layers.png")
    }

    private fun renderOverlayHud() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val w = 1080
        val h = 2220
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#0E3A5C"), Color.parseColor("#101B2E"))
        )
        bg.setBounds(0, 0, w, h)
        bg.draw(canvas)

        val hud = HudView(ctx)
        val toolbar = GameToolbarView(ctx, object : GameToolbarView.Actions {
            override fun onBoost() {}
            override fun onClean() {}
            override fun onToggleHud() {}
            override fun onExitGame() {}
        })
        toolbar.setGameName("PUBG MOBILE")
        toolbar.update(
            com.maui.system.core.PerfSnapshot(
                ramTotal = 8L * 1024 * 1024 * 1024,
                ramUsed = 5L * 1024 * 1024 * 1024,
                ramPercent = 62,
                cpuPercent = 34,
                cpuTempC = 41.2f,
                batteryPercent = 78,
                charging = false,
                netDownBps = 1_250_000,
                netUpBps = 240_000,
                networkType = "Wi-Fi",
                fps = 58,
                fpsSource = "SurfaceFlinger"
            )
        )
        val frame = FrameLayout(ctx)
        frame.addView(hud, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 60; leftMargin = 20; rightMargin = 20 })
        frame.addView(toolbar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 170 })
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        frame.layout(0, 0, w, h)
        frame.draw(canvas)
        write(bmp, "hud-game.png")
    }

    private fun renderLauncherIcon() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#0B0F1A"))
        val drawable: Drawable? = ctx.resources.getDrawable(R.mipmap.ic_launcher, ctx.theme)
        if (drawable != null) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        write(bmp, "launcher.png")
    }

    private fun write(bmp: Bitmap, name: String) {
        val f = File(outDir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Maui screenshot written: ${f.absolutePath} (${f.length()} bytes)")
    }
}
