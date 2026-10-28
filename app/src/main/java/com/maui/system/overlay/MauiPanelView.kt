package com.maui.system.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.maui.system.core.PerfSnapshot
import com.maui.system.util.Format

/**
 * The Maui control panel — the One UI-style sheet that slides out of the
 * bubble. Live device telemetry on top, one-tap actions underneath.
 */
class MauiPanelView(
    context: Context,
    private val actions: Actions
) : LinearLayout(context) {

    interface Actions {
        fun onClean()
        fun onBoost()
        fun onToggleHud(enabled: Boolean)
        fun onToggleGameMode(enabled: Boolean)
        fun onToggleGameToolbar(enabled: Boolean)
        fun onOpenFiles()
        fun onOpenApps()
        fun onOpenSettings()
        fun onClose()
    }

    private val ramValue: TextView
    private val cpuValue: TextView
    private val tempValue: TextView
    private val batValue: TextView
    private val netValue: TextView
    private val hudSwitch: Switch
    private val gameSwitch: Switch
    private val toolbarSwitch: Switch

    init {
        orientation = VERTICAL
        setPadding(
            OverlayTheme.dp(context, 16f),
            OverlayTheme.dp(context, 14f),
            OverlayTheme.dp(context, 16f),
            OverlayTheme.dp(context, 16f)
        )
        background = OverlayTheme.card(context, 26f, OverlayTheme.BG_CARD, OverlayTheme.STROKE)
        elevation = OverlayTheme.dp(context, 14).toFloat()

        // ---- header ------------------------------------------------------
        val header = OverlayTheme.row(context)
        val title = OverlayTheme.label(context, "MAUI LAYERS", 15f, OverlayTheme.TEXT, true)
        title.letterSpacing = 0.18f
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val close = OverlayTheme.label(context, "✕", 16f, OverlayTheme.TEXT_DIM, false)
        close.setPadding(OverlayTheme.dp(context, 12), OverlayTheme.dp(context, 4), 0, 0)
        close.setOnClickListener { actions.onClose() }
        header.addView(close)
        addView(header)
        addView(OverlayTheme.spacer(context, 10f))

        // ---- telemetry ---------------------------------------------------
        val statsRow = OverlayTheme.row(context)
        ramValue = statCell(context, "RAM", statsRow)
        cpuValue = statCell(context, "CPU", statsRow)
        tempValue = statCell(context, "TEMP", statsRow)
        batValue = statCell(context, "BAT", statsRow)
        netValue = statCell(context, "NET", statsRow)
        addView(statsRow)
        addView(OverlayTheme.spacer(context, 14f))

        // ---- quick actions ------------------------------------------------
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        grid.addView(actionRow(context, listOf(
            ActionSpec("🧹", "Clean", OverlayTheme.MAUI_CYAN) { actions.onClean() },
            ActionSpec("⚡", "Boost", OverlayTheme.MAUI_AMBER) { actions.onBoost() }
        )))
        grid.addView(OverlayTheme.spacer(context, 8f))
        grid.addView(actionRow(context, listOf(
            ActionSpec("📂", "Files", OverlayTheme.MAUI_BLUE) { actions.onOpenFiles() },
            ActionSpec("📦", "Apps", OverlayTheme.MAUI_PURPLE) { actions.onOpenApps() }
        )))
        grid.addView(OverlayTheme.spacer(context, 8f))
        grid.addView(actionRow(context, listOf(
            ActionSpec("⚙️", "Setup", OverlayTheme.TEXT_DIM) { actions.onOpenSettings() },
            ActionSpec("🏠", "Maui", OverlayTheme.MAUI_GREEN) { actions.onOpenSettings() }
        )))
        addView(grid)
        addView(OverlayTheme.spacer(context, 12f))

        // ---- toggles ------------------------------------------------------
        hudSwitch = toggle(context, "Live HUD (FPS / RAM / CPU)") { actions.onToggleHud(it) }
        gameSwitch = toggle(context, "Game Mode") { actions.onToggleGameMode(it) }
        toolbarSwitch = toggle(context, "Game toolbar overlay") { actions.onToggleGameToolbar(it) }
        addView(hudSwitch)
        addView(gameSwitch)
        addView(toolbarSwitch)
    }

    private data class ActionSpec(
        val icon: String,
        val label: String,
        val tint: Int,
        val click: () -> Unit
    )

    private fun actionRow(ctx: Context, specs: List<ActionSpec>): LinearLayout {
        val row = OverlayTheme.row(ctx)
        for (s in specs) {
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = OverlayTheme.card(ctx, 18f, Color.argb(46, 255, 255, 255), OverlayTheme.STROKE)
                setPadding(0, OverlayTheme.dp(ctx, 10f), 0, OverlayTheme.dp(ctx, 10f))
                isClickable = true
                setOnClickListener { s.click() }
            }
            val icon = OverlayTheme.label(ctx, s.icon, 18f, s.tint, false).apply {
                gravity = Gravity.CENTER
            }
            val title = OverlayTheme.label(ctx, s.label, 11.5f, OverlayTheme.TEXT, true).apply {
                gravity = Gravity.CENTER
            }
            cell.addView(icon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            cell.addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            row.addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                if (s != specs.first()) marginStart = OverlayTheme.dp(ctx, 8f)
            })
        }
        return row
    }

    private fun statCell(ctx: Context, label: String, row: LinearLayout): TextView {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val l = OverlayTheme.label(ctx, label, 9.5f, OverlayTheme.TEXT_DIM, false)
        val v = OverlayTheme.label(ctx, "--", 13f, OverlayTheme.TEXT, true)
        col.addView(l)
        col.addView(v)
        row.addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        return v
    }

    private fun toggle(ctx: Context, label: String, onChange: (Boolean) -> Unit): Switch =
        Switch(ctx).apply {
            text = label
            setTextColor(OverlayTheme.TEXT)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, OverlayTheme.dp(ctx, 4f), 0, OverlayTheme.dp(ctx, 4f))
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }

    fun setToggleStates(hud: Boolean, game: Boolean, toolbar: Boolean) {
        hudSwitch.isChecked = hud
        gameSwitch.isChecked = game
        toolbarSwitch.isChecked = toolbar
    }

    fun update(s: PerfSnapshot) {
        ramValue.text = "${Format.size(s.ramUsed)}\n${s.ramPercent}%"
        cpuValue.text = "${s.cpuPercent}%"
        tempValue.text = if (s.cpuTempC > 0) String.format("%.0f°C", s.cpuTempC) else "--"
        batValue.text = "${s.batteryPercent}%" + if (s.charging) " ⚡" else ""
        netValue.text = "${Format.speed(s.netDownBps)}\n${s.networkType}"
    }
}
