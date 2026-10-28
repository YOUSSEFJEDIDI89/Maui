package com.maui.system.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * The Maui bubble — the handle that opens the control panel.
 * Draggable with a magnetic snap to the screen edge, exactly like a
 * One UI edge panel handle.
 */
class BubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDrag: (x: Int, y: Int) -> Unit,
    private val onDragEnd: (x: Int, y: Int) -> Unit,
    private val onLongPress: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradientShader: Shader? = null
    private var pulse = 0f
    private var pulseAnimator: ValueAnimator? = null

    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var longPressed = false

    private val longPressRunnable = Runnable {
        if (!dragging && !longPressed) {
            longPressed = true
            onLongPress()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradientShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(OverlayTheme.MAUI_CYAN, OverlayTheme.MAUI_BLUE, OverlayTheme.MAUI_PURPLE),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    fun startPulse() {
        if (pulseAnimator != null) return
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f

        // Soft outer glow
        glowPaint.shader = RadialGradient(
            cx, cy, r * (1.25f + pulse * 0.25f),
            intArrayOf(
                Color.argb((70 * (1f - pulse)).toInt(), 46, 123, 255),
                Color.TRANSPARENT
            ),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * (1.25f + pulse * 0.25f), glowPaint)

        // Body
        paint.shader = gradientShader
        canvas.drawCircle(cx, cy, r * 0.86f, paint)
        paint.shader = null

        // Inner ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.06f
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawCircle(cx, cy, r * 0.72f, paint)
        paint.style = Paint.Style.FILL

        // "M" glyph
        paint.color = Color.WHITE
        paint.textSize = r * 0.85f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        val fm = paint.fontMetrics
        canvas.drawText("M", cx, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = event.rawX
                startY = event.rawY
                dragging = false
                longPressed = false
                postDelayed(longPressRunnable, 550)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && (abs(dx) > 12 || abs(dy) > 12)) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    onDrag((startX + dx).toInt(), (startY + dy).toInt())
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    onDragEnd(event.rawX.toInt(), event.rawY.toInt())
                } else if (!longPressed) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onTap()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                dragging = false
            }
        }
        return true
    }
}
