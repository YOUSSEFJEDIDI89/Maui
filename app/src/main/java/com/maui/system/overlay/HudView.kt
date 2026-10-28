package com.maui.system.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.maui.system.core.PerfSnapshot
import com.maui.system.util.Format

/**
 * The live HUD — FPS, RAM, CPU, temperature, battery and network speed,
 * drawn on top of whatever app is in the foreground.
 */
class HudView(context: Context) : LinearLayout(context) {

    private val fpsText: TextView
    private val ramText: TextView
    private val cpuText: TextView
    private val tempText: TextView
    private val batteryText: TextView
    private val netText: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            OverlayTheme.dp(context, 12f),
            OverlayTheme.dp(context, 6f),
            OverlayTheme.dp(context, 12f),
            OverlayTheme.dp(context, 6f)
        )
        background = OverlayTheme.card(context, 100f, OverlayTheme.BG_DEEP, OverlayTheme.STROKE)
        elevation = OverlayTheme.dp(context, 8).toFloat()

        fpsText = chip(context, "FPS")
        ramText = chip(context, "RAM")
        cpuText = chip(context, "CPU")
        tempText = chip(context, "TEMP")
        batteryText = chip(context, "BAT")
        netText = chip(context, "NET")

        addView(fpsText)
        addView(divider())
        addView(ramText)
        addView(divider())
        addView(cpuText)
        addView(divider())
        addView(tempText)
        addView(divider())
        addView(batteryText)
        addView(divider())
        addView(netText)
    }

    private fun chip(ctx: Context, label: String): TextView {
        val tv = OverlayTheme.label(ctx, "$label —", 10.5f, OverlayTheme.TEXT_DIM, false)
        tv.setPadding(0, 0, OverlayTheme.dp(ctx, 3), 0)
        return tv
    }

    private fun divider(): View = View(context).apply {
        layoutParams = LayoutParams(OverlayTheme.dp(context, 1), OverlayTheme.dp(context, 12)).apply {
            marginStart = OverlayTheme.dp(context, 6)
            marginEnd = OverlayTheme.dp(context, 6)
        }
        setBackgroundColor(OverlayTheme.STROKE)
    }

    fun update(s: PerfSnapshot) {
        fpsText.text = "FPS — " + if (s.fps > 0) s.fps.toString() else "--"
        fpsText.setTextColor(if (s.fps >= 50) OverlayTheme.MAUI_GREEN else if (s.fps in 25..49) OverlayTheme.MAUI_AMBER else OverlayTheme.MAUI_RED)
        ramText.text = "RAM — " + Format.size(s.ramUsed) + " (" + s.ramPercent + "%)"
        ramText.setTextColor(if (s.ramPercent > 85) OverlayTheme.MAUI_RED else OverlayTheme.TEXT)
        cpuText.text = "CPU — " + s.cpuPercent + "%"
        cpuText.setTextColor(if (s.cpuPercent > 85) OverlayTheme.MAUI_AMBER else OverlayTheme.TEXT)
        tempText.text = "TEMP — " + (if (s.cpuTempC > 0) String.format("%.0f°C", s.cpuTempC) else "--")
        tempText.setTextColor(if (s.cpuTempC > 45) OverlayTheme.MAUI_AMBER else OverlayTheme.TEXT)
        batteryText.text = "BAT — " + s.batteryPercent + "%" + if (s.charging) " ⚡" else ""
        netText.text = "NET — " + Format.speed(s.netDownBps)
    }
}
