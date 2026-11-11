package com.maui.system.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * A generated launcher icon for apps that do not ship one.
 *
 * The tile colour is derived from the app name, so the same app always gets
 * the same icon, and the first letter is drawn in the middle.
 */
class PlaceholderIconDrawable(
    context: Context,
    private val label: String
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val density = context.resources.displayMetrics.density

    private val start: Int
    private val end: Int

    init {
        val hash = label.hashCode()
        val palettes = arrayOf(
            intArrayOf(0xFF2E7BFF.toInt(), 0xFF8A5CFF.toInt()),
            intArrayOf(0xFF22C55E.toInt(), 0xFF22D3EE.toInt()),
            intArrayOf(0xFFFF6B35.toInt(), 0xFFF59E0B.toInt()),
            intArrayOf(0xFF8A5CFF.toInt(), 0xFFEC4899.toInt()),
            intArrayOf(0xFF0EA5E9.toInt(), 0xFF22D3EE.toInt()),
            intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt())
        )
        val palette = palettes[Math.abs(hash) % palettes.size]
        start = palette[0]
        end = palette[1]
        textPaint.textSize = 24f * density
    }

    override fun draw(canvas: Canvas) {
        val r = RectF(bounds)
        val radius = r.width() * 0.22f
        paint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            start, end, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(r, radius, radius, paint)
        paint.shader = null

        val letter = label.trim().firstOrNull()?.uppercase()?.toString() ?: "?"
        val fm = textPaint.fontMetrics
        canvas.drawText(
            letter,
            r.centerX(),
            r.centerY() - (fm.ascent + fm.descent) / 2f,
            textPaint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
