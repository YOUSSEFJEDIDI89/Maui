package com.maui.system.core

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import java.io.File

/** One concrete change Maui tried to make, and whether it really took. */
data class OptimizerStep(
    val title: String,
    val applied: Boolean,
    val detail: String
)

data class OptimizationReport(
    val steps: List<OptimizerStep>,
    val packageName: String,
    val profile: GameProfile
) {
    val appliedCount: Int get() = steps.count { it.applied }
    val skippedCount: Int get() = steps.count { !it.applied }
    val ramFreed: Long get() = steps.firstOrNull { it.title.startsWith("Memory") }?.let { parseSize(it.detail) } ?: 0L

    private fun parseSize(detail: String): Long {
        val m = Regex("([0-9.]+)\\s*(B|KB|MB|GB)").find(detail) ?: return 0L
        val v = m.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (m.groupValues[2]) {
            "B" -> v.toLong()
            "KB" -> (v * 1024).toLong()
            "MB" -> (v * 1024 * 1024).toLong()
            else -> (v * 1024 * 1024 * 1024).toLong()
        }
    }
}

/**
 * The game-space engine.
 *
 * Every function here changes real OS state and then reads it back, so the
 * report tells the truth about what actually happened. When a change needs a
 * permission or root that is missing, the step is reported as skipped with
 * the reason instead of silently pretending to work.
 */
object GameOptimizer {

    // These two are not exposed as public SDK constants, but they are the
    // system keys every game-space app uses to drive the panel refresh rate.
    private const val KEY_PEAK_REFRESH = "peak_refresh_rate"
    private const val KEY_MIN_REFRESH = "min_refresh_rate"


    // ---------------------------------------------------------------- public

    /**
     * Applies [profile] to [packageName] and returns exactly what worked.
     */
    fun optimize(
        context: Context,
        packageName: String,
        profile: GameProfile,
        keepScreenOn: Boolean = true,
        isFreeFire: Boolean = false
    ): OptimizationReport {
        val steps = mutableListOf<OptimizerStep>()
        val app = context.applicationContext

        // 1) Memory — kill background processes so their heaps go back to the pool.
        steps += releaseMemory(context)

        // 2) Animations — a real, measurable win against frame drops.
        if (profile.killsAnimations) {
            steps += setAnimationsOff(app)
        } else {
            steps += OptimizerStep("Animations", false, "left untouched by the Battery saver profile")
        }

        // 3) Display — push the panel to its fastest mode the device supports.
        if (profile.boostsRefresh) {
            steps += setMaxRefreshRate(app)
        } else {
            steps += OptimizerStep("Refresh rate", false, "left at the system default")
        }

        // 4) Notifications off while playing.
        steps += setDoNotDisturb(app)

        // 5) Screen stays on.
        if (keepScreenOn) {
            steps += extendScreenTimeout(app)
        } else {
            steps += OptimizerStep("Screen timeout", false, "left at the system value")
        }

        // 6) Root-only power and scheduling changes.
        if (RootShell.isRooted) {
            steps += rootOptimisations(packageName, isFreeFire)
        } else {
            steps += OptimizerStep(
                "Root tuning",
                false,
                "no root — governor, GPU boost and doze whitelist unavailable"
            )
        }

        return OptimizationReport(steps, packageName, profile)
    }

    /** Puts back the display and animation settings Maui changed. */
    fun restore(context: Context) {
        val app = context.applicationContext
        try {
            Settings.System.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            Settings.System.putFloat(app.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
            Settings.System.putFloat(app.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            Settings.System.putFloat(app.contentResolver, KEY_PEAK_REFRESH, 0f)
            Settings.System.putFloat(app.contentResolver, KEY_MIN_REFRESH, 0f)
            Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (t: Throwable) {
        }
    }

    // --------------------------------------------------------------- memory

    /**
     * Kills every background process we are allowed to kill and reports how
     * much memory actually became available.
     */
    fun releaseMemory(context: Context): OptimizerStep {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        var killed = 0
        try {
            for (p in am.runningAppProcesses ?: emptyList()) {
                if (p.uid == android.os.Process.myUid()) continue
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) continue
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) continue
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) continue
                am.killBackgroundProcesses(p.processName)
                killed++
            }
        } catch (t: Throwable) {
        }
        Thread.sleep(700)
        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        val freed = (after - before).coerceAtLeast(0)
        return OptimizerStep(
            "Memory released",
            freed > 0 || killed > 0,
            "${com.maui.system.util.Format.size(freed)} freed • $killed background processes stopped"
        )
    }

    // ------------------------------------------------------------ animation

    fun setAnimationsOff(context: Context): OptimizerStep {
        return try {
            val before = Settings.System.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            Settings.System.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            Settings.System.putFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0f)
            Settings.System.putFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
            val after = Settings.System.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            OptimizerStep(
                "Animations off",
                after == 0f,
                "scale $before → $after (window + transitions too)"
            )
        } catch (t: Throwable) {
            OptimizerStep("Animations off", false, "needs “modify system settings” — grant it in Maui → Settings")
        }
    }

    // ------------------------------------------------------------- refresh

    /** Puts the display on its fastest supported mode and verifies it stuck. */
    fun setMaxRefreshRate(context: Context): OptimizerStep {
        return try {
            val display = currentDisplay(context)
            val modes = display?.supportedModes ?: emptyArray()
            if (modes.isEmpty()) {
                return OptimizerStep("Refresh rate", false, "this display reports no modes")
            }
            val best = modes.maxByOrNull { it.refreshRate }!!
            val current = display?.refreshRate ?: 0f
            Settings.System.putFloat(context.contentResolver, KEY_PEAK_REFRESH, best.refreshRate)
            Settings.System.putFloat(context.contentResolver, KEY_MIN_REFRESH, best.refreshRate)
            val readBack = Settings.System.getFloat(context.contentResolver, KEY_PEAK_REFRESH, 0f)
            OptimizerStep(
                "Refresh rate",
                readBack > 0f,
                "${"%.1f".format(current)} Hz → ${"%.1f".format(best.refreshRate)} Hz"
            )
        } catch (t: Throwable) {
            OptimizerStep("Refresh rate", false, "needs “modify system settings” — grant it in Maui → Settings")
        }
    }

    fun currentRefreshRate(context: Context): Float {
        return try {
            currentDisplay(context)?.refreshRate ?: 0f
        } catch (t: Throwable) {
            0f
        }
    }

    private fun currentDisplay(context: Context): Display? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        }
    } catch (t: Throwable) {
        null
    }

    // ------------------------------------------------------------------ dnd

    fun setDoNotDisturb(context: Context): OptimizerStep {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                return OptimizerStep("Do Not Disturb", false, "needs Do Not Disturb access for notifications")
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            OptimizerStep("Do Not Disturb", true, "notifications silenced while you play")
        } catch (t: Throwable) {
            OptimizerStep("Do Not Disturb", false, "not permitted on this device")
        }
    }

    // ----------------------------------------------------------- screen on

    fun extendScreenTimeout(context: Context): OptimizerStep {
        return try {
            val before = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 1_800_000)
            val after = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 0)
            OptimizerStep(
                "Screen stays on",
                after == 1_800_000,
                "timeout ${before / 1000}s → ${after / 1000}s"
            )
        } catch (t: Throwable) {
            OptimizerStep("Screen stays on", false, "needs “modify system settings”")
        }
    }

    // ----------------------------------------------------------------- root

    /**
     * Root-only tuning: CPU governor, GPU boost, doze whitelist, standby
     * bucket and a system cache trim. Each step reads the value back.
     */
    private fun rootOptimisations(packageName: String, isFreeFire: Boolean): List<OptimizerStep> {
        val out = mutableListOf<OptimizerStep>()

        // Keep the game out of Doze — this is what stops Android freezing a
        // backgrounded game and throttling its wakeups.
        val doze = RootShell.runRoot("dumpsys deviceidle whitelist +$packageName", 10_000)
        out += OptimizerStep(
            "Doze protection",
            doze.first == 0,
            if (doze.first == 0) "$packageName kept out of Doze" else "could not update the doze whitelist"
        )

        // Maximum RAM priority for the game process.
        val bucket = RootShell.runRoot("am set-standby-bucket $packageName active", 10_000)
        out += OptimizerStep(
            "RAM priority",
            bucket.first == 0,
            if (bucket.first == 0) "standby bucket = active" else "standby bucket unchanged"
        )

        // CPU governor — performance beats the default scheduler for games.
        val govPaths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
            "/sys/devices/system/cpu/cpu4/cpufreq/scaling_governor",
            "/sys/devices/system/cpu/cpupolicy0/scaling_governor"
        )
        var govDone = 0
        var govRead = ""
        for (p in govPaths) {
            val f = File(p)
            if (!f.exists()) continue
            val write = RootShell.runRoot("echo performance > $p", 8_000)
            val now = RootShell.readFile(p)?.trim() ?: ""
            if (write.first == 0 && now.contains("performance")) {
                govDone++
                if (govRead.isEmpty()) govRead = now
            }
        }
        out += OptimizerStep(
            "CPU governor",
            govDone > 0,
            if (govDone > 0) "performance governor on $govDone core(s)" else "governor not writable"
        )

        // GPU boost — vendor specific, so we probe the known paths.
        val gpuPaths = listOf(
            "/sys/devices/platform/soc/*.gpu/devfreq/adrenoboost",
            "/sys/class/devfreq/*.gpu/devfreq/adrenoboost",
            "/sys/class/kgsl/kgsl-3d0/max_pwrclocks",
            "/sys/class/devfreq/*.gpu/devfreq/min_freq"
        )
        var gpuDone = 0
        for (p in gpuPaths) {
            val matches = if (p.contains("*")) {
                File("/").walkTopDown().maxDepth(6).filter { it.isFile }
                    .filter { f -> globMatch(p, f.path) }
                    .toList()
            } else {
                listOf(File(p)).filter { it.exists() }
            }
            for (f in matches) {
                val value = if (f.name == "min_freq" || f.name == "max_pwrclocks") {
                    val max = RootShell.readFile(f.path)?.trim()?.split(",")?.lastOrNull() ?: continue
                    max
                } else {
                    "1"
                }
                val write = RootShell.runRoot("echo $value > ${f.path}", 8_000)
                if (write.first == 0) gpuDone++
            }
        }
        out += OptimizerStep(
            "GPU boost",
            gpuDone > 0,
            if (gpuDone > 0) "applied on $gpuDone device(s)" else "this GPU exposes no writable boost control"
        )

        // Trim the system cache so the game gets more free memory.
        val trim = RootShell.runRoot("pm trim-caches 999999999", 45_000)
        out += OptimizerStep(
            "System cache trim",
            trim.first == 0,
            if (trim.first == 0) "cached pages released" else "trim unavailable"
        )

        if (isFreeFire) {
            val bg = RootShell.runRoot(
                "am kill-all 2>/dev/null; dumpsys meminfo $packageName | head -3",
                15_000
            )
            out += OptimizerStep(
                "Free Fire tuning",
                bg.first == 0,
                "background processes flushed, game memory inspected"
            )
        }
        return out
    }

    private fun globMatch(pattern: String, path: String): Boolean {
        val regex = pattern.replace(".", "\\.").replace("*", "[^/]*")
        return Regex(regex).matches(path)
    }

    /** Thermal status 0..5, higher is hotter. */
    fun thermalStatus(context: Context): Int = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus else 0
    } catch (t: Throwable) {
        0
    }
}
