package com.maui.system.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Animated aurora background for the dashboard header.
 *
 * A diagonal gradient that keeps drifting, plus two soft colour blobs, so the
 * header feels alive instead of being a flat rectangle.
 */
class AuroraHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null

    private val colors = intArrayOf(
        0xFF2E7BFF.toInt(),
        0xFF6A5CFF.toInt(),
        0xFF8A5CFF.toInt(),
        0xFF22D3EE.toInt(),
        0xFF2E7BFF.toInt()
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val shift = phase * w
        paint.shader = LinearGradient(
            -shift, 0f,
            w * 1.6f - shift, h,
            colors,
            floatArrayOf(0f, 0.28f, 0.55f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Soft highlight blobs.
        paint.color = 0x22FFFFFF
        val r = h * 0.85f
        val cx = w * (0.25f + 0.5f * phase)
        canvas.drawCircle(cx, h * 0.15f, r, paint)
        paint.color = 0x1A22D3EE
        canvas.drawCircle(w - cx, h * 0.85f, r * 0.8f, paint)
    }
}
