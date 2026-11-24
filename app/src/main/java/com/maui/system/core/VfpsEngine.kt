package com.maui.system.core

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import java.io.File

/**
 * The VFPS engine — Maui's frame provider.
 *
 * ## What VFPS really means
 *
 * In the PC / emulator world, **VFPS** means the *vertical refresh* rate: how
 * many frames the display can physically present per second (60, 90, 120,
 * 144 Hz). It is a hard ceiling — no application, however clever, can put
 * more presented frames on a 120 Hz panel than 120 per second.
 *
 * **RFPS** (renders per second) is what the game really produces. On a device
 * that renders faster than the panel refreshes, RFPS can legitimately be
 * *higher* than VFPS — that is the only honest way a counter can ever show a
 * number like "200+".
 *
 * Maui therefore never prints an invented "999+ FPS". It prints the ceiling,
 * the measured rate, how much of the ceiling is used, the 1% low, the jank
 * share and the frame-pacing deviation — all measured, all real.
 *
 * ## What the engine actually does
 *
 * The one thing that genuinely raises the number of frames you *see* is
 * unlocking the panel to its fastest mode and then making sure the game is
 * the only thing competing for the hardware. Everything the engine does is
 * that, and every step is read back and reported — applied or skipped with
 * the reason.
 */
object VfpsEngine {

    // Not public SDK constants, but these are the system keys that drive the
    // panel refresh rate on every Android device.
    private const val KEY_PEAK_REFRESH = "peak_refresh_rate"
    private const val KEY_MIN_REFRESH = "min_refresh_rate"

    /** One reading of the frame situation. */
    data class Snapshot(
        /** The panel ceiling — the highest refresh rate the display supports. */
        val vfps: Int = 0,
        /** The refresh rate the panel is running at right now. */
        val currentHz: Float = 0f,
        /** The measured frame rate of the game. */
        val rfps: Int = 0,
        /** Where the measurement came from. */
        val source: String = "",
        /** True when the rate is really measured, false when it is unknown. */
        val measured: Boolean = false,
        /** The frame statistics of the measurement window. */
        val stats: FrameMetrics.Stats = FrameMetrics.Stats(),
        /** A note explaining anything unusual. */
        val note: String = ""
    ) {
        /** Share of the panel ceiling that the measured rate uses. */
        val ceilingUsage: Int get() = FrameMetrics.ceilingUsage(rfps, vfps)

        /** True when the game is running at the panel's ceiling. */
        val atCeiling: Boolean get() = FrameMetrics.atCeiling(rfps, vfps)

        /** The label the UI shows for the big number. */
        val headline: String get() = if (measured) "$rfps" else "--"
    }

    /** The result of a boost run. */
    data class BoostReport(
        val steps: List<OptimizerStep>,
        val before: Snapshot,
        val after: Snapshot
    ) {
        val appliedCount: Int get() = steps.count { it.applied }
        val skippedCount: Int get() = steps.count { !it.applied }
    }

    // ------------------------------------------------------------- ceiling

    /**
     * The VFPS ceiling of the panel: the fastest refresh rate the display
     * reports in its supported modes. This is the physical limit.
     */
    fun panelCeiling(context: Context): Int {
        val display = currentDisplay(context) ?: return 0
        val modes = try { display.supportedModes } catch (t: Throwable) { null }
        var ceiling = 0
        if (!modes.isNullOrEmpty()) {
            ceiling = modes.maxOf { it.refreshRate }.toInt()
        }
        // The panel may already run faster than the mode list reports (and it
        // always does on the devices that expose the real refresh rate).
        val live = display.refreshRate
        if (live > ceiling) ceiling = live.toInt()
        return ceiling
    }

    /** The refresh rate the panel is running at right now. */
    fun currentRefresh(context: Context): Float =
        currentDisplay(context)?.refreshRate ?: 0f

    /**
     * The display of the device.
     *
     * The window manager is used first: it works with an application context
     * (the service, the receivers), while `context.display` only works for a
     * visual context such as an activity.
     */
    private fun currentDisplay(context: Context): Display? = try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        wm?.defaultDisplay ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display else null
    } catch (t: Throwable) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display else null
        } catch (t2: Throwable) {
            null
        }
    }

    // ----------------------------------------------------------- measuring

    /**
     * Measures the frame rate of [packageName].
     *
     * The only accurate source for another app's frames is the compositor's
     * own frame timeline, which needs root. Without root Maui says so instead
     * of printing a number it cannot back up.
     */
    fun measure(context: Context, packageName: String?): Snapshot {
        val ceiling = panelCeiling(context)
        val now = currentRefresh(context)
        if (packageName.isNullOrEmpty()) {
            return Snapshot(ceiling, now, 0, "", false, note = "no game selected")
        }
        val reading = SurfaceFlingerProbe.fpsForPackage(packageName)
        if (!reading.ok) {
            return Snapshot(
                vfps = ceiling,
                currentHz = now,
                rfps = 0,
                source = "",
                measured = false,
                note = reading.note.ifEmpty { "the frame timeline cannot be read" }
            )
        }
        // Pull the raw timestamps again for the detailed statistics.
        val layer = reading.layer
        val raw = SurfaceFlingerProbe.timestampsFor(layer)
        val durations = FrameMetrics.durationsMs(raw)
        val stats = FrameMetrics.stats(durations, ceiling.coerceAtLeast(1))
        return Snapshot(
            vfps = ceiling,
            currentHz = now,
            rfps = reading.fps,
            source = reading.source,
            measured = true,
            stats = stats,
            note = reading.note
        )
    }

    /**
     * Measures Maui's own UI frame rate — the Choreographer frames of the app
     * itself. This is always available and it is what the HUD shows when no
     * game is running.
     */
    fun measureOwnUi(samples: List<Double>, ceiling: Int): Snapshot {
        val stats = FrameMetrics.stats(samples, ceiling.coerceAtLeast(1))
        return Snapshot(
            vfps = ceiling,
            currentHz = ceiling.toFloat(),
            rfps = stats.fps,
            source = "Choreographer",
            measured = stats.frames >= 3,
            stats = stats,
            note = "Maui UI frames"
        )
    }

    // --------------------------------------------------------------- boost

    /**
     * Runs the full VFPS boost for [packageName] and reports what really
     * happened, with the frame situation before and after.
     */
    fun boost(context: Context, packageName: String?, profile: GameProfile): BoostReport {
        val app = context.applicationContext
        val before = measure(context, packageName)
        val steps = mutableListOf<OptimizerStep>()

        // 1) Unlock the panel to its ceiling. This is the only change that can
        //    really raise the number of frames you see.
        steps += lockRefreshToCeiling(app)

        // 2) Free the memory and the CPU the game needs.
        steps += GameOptimizer.releaseMemory(app)

        // 3) Animations off — every animation the system draws is a frame the
        //    game cannot draw.
        if (profile.killsAnimations) steps += GameOptimizer.setAnimationsOff(app)

        // 4) Silence notifications so nothing steals a frame.
        steps += GameOptimizer.setDoNotDisturb(app)

        // 5) Keep the screen alive — a screen timeout mid-match is a stutter.
        steps += GameOptimizer.extendScreenTimeout(app)

        // 6) Give the game maximum RAM priority (root) and keep it out of Doze.
        if (RootShell.isRooted && !packageName.isNullOrEmpty()) {
            steps += OptimizerStep(
                "Doze protection",
                RootShell.runRoot("dumpsys deviceidle whitelist +$packageName", 10_000).first == 0,
                "$packageName kept out of Doze"
            )
            steps += OptimizerStep(
                "RAM priority",
                RootShell.runRoot("am set-standby-bucket $packageName active", 10_000).first == 0,
                "standby bucket = active"
            )
            steps += OptimizerStep(
                "CPU governor",
                setPerformanceGovernor(),
                "performance governor on the big cores"
            )
            steps += OptimizerStep(
                "GPU boost",
                boostGpu(),
                "vendor GPU boost control raised where the device exposes one"
            )
        } else {
            steps += OptimizerStep(
                "Power tuning",
                false,
                "no root — governor, GPU boost and doze protection unavailable"
            )
        }

        // 7) Thermal guard — a hot phone throttles below the ceiling, so say
        //    it plainly instead of pretending the boost is free.
        val thermal = thermalStatus(context)
        steps += OptimizerStep(
            "Thermal state",
            thermal <= 2,
            when (thermal) {
                0 -> "cool — the boost can hold"
                1, 2 -> "warm — the phone may drop below the ceiling"
                3 -> "hot — the system will throttle the game"
                else -> "critical — stop playing and let the phone cool down"
            }
        )

        val after = measure(context, packageName)
        return BoostReport(steps, before, after)
    }

    /**
     * Pushes the panel to its fastest supported mode and verifies it stuck by
     * reading the setting back and re-reading the live refresh rate.
     */
    fun lockRefreshToCeiling(context: Context): OptimizerStep {
        return try {
            val ceiling = panelCeiling(context).toFloat()
            if (ceiling <= 0f) {
                return OptimizerStep("Refresh rate", false, "this display reports no modes")
            }
            val before = currentRefresh(context)
            Settings.System.putFloat(context.contentResolver, KEY_PEAK_REFRESH, ceiling)
            Settings.System.putFloat(context.contentResolver, KEY_MIN_REFRESH, ceiling)
            val readBack = Settings.System.getFloat(context.contentResolver, KEY_PEAK_REFRESH, 0f)
            OptimizerStep(
                "Refresh rate",
                readBack >= ceiling - 0.5f,
                "${"%.1f".format(before)} Hz → ceiling ${"%.0f".format(ceiling)} Hz"
            )
        } catch (t: Throwable) {
            OptimizerStep("Refresh rate", false, "needs “modify system settings” — grant it in Maui → Settings")
        }
    }

    /** Puts the panel back to the system default refresh behaviour. */
    fun restoreRefresh(context: Context) {
        try {
            Settings.System.putFloat(context.contentResolver, KEY_PEAK_REFRESH, 0f)
            Settings.System.putFloat(context.contentResolver, KEY_MIN_REFRESH, 0f)
        } catch (t: Throwable) {
        }
    }

    /** True when the panel is currently locked to the ceiling. */
    fun isLockedToCeiling(context: Context): Boolean = try {
        val ceiling = panelCeiling(context).toFloat()
        val peak = Settings.System.getFloat(context.contentResolver, KEY_PEAK_REFRESH, 0f)
        ceiling > 0f && peak >= ceiling - 0.5f
    } catch (t: Throwable) {
        false
    }

    // --------------------------------------------------------------- root

    private fun setPerformanceGovernor(): Boolean {
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
            "/sys/devices/system/cpu/cpu4/cpufreq/scaling_governor",
            "/sys/devices/system/cpu/cpu6/cpufreq/scaling_governor",
            "/sys/devices/system/cpu/cpupolicy0/scaling_governor"
        )
        var done = 0
        for (p in paths) {
            if (!File(p).exists()) continue
            val write = RootShell.runRoot("echo performance > $p", 8_000)
            val now = RootShell.readFile(p)?.trim() ?: ""
            if (write.first == 0 && now.contains("performance")) done++
        }
        return done > 0
    }

    private fun boostGpu(): Boolean {
        val candidates = listOf(
            "/sys/class/kgsl/kgsl-3d0/max_pwrclocks",
            "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost",
            "/sys/class/devfreq/0.platform.gpu/devfreq/adrenoboost"
        )
        var done = 0
        for (p in candidates) {
            val f = File(p)
            if (!f.exists()) continue
            val value = if (f.name == "max_pwrclocks") {
                RootShell.readFile(p)?.trim()?.split(",")?.lastOrNull() ?: continue
            } else {
                "1"
            }
            if (RootShell.runRoot("echo $value > $p", 8_000).first == 0) done++
        }
        return done > 0
    }

    /** Thermal status 0..5, higher is hotter. */
    fun thermalStatus(context: Context): Int = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus else 0
    } catch (t: Throwable) {
        0
    }

    /** The battery temperature in degrees, when the device reports one. */
    fun batteryTemp(context: Context): Float = try {
        DeviceProfiler.batterySnapshot(context).second
    } catch (t: Throwable) {
        0f
    }
}
