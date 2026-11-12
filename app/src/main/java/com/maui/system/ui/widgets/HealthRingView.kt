package com.maui.system.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.maui.system.R
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * A ring that shows how healthy the device is right now.
 *
 * The score is computed from the live readings — RAM pressure, CPU load,
 * temperature and free storage — and the colour follows it, so the ring is a
 * summary of the real numbers rather than decoration.
 */
class HealthRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var score = 100
    private var caption = ""
    private var measured = false
    private var sweep: SweepGradient? = null

    init {
        val d = resources.displayMetrics.density
        track.strokeWidth = 7f * d
        progress.strokeWidth = 7f * d
        valueText.textSize = 26f * d
        labelText.textSize = 10f * d
    }

    /** @param score 0..100, @param caption short word under the number. */
    fun setHealth(score: Int, caption: String) {
        this.score = score.coerceIn(0, 100)
        this.caption = caption
        invalidate()
    }

    /**
     * Builds the score from the live device readings.
     *
     * Only the readings that are actually available take part in the score, and
     * their weights are re-normalised — a device that cannot report its
     * temperature is never scored as if it were perfectly cool.
     */
    fun update(
        ramPercent: Int,
        cpuPercent: Int,
        tempC: Float,
        storagePercent: Int
    ) {
        val parts = mutableListOf<Pair<Int, Double>>()
        parts += (100 - ramPercent).coerceIn(0, 100) to 0.35
        parts += (100 - storagePercent).coerceIn(0, 100) to 0.20
        if (cpuPercent > 0) parts += (100 - cpuPercent).coerceIn(0, 100) to 0.20
        if (tempC > 0f) {
            val tempPart = when {
                tempC < 40f -> 100
                tempC < 50f -> (100 - (tempC - 40f) * 6).toInt().coerceIn(30, 100)
                else -> (100 - (tempC - 50f) * 10).toInt().coerceIn(0, 100)
            }
            parts += tempPart to 0.25
        }
        val weight = parts.sumOf { it.second }
        val total = if (weight <= 0.0) 0 else (parts.sumOf { it.first * it.second } / weight).toInt()
        val caption = context.getString(
            when {
                total >= 85 -> R.string.dash_health_good
                total >= 65 -> R.string.dash_health_ok
                total >= 45 -> R.string.dash_health_fair
                else -> R.string.dash_health_bad
            }
        )
        measured = true
        setHealth(total, caption)
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val size = minOf(width, height).toFloat()
        val stroke = track.strokeWidth
        val captionSpace = d * 14f
        val rect = RectF(
            stroke / 2 + d * 4,
            stroke / 2 + d * 2,
            width - stroke / 2 - d * 4,
            height - stroke / 2 - d * 4 - captionSpace
        )
        val centerX = rect.centerX()
        val centerY = rect.centerY()

        track.color = 0x22FFFFFF.toInt()
        canvas.drawArc(rect, 0f, 360f, false, track)

        val start = -90f
        val sweepAngle = 360f * score / 100f
        sweep = SweepGradient(
            centerX, centerY,
            intArrayOf(0xFF22D3EE.toInt(), 0xFF2E7BFF.toInt().toInt(), 0xFF8A5CFF.toInt(), 0xFF22D3EE.toInt()),
            floatArrayOf(0f, 0.4f, 0.75f, 1f)
        )
        progress.shader = sweep
        canvas.drawArc(rect, start, sweepAngle, false, progress)
        progress.shader = null

        valueText.color = when {
            score >= 85 -> 0xFF22C55E.toInt().toInt()
            score >= 65 -> 0xFF2E7BFF.toInt().toInt()
            score >= 45 -> 0xFFF59E0B.toInt().toInt()
            else -> 0xFFEF4444.toInt().toInt()
        }
        val number = if (measured) "$score" else "—"
        canvas.drawText(number, centerX, centerY + valueText.textSize * 0.18f, valueText)

        // The caption sits inside the ring, under the number.
        labelText.color = 0xD9FFFFFF.toInt()
        if (measured) canvas.drawText(caption, centerX, centerY + valueText.textSize * 0.18f + d * 13f, labelText)
    }
}
