package com.maui.system.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.maui.system.core.PerfSnapshot
import com.maui.system.util.Format

/**
 * The in-game toolbar. It floats above the game you are playing and gives
 * you the same Maui controls without leaving the match.
 */
class GameToolbarView(
    context: Context,
    private val actions: Actions
) : LinearLayout(context) {

    interface Actions {
        fun onBoost()
        fun onClean()
        fun onToggleHud()
        fun onExitGame()
    }

    private val titleLabel: TextView
    private val fpsLabel: TextView
    private val ramLabel: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            OverlayTheme.dp(context, 10f),
            OverlayTheme.dp(context, 5f),
            OverlayTheme.dp(context, 10f),
            OverlayTheme.dp(context, 5f)
        )
        background = OverlayTheme.card(context, 100f, OverlayTheme.BG_DEEP, OverlayTheme.STROKE)
        elevation = OverlayTheme.dp(context, 10).toFloat()

        val logo = OverlayTheme.label(context, "M", 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = OverlayTheme.gradient(context)
            setPadding(OverlayTheme.dp(context, 8), OverlayTheme.dp(context, 3), OverlayTheme.dp(context, 8), OverlayTheme.dp(context, 3))
        }
        addView(logo)

        titleLabel = OverlayTheme.label(context, "Game", 11f, OverlayTheme.TEXT_DIM, false).apply {
            maxWidth = OverlayTheme.dp(context, 110)
        }
        addView(titleLabel, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = OverlayTheme.dp(context, 8)
        })

        fpsLabel = OverlayTheme.label(context, "-- FPS", 11f, OverlayTheme.MAUI_GREEN, true)
        ramLabel = OverlayTheme.label(context, "", 11f, OverlayTheme.TEXT_DIM, false)
        addView(fpsLabel)
        addView(ramLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = OverlayTheme.dp(context, 8)
        })

        addView(button(context, "⚡", OverlayTheme.MAUI_AMBER) { actions.onBoost() })
        addView(button(context, "🧹", OverlayTheme.MAUI_CYAN) { actions.onClean() })
        addView(button(context, "📊", OverlayTheme.MAUI_BLUE) { actions.onToggleHud() })
        addView(button(context, "✕", OverlayTheme.MAUI_RED) { actions.onExitGame() })
    }

    private fun button(ctx: Context, glyph: String, tint: Int, onClick: () -> Unit): TextView =
        OverlayTheme.label(ctx, glyph, 14f, tint, false).apply {
            gravity = Gravity.CENTER
            setPadding(OverlayTheme.dp(ctx, 10), OverlayTheme.dp(ctx, 4), OverlayTheme.dp(ctx, 10), OverlayTheme.dp(ctx, 4))
            background = OverlayTheme.card(ctx, 100f, Color.argb(40, 255, 255, 255), Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun setGameName(name: String) {
        titleLabel.text = name
    }

    fun update(s: PerfSnapshot) {
        fpsLabel.text = (if (s.fps > 0) s.fps.toString() else "--") + " FPS"
        fpsLabel.setTextColor(
            when {
                s.fps >= 50 -> OverlayTheme.MAUI_GREEN
                s.fps in 25..49 -> OverlayTheme.MAUI_AMBER
                else -> OverlayTheme.MAUI_RED
            }
        )
        ramLabel.text = Format.size(s.ramUsed)
    }
}
