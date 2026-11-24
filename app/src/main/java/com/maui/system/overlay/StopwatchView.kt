package com.maui.system.overlay

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.widget.TextView
import java.util.Locale

/**
 * A stopwatch that floats over the game, the way a game-space app times a
 * match. It counts real elapsed time and can be started, stopped and reset.
 */
class StopwatchView(context: Context) : TextView(context) {

    private var startedAt = 0L
    private var accumulated = 0L
    private var running = false

    init {
        text = format(0L)
        setTextColor(OverlayTheme.MAUI_CYAN)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(
            OverlayTheme.dp(context, 14),
            OverlayTheme.dp(context, 8),
            OverlayTheme.dp(context, 14),
            OverlayTheme.dp(context, 8)
        )
        background = OverlayTheme.card(context, 100f, Color.argb(190, 10, 14, 26), OverlayTheme.STROKE)
        elevation = OverlayTheme.dp(context, 10).toFloat()
    }

    /** Starts the timer, or continues a stopped one. */
    fun start() {
        if (running) return
        startedAt = SystemClock.elapsedRealtime()
        running = true
        refresh()
    }

    /** Stops the timer, keeping the elapsed time. */
    fun stop() {
        if (!running) return
        accumulated += SystemClock.elapsedRealtime() - startedAt
        running = false
        refresh()
    }

    /** Stops the timer and clears the time. */
    fun reset() {
        running = false
        accumulated = 0L
        startedAt = 0L
        refresh()
    }

    /** The elapsed time in milliseconds. */
    fun elapsed(): Long =
        if (running) accumulated + (SystemClock.elapsedRealtime() - startedAt) else accumulated

    /** Updates the label — call this once a second. */
    fun refresh() {
        text = format(elapsed())
    }

    private fun format(ms: Long): String {
        val total = ms / 1000
        val minutes = total / 60
        val seconds = total % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
