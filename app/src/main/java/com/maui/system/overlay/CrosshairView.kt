package com.maui.system.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * A crosshair drawn over the game.
 *
 * It is a display aid only — a marker on the screen. It does not read the
 * screen, does not touch the game and cannot aim for the player.
 */
class CrosshairView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = OverlayTheme.dp(context, 1.5f).toFloat()
        color = Color.parseColor("#B322D3EE")
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B322D3EE")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = OverlayTheme.dp(context, 14f).toFloat()
        val gap = OverlayTheme.dp(context, 5f).toFloat()
        // Four ticks with a gap in the middle.
        canvas.drawLine(cx - r, cy, cx - gap, cy, paint)
        canvas.drawLine(cx + gap, cy, cx + r, cy, paint)
        canvas.drawLine(cx, cy - r, cx, cy - gap, paint)
        canvas.drawLine(cx, cy + gap, cx, cy + r, paint)
        canvas.drawCircle(cx, cy, OverlayTheme.dp(context, 1.2f).toFloat(), dot)
    }
}
