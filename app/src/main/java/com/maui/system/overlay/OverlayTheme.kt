package com.maui.system.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Shared look & feel for every Maui floating surface. */
object OverlayTheme {

    // Brand palette
    val MAUI_BLUE = Color.parseColor("#2E7BFF")
    val MAUI_PURPLE = Color.parseColor("#8A5CFF")
    val MAUI_CYAN = Color.parseColor("#22D3EE")
    val MAUI_GREEN = Color.parseColor("#22C55E")
    val MAUI_AMBER = Color.parseColor("#F59E0B")
    val MAUI_RED = Color.parseColor("#EF4444")

    val BG_DEEP = Color.parseColor("#E6101420")
    val BG_CARD = Color.parseColor("#F21B2233")
    val STROKE = Color.parseColor("#33FFFFFF")
    val TEXT = Color.parseColor("#F2FFFFFF")
    val TEXT_DIM = Color.parseColor("#99FFFFFF")

    fun dp(ctx: Context, v: Number): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
    ).toInt()

    fun card(ctx: Context, radiusDp: Float = 22f, color: Int = BG_CARD, stroke: Int = STROKE): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setColor(color)
            setStroke(dp(ctx, 1f), stroke)
        }

    fun pill(ctx: Context, color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(ctx, 100).toFloat()
        setColor(color)
    }

    fun gradient(ctx: Context, start: Int = MAUI_BLUE, end: Int = MAUI_PURPLE): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(start, end)
        ).apply { cornerRadius = dp(ctx, 100).toFloat() }

    fun label(
        ctx: Context,
        text: String,
        sizeSp: Number = 12f,
        color: Int = TEXT,
        bold: Boolean = true
    ): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    fun row(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun column(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun spacer(ctx: Context, h: Float): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, h))
    }

    fun dpInt(ctx: Context, v: Number): Int = dp(ctx, v)
}
