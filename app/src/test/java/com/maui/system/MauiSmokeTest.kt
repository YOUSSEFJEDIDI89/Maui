package com.maui.system

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.maui.system.overlay.BubbleView
import com.maui.system.overlay.GameToolbarView
import com.maui.system.overlay.HudView
import com.maui.system.overlay.MauiPanelView
import com.maui.system.scanner.JunkCategory
import com.maui.system.scanner.StorageScanner
import com.maui.system.ui.AppsActivity
import com.maui.system.ui.FileExplorerActivity
import com.maui.system.ui.MainActivity
import com.maui.system.ui.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Smoke test: every screen of Maui is actually created and inflated on a real
 * Android runtime (Robolectric), and the scanner really finds junk on disk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MauiSmokeTest {

    @Test
    fun mainActivity_launches_with_all_navigation_tabs() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val nav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
            R.id.bottom_nav
        )
        assertNotNull(nav)
        assertEquals(5, nav.menu.size())
        val container = activity.findViewById<FrameLayout>(R.id.fragment_container)
        assertNotNull(container)
        assertTrue("dashboard fragment should be attached", container.childCount > 0)
        controller.pause().stop().destroy()
    }

    @Test
    fun dashboard_shows_live_stat_tiles() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val root = controller.get().findViewById<FrameLayout>(R.id.fragment_container)
        val tile = root.findViewById<View>(R.id.tile_ram)
        assertNotNull("RAM tile must be present", tile)
        val value = tile.findViewById<TextView>(R.id.stat_value)
        assertNotNull(value)
        controller.pause().stop().destroy()
    }

    @Test
    fun settings_activity_inflates_with_permission_rows() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).create().start().resume()
        val rows = controller.get().findViewById<android.widget.LinearLayout>(R.id.permissions_container)
        assertNotNull(rows)
        assertTrue("there must be permission rows", rows.childCount >= 5)
        controller.pause().stop().destroy()
    }

    @Test
    fun apps_and_file_explorer_activities_inflate() {
        val apps = Robolectric.buildActivity(AppsActivity::class.java).create().start().resume()
        assertNotNull(apps.get().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list))
        apps.pause().stop().destroy()

        val files = Robolectric.buildActivity(FileExplorerActivity::class.java).create().start().resume()
        assertNotNull(files.get().findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list))
        files.pause().stop().destroy()
    }

    @Test
    fun overlay_views_can_be_created() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        val bubble = BubbleView(ctx, {}, { _, _ -> }, { _, _ -> }, {})
        bubble.measure(0, 0)
        bubble.layout(0, 0, 200, 200)
        assertEquals(200, bubble.width)

        val hud = HudView(ctx)
        hud.measure(0, 0)
        hud.layout(0, 0, 400, 60)
        assertTrue("HUD must have children", hud.childCount > 0)

        val toolbar = GameToolbarView(ctx, object : GameToolbarView.Actions {
            override fun onBoost() {}
            override fun onClean() {}
            override fun onToggleHud() {}
            override fun onExitGame() {}
        })
        toolbar.measure(0, 0)
        toolbar.layout(0, 0, 600, 80)
        assertTrue("game toolbar must have children", toolbar.childCount > 0)

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
        panel.measure(0, 0)
        panel.layout(0, 0, 500, 700)
        assertTrue("panel must have children", panel.childCount > 0)
    }

    @Test
    fun scanner_really_finds_junk_files_on_disk() = kotlinx.coroutines.runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scanner = StorageScanner(ctx)

        // Plant real junk in the app cache directory and make sure it is found.
        val cache = ctx.cacheDir
        val tmp = File(cache, "maui-junk-test.log")
        tmp.writeText("x".repeat(2048))
        val empty = File(cache, "maui-empty-dir")
        empty.mkdirs()

        val result = scanner.scan(includeHidden = true, includeDuplicates = true)
        val foundLog = result.items.any {
            it.category == JunkCategory.LOGS && it.path == tmp.absolutePath
        }
        val foundEmpty = result.items.any {
            it.category == JunkCategory.EMPTY && it.path == empty.absolutePath
        }
        assertTrue("the .log file must be detected as junk", foundLog)
        assertTrue("the empty directory must be detected", foundEmpty)
        assertTrue("reclaimable size must be > 0", result.totalBytes > 0)
    }
}
