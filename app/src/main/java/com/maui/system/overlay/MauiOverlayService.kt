package com.maui.system.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.maui.system.ui.MainActivity
import com.maui.system.R
import com.maui.system.core.AppHelper
import com.maui.system.core.Cleaner
import com.maui.system.core.DeviceProfiler
import com.maui.system.core.PerformanceSampler
import com.maui.system.core.Prefs
import com.maui.system.util.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Maui Layers — the system that lives on top of Android.
 *
 * A draggable bubble opens a control panel with live device telemetry and
 * one-tap actions; a HUD can be pinned over any app; and a game toolbar
 * appears automatically when a game is detected in the foreground.
 */
class MauiOverlayService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.maui.system.START"
        const val ACTION_STOP = "com.maui.system.STOP"
        const val ACTION_TOGGLE_PANEL = "com.maui.system.TOGGLE_PANEL"
        const val ACTION_TOGGLE_HUD = "com.maui.system.TOGGLE_HUD"
        const val ACTION_TOGGLE_GAME = "com.maui.system.TOGGLE_GAME"
        const val ACTION_CLEAN = "com.maui.system.CLEAN"
        const val ACTION_BOOST = "com.maui.system.BOOST"
        const val CHANNEL_ID = "maui_layers"
        private const val NOTIF_ID = 4711

        fun start(context: Context) {
            val i = Intent(context, MauiOverlayService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MauiOverlayService::class.java).setAction(ACTION_STOP))
        }

        fun togglePanel(context: Context) {
            context.startService(Intent(context, MauiOverlayService::class.java).setAction(ACTION_TOGGLE_PANEL))
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var windowManager: WindowManager
    private lateinit var sampler: PerformanceSampler

    private var bubble: BubbleView? = null
    private var panel: MauiPanelView? = null
    private var hud: HudView? = null
    private var gameToolbar: GameToolbarView? = null

    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var gameParams: WindowManager.LayoutParams? = null

    private var samplerJob: Job? = null
    private var gameWatchJob: Job? = null
    private var lastGamePackage: String? = null
    private var currentForeground: String? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sampler = PerformanceSampler(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PANEL -> togglePanel()
            ACTION_TOGGLE_HUD -> toggleHud()
            ACTION_TOGGLE_GAME -> toggleGameMode()
            ACTION_CLEAN -> doClean()
            ACTION_BOOST -> doBoost()
            else -> {
                startForegroundSafely()
                showBubble()
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ UI

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        if (bubble != null) return
        val ctx = this
        val b = BubbleView(
            this,
            onTap = { togglePanel() },
            onDrag = { x, y -> moveBubble(x, y) },
            onDragEnd = { x, y -> snapBubble(x, y) },
            onLongPress = { prefs.overlayEnabled = false; stopEverything() }
        )

        val size = OverlayTheme.dp(this, prefs.bubbleSize.toFloat())
        val p = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (prefs.overlayX >= 0) prefs.overlayX else 0
            y = if (prefs.overlayY >= 0) prefs.overlayY else OverlayTheme.dp(this@MauiOverlayService, 220f)
        }
        try {
            windowManager.addView(b, p)
            bubble = b
            bubbleParams = p
            b.startPulse()
        } catch (t: Throwable) {
            bubble = null
        }
        startLoops()
    }

    private fun moveBubble(rawX: Int, rawY: Int) {
        val p = bubbleParams ?: return
        val metrics = resources.displayMetrics
        p.x = (rawX - size(p) / 2).coerceIn(0, metrics.widthPixels - size(p))
        p.y = (rawY - size(p) / 2).coerceIn(0, metrics.heightPixels - size(p))
        try { windowManager.updateViewLayout(bubble, p) } catch (t: Throwable) { }
    }

    private fun snapBubble(rawX: Int, rawY: Int) {
        val p = bubbleParams ?: return
        val metrics = resources.displayMetrics
        val s = size(p)
        val left = rawX < metrics.widthPixels / 2
        p.x = if (left) 0 else metrics.widthPixels - s
        p.y = (rawY - s / 2).coerceIn(0, metrics.heightPixels - s)
        prefs.overlayX = p.x
        prefs.overlayY = p.y
        prefs.overlayEdge = if (left) "left" else "right"
        try { windowManager.updateViewLayout(bubble, p) } catch (t: Throwable) { }
    }

    private fun size(p: WindowManager.LayoutParams): Int = if (p.width > 0) p.width else OverlayTheme.dp(this, 58f)

    private fun togglePanel() {
        if (panel != null) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panel != null) return
        val actions = object : MauiPanelView.Actions {
            override fun onClean() { doClean(); hidePanel() }
            override fun onBoost() { doBoost(); hidePanel() }
            override fun onToggleHud(enabled: Boolean) { setHud(enabled) }
            override fun onToggleGameMode(enabled: Boolean) { setGameMode(enabled) }
            override fun onToggleGameToolbar(enabled: Boolean) { prefs.gameToolbarEnabled = enabled; updateGameToolbar() }
            override fun onOpenFiles() { openMaui(com.maui.system.ui.FileExplorerActivity::class.java); hidePanel() }
            override fun onOpenApps() { openMaui(com.maui.system.ui.AppsActivity::class.java); hidePanel() }
            override fun onOpenSettings() { openMaui(com.maui.system.ui.SettingsActivity::class.java); hidePanel() }
            override fun onClose() { hidePanel() }
        }
        val view = MauiPanelView(this, actions)
        val metrics = resources.displayMetrics
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = OverlayTheme.dp(this@MauiOverlayService, 10f)
            y = OverlayTheme.dp(this@MauiOverlayService, 90f)
            width = (metrics.widthPixels - OverlayTheme.dp(this@MauiOverlayService, 20f)).coerceAtMost(
                OverlayTheme.dp(this@MauiOverlayService, 360f)
            )
        }
        try {
            windowManager.addView(view, p)
            panel = view
            panelParams = p
            view.setToggleStates(prefs.hudEnabled, prefs.gameMode, prefs.gameToolbarEnabled)
        } catch (t: Throwable) {
            panel = null
        }
    }

    private fun hidePanel() {
        val v = panel ?: return
        try { windowManager.removeView(v) } catch (t: Throwable) { }
        panel = null
    }

    private fun toggleHud() = setHud(!prefs.hudEnabled)

    private fun setHud(enabled: Boolean) {
        prefs.hudEnabled = enabled
        if (enabled) {
            showHud()
        } else {
            hideHud()
        }
        panel?.setToggleStates(prefs.hudEnabled, prefs.gameMode, prefs.gameToolbarEnabled)
    }

    private fun showHud() {
        if (hud != null) return
        val view = HudView(this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = OverlayTheme.dp(this@MauiOverlayService, 8f)
            y = OverlayTheme.dp(this@MauiOverlayService, 8f)
        }
        try {
            windowManager.addView(view, p)
            hud = view
            hudParams = p
        } catch (t: Throwable) {
            hud = null
        }
    }

    private fun hideHud() {
        val v = hud ?: return
        try { windowManager.removeView(v) } catch (t: Throwable) { }
        hud = null
    }

    private fun showGameToolbar(gameLabel: String) {
        if (gameToolbar != null) return
        val actions = object : GameToolbarView.Actions {
            override fun onBoost() { doBoost() }
            override fun onClean() { doClean() }
            override fun onToggleHud() { toggleHud() }
            override fun onExitGame() { stopEverything() }
        }
        val view = GameToolbarView(this, actions)
        view.setGameName(gameLabel)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = OverlayTheme.dp(this@MauiOverlayService, 6f)
        }
        try {
            windowManager.addView(view, p)
            gameToolbar = view
            gameParams = p
        } catch (t: Throwable) {
            gameToolbar = null
        }
    }

    private fun hideGameToolbar() {
        val v = gameToolbar ?: return
        try { windowManager.removeView(v) } catch (t: Throwable) { }
        gameToolbar = null
    }

    // ------------------------------------------------------------- actions

    private fun doClean() {
        lifecycleScope.launch {
            val report = Cleaner.cleanOwnCaches(this@MauiOverlayService)
            notifyUser(
                "Maui Cleaner",
                if (report.freedBytes > 0)
                    "Reclaimed ${Format.size(report.freedBytes)} from app caches"
                else "Nothing to clean — storage is already tidy"
            )
        }
    }

    private fun doBoost() {
        lifecycleScope.launch {
            val report = Cleaner.boostMemory(this@MauiOverlayService)
            notifyUser("Maui Boost", report.message)
        }
    }

    private fun toggleGameMode() = setGameMode(!prefs.gameMode)

    private fun setGameMode(enabled: Boolean) {
        prefs.gameMode = enabled
        applyPerformanceMode(enabled)
        panel?.setToggleStates(prefs.hudEnabled, prefs.gameMode, prefs.gameToolbarEnabled)
        if (enabled) {
            lifecycleScope.launch { Cleaner.boostMemory(this@MauiOverlayService) }
        }
    }

    /**
     * Game mode: kill the animation scaling to 0 (this genuinely removes
     * jank on most devices), drop the refresh overhead and trim background
     * apps. WRITE_SETTINGS is required for the animation scale.
     */
    private fun applyPerformanceMode(enabled: Boolean) {
        try {
            val scale = if (enabled) 0f else prefs.animationScale
            Settings.System.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
            Settings.System.putFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, scale)
            Settings.System.putFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, scale)
        } catch (t: Throwable) {
        }
        if (enabled) {
            AppHelper.killAllBackground(this)
            setDoNotDisturb(true)
        } else {
            setDoNotDisturb(false)
        }
    }

    /**
     * Silences notifications while gaming. Android only allows this when the
     * app holds the notification-policy access, so it degrades gracefully.
     */
    private fun setDoNotDisturb(enabled: Boolean) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return
            nm.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        } catch (t: Throwable) {
        }
    }

    private fun openMaui(target: Class<*>) {
        try {
            val i = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (t: Throwable) {
        }
    }

    private fun notifyUser(title: String, text: String) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val n = baseNotification()
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_maui_stat)
                .build()
            nm.notify(NOTIF_ID + 1, n)
        } catch (t: Throwable) {
        }
    }

    // -------------------------------------------------------------- loops

    private fun startLoops() {
        if (samplerJob == null) {
            samplerJob = lifecycleScope.launch {
                sampler.start()
                while (isActive) {
                    val snap = sampler.sample()
                    hud?.update(snap)
                    panel?.update(snap)
                    gameToolbar?.update(snap)
                    delay(1000)
                }
            }
        }
        if (gameWatchJob == null) {
            gameWatchJob = lifecycleScope.launch {
                while (isActive) {
                    updateGameToolbar()
                    delay(2500)
                }
            }
        }
    }

    private fun updateGameToolbar() {
        val fg = AppHelper.foregroundApp(this) ?: return
        currentForeground = fg.packageName
        if (fg.packageName == packageName) {
            hideGameToolbar()
            lastGamePackage = null
            return
        }
        val isGame = AppHelper.isGamePackage(fg.packageName)
        if (isGame && prefs.gameToolbarEnabled) {
            if (lastGamePackage != fg.packageName) {
                lastGamePackage = fg.packageName
                if (prefs.gameMode) {
                    lifecycleScope.launch { Cleaner.boostMemory(this@MauiOverlayService) }
                }
            }
            if (gameToolbar == null) showGameToolbar(fg.label)
        } else {
            hideGameToolbar()
            lastGamePackage = null
        }
    }

    // ---------------------------------------------------------- lifecycle

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_overlay),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_overlay_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun baseNotification(): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val togglePanel = PendingIntent.getService(
            this, 1,
            Intent(this, MauiOverlayService::class.java).setAction(ACTION_TOGGLE_PANEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleHud = PendingIntent.getService(
            this, 2,
            Intent(this, MauiOverlayService::class.java).setAction(ACTION_TOGGLE_HUD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val game = PendingIntent.getService(
            this, 3,
            Intent(this, MauiOverlayService::class.java).setAction(ACTION_TOGGLE_GAME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 4,
            Intent(this, MauiOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_maui_stat)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.action_panel), togglePanel)
            .addAction(0, getString(R.string.action_hud), toggleHud)
            .addAction(0, getString(R.string.action_game), game)
            .addAction(0, getString(R.string.action_stop), stop)
    }

    private fun startForegroundSafely() {
        try {
            val n = baseNotification().build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (t: Throwable) {
            try { startForeground(NOTIF_ID, baseNotification().build()) } catch (t2: Throwable) { }
        }
    }

    private fun stopEverything() {
        hidePanel()
        hideHud()
        hideGameToolbar()
        bubble?.stopPulse()
        bubble?.let { try { windowManager.removeView(it) } catch (t: Throwable) { } }
        bubble = null
        samplerJob?.cancel(); samplerJob = null
        gameWatchJob?.cancel(); gameWatchJob = null
        sampler.stop()
        prefs.overlayEnabled = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        hidePanel()
        hideHud()
        hideGameToolbar()
        bubble?.let { try { windowManager.removeView(it) } catch (t: Throwable) { } }
        bubble = null
        sampler.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
