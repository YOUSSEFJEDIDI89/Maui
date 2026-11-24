package com.maui.system.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.maui.system.core.FrameMetrics
import kotlin.math.max

/**
 * A live frame-time graph.
 *
 * The line is the real frame duration of every measured frame, the dashed
 * line is the frame budget of the target rate, and the red band is the jank
 * zone. It is the graph that shows whether a game is really smooth or only
 * averages well.
 */
class FpsGraphView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#22D3EE")
    }
    private val budgetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.parseColor("#66FFFFFF")
    }
    private val jankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1AEF4444")
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#22D3EE")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        textSize = dp(10f)
    }

    private val window = FrameMetrics.Window(180)
    private var budgetMs = 16.67f
    private var targetLabel = ""

    /** Feeds a new frame time in milliseconds. */
    fun addFrame(frameMs: Double) {
        window.add(frameMs)
        invalidate()
    }

    /** Sets the frame budget the graph draws the target line at. */
    fun setBudget(budgetMs: Float, label: String = "") {
        this.budgetMs = if (budgetMs > 0f) budgetMs else 16.67f
        this.targetLabel = label
        invalidate()
    }

    /** Replaces the whole series at once — used when a measurement arrives. */
    fun setFrames(frameTimesMs: List<Double>, budgetMs: Float, label: String = "") {
        window.clear()
        frameTimesMs.forEach { window.add(it) }
        setBudget(budgetMs, label)
    }

    /** The frame times currently on screen. */
    fun samples(): List<Double> = window.snapshot()

    /** The statistics of what is currently on screen. */
    fun stats(targetFps: Int): FrameMetrics.Stats =
        FrameMetrics.stats(window.snapshot(), targetFps)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // The jank zone starts at the budget and ends at 2x the budget.
        val maxMs = max(budgetMs * 2.2f, 24f)
        val scaleY = h / maxMs

        val jankTop = h - (budgetMs * 1.75f) * scaleY
        if (jankTop > 0) {
            canvas.drawRect(0f, jankTop, w, h, jankPaint)
        }

        // The budget line.
        val budgetY = h - budgetMs * scaleY
        canvas.drawLine(0f, budgetY, w, budgetY, budgetPaint)

        val samples = window.snapshot()
        if (samples.size < 2) {
            canvas.drawText(
                if (targetLabel.isEmpty()) "waiting for frames…" else "waiting for frames ($targetLabel)",
                dp(12f),
                h / 2,
                textPaint
            )
            return
        }

        val step = w / max(1, samples.size - 1)
        val path = Path()
        var lastX = 0f
        var lastY = 0f
        for ((i, ms) in samples.withIndex()) {
            val x = i * step
            val y = (h - (ms.toFloat() * scaleY)).coerceIn(0f, h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            lastX = x
            lastY = y
        }
        canvas.drawPath(path, linePaint)
        canvas.drawCircle(lastX, lastY, dp(3f), dotPaint)

        val stats = FrameMetrics.stats(samples, (1000f / budgetMs).toInt())
        canvas.drawText(
            "${stats.fps} fps · ${"%.1f".format(stats.avgFrameMs)} ms · ${"%.1f".format(stats.jankPercent)}% jank",
            dp(10f),
            dp(14f),
            textPaint
        )
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
