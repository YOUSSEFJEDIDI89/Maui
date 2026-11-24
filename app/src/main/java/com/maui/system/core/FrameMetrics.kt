package com.maui.system.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Frame-time analytics for the VFPS engine.
 *
 * Everything here is pure maths over measured frame times — no estimates and
 * no invented numbers. The three ideas that matter:
 *
 *  - **VFPS** (vertical refresh) is the ceiling: how many frames the panel can
 *    physically present per second (60 / 90 / 120 / 144 Hz). No app can show
 *    more presented frames than this.
 *  - **RFPS** (renders per second) is what the game actually produced. It can
 *    legitimately be *higher* than VFPS on a device that renders faster than
 *    the panel refreshes, and that is the only honest way to ever show a
 *    number like "200+".
 *  - **Jank** is the share of frames that missed their deadline, and the
 *    **frame pacing** deviation is what makes a game feel uneven even when the
 *    average FPS looks good.
 */
object FrameMetrics {

    /** A frame is janky when it took longer than this multiple of the budget. */
    private const val JANK_FACTOR = 1.75

    data class Stats(
        /** Frames per second measured over the window. */
        val fps: Int = 0,
        /** The 1% lowest frame rate — the number that exposes stutter. */
        val low1Percent: Int = 0,
        /** Share of frames that missed their deadline, in percent. */
        val jankPercent: Float = 0f,
        /** Average frame time in milliseconds. */
        val avgFrameMs: Float = 0f,
        /** Worst frame time in the window, in milliseconds. */
        val worstFrameMs: Float = 0f,
        /** Standard deviation of the frame time — the pacing deviation. */
        val pacingDeviationMs: Float = 0f,
        /** How many frames were measured. */
        val frames: Int = 0,
        /** The frame budget of the target rate, in milliseconds. */
        val budgetMs: Float = 0f,
        /** True when the window is too small to say anything. */
        val empty: Boolean = true
    ) {
        /** True when the average is close to the budget, i.e. the game keeps up. */
        val smooth: Boolean get() = !empty && fps > 0 && avgFrameMs <= budgetMs * 1.05f

        /** A one-line, honest description for the UI. */
        fun describe(): String = when {
            empty -> "no frames measured yet"
            frames < 10 -> "sampling… (${frames} frames)"
            else -> "$fps fps • ${"%.1f".format(avgFrameMs)} ms • ${"%.1f".format(jankPercent)}% jank"
        }
    }

    /**
     * Computes the statistics of a list of frame durations in milliseconds.
     *
     * @param frameTimesMs one entry per frame, in milliseconds.
     * @param targetFps the rate the game is aiming for — the frame budget is
     *        derived from it (60 fps → 16.67 ms).
     */
    fun stats(frameTimesMs: List<Double>, targetFps: Int): Stats {
        if (frameTimesMs.isEmpty()) return Stats(budgetMs = budgetOf(targetFps))
        val budget = budgetOf(targetFps)
        val sorted = frameTimesMs.sorted()
        val avg = frameTimesMs.average()
        val worst = sorted.last()
        val deviation = sqrt(frameTimesMs.map { (it - avg) * (it - avg) }.average())
        val janky = frameTimesMs.count { it > budget * JANK_FACTOR }
        // The 1% low: the average rate the slowest 1% of the frames would give.
        // The slowest frames are at the END of the sorted list.
        val slowCount = (sorted.size * 0.01).toInt().coerceAtLeast(1)
        val slowSlice = sorted.takeLast(slowCount)
        val slowAvg = slowSlice.average()
        val low = if (slowAvg > 0) Math.round(1000.0 / slowAvg).toInt() else 0

        return Stats(
            fps = if (avg > 0) Math.round(1000.0 / avg).toInt() else 0,
            low1Percent = low.coerceIn(0, 999),
            jankPercent = (janky * 100.0 / frameTimesMs.size).toFloat(),
            avgFrameMs = avg.toFloat(),
            worstFrameMs = worst.toFloat(),
            pacingDeviationMs = deviation.toFloat(),
            frames = frameTimesMs.size,
            budgetMs = budget,
            empty = false
        )
    }

    /** The frame budget of a target rate, in milliseconds. */
    fun budgetOf(targetFps: Int): Float =
        if (targetFps <= 0) 0f else (1000f / targetFps)

    /**
     * Converts a list of frame *timestamps* (nanoseconds, monotonic) into
     * frame durations in milliseconds. Timestamps that are not strictly
     * increasing are dropped.
     */
    fun durationsMs(timestampsNs: List<Long>): List<Double> {
        if (timestampsNs.size < 2) return emptyList()
        val out = mutableListOf<Double>()
        for (i in 1 until timestampsNs.size) {
            val d = timestampsNs[i] - timestampsNs[i - 1]
            if (d > 0 && d < 1_000_000_000L) out += d / 1_000_000.0
        }
        return out
    }

    /**
     * Frame-pacing score from 0 to 100.
     *
     * A game that runs at 60 fps but alternates between 8 ms and 30 ms frames
     * feels far worse than a steady 60 fps, so the score rewards a small
     * deviation relative to the frame budget.
     */
    fun pacingScore(stats: Stats): Int {
        if (stats.empty || stats.budgetMs <= 0f) return 0
        val ratio = stats.pacingDeviationMs / stats.budgetMs
        return (100 - (ratio * 100)).toInt().coerceIn(0, 100)
    }

    /**
     * How close a measured rate is to the panel ceiling, in percent.
     * This is the honest version of "how much of VFPS are we using".
     */
    fun ceilingUsage(rfps: Int, vfps: Int): Int {
        if (rfps <= 0 || vfps <= 0) return 0
        return (rfps * 100 / vfps).coerceIn(0, 999)
    }

    /** True when the measured rate is at (or above) the panel ceiling. */
    fun atCeiling(rfps: Int, vfps: Int): Boolean =
        vfps > 0 && rfps >= vfps - 2

    /**
     * Classifies a frame time against a budget — used by the live overlay to
     * colour the counter.
     */
    enum class FrameState { FAST, ON_TIME, SLOW, JANK }

    fun classify(frameMs: Double, budgetMs: Float): FrameState = when {
        budgetMs <= 0f -> FrameState.ON_TIME
        frameMs <= budgetMs * 0.85 -> FrameState.FAST
        frameMs <= budgetMs -> FrameState.ON_TIME
        frameMs <= budgetMs * JANK_FACTOR -> FrameState.SLOW
        else -> FrameState.JANK
    }

    /**
     * A rolling window of frame times.
     *
     * It keeps the newest [capacity] samples so the live counter reacts
     * quickly while the long-term statistics stay stable.
     */
    class Window(private val capacity: Int = 120) {
        private val values = ArrayDeque<Double>()

        @Synchronized
        fun add(frameMs: Double) {
            if (frameMs <= 0 || frameMs > 2000) return
            values.addLast(frameMs)
            while (values.size > capacity) values.removeFirst()
        }

        @Synchronized
        fun snapshot(): List<Double> = values.toList()

        @Synchronized
        fun clear() = values.clear()

        @Synchronized
        fun size(): Int = values.size

        /** The most recent frame time, or 0 when nothing was measured. */
        @Synchronized
        fun last(): Double = values.lastOrNull() ?: 0.0
    }

    /**
     * Estimates the panel's vertical refresh rate from frame intervals.
     *
     * On a healthy device the compositor presents one frame per refresh
     * cycle, so the median interval is the refresh period. The result is the
     * VFPS ceiling as the panel actually behaves right now — which can be
     * lower than the panel's maximum mode.
     */
    fun vfpsFromIntervals(frameTimesMs: List<Double>): Int {
        if (frameTimesMs.size < 4) return 0
        val sorted = frameTimesMs.sorted()
        val median = sorted[sorted.size / 2]
        if (median <= 0.5) return 0
        val hz = 1000.0 / median
        return hz.toInt().coerceIn(1, 480)
    }

    /**
     * Measures the deviation between the average and the median frame time.
     * A large gap is the signature of uneven pacing.
     */
    fun pacingSkew(frameTimesMs: List<Double>): Float {
        if (frameTimesMs.isEmpty()) return 0f
        val avg = frameTimesMs.average()
        val sorted = frameTimesMs.sorted()
        val median = sorted[sorted.size / 2]
        return abs(avg - median).toFloat()
    }
}
