package com.maui.system.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.maui.system.core.GameTools

/**
 * The in-game tool panel — Maui's "Energy Cube".
 *
 * A grid of the tools a game-space app puts one tap away: close the
 * background apps, silence the notifications, lock the brightness, freeze the
 * rotation, boost the audio, check the ping, and so on.
 *
 * Every button shows its real state and reports what actually happened. The
 * tools that need the overlay itself (crosshair, stopwatch, screenshot) are
 * handed to the service through [Actions].
 */
class GameToolsPanelView(
    context: Context,
    private val actions: Actions
) : LinearLayout(context) {

    interface Actions {
        fun onTool(id: String)
        fun onClose()
        fun onStatus(text: String)
    }

    /** The ids of the tools the panel owns. */
    object Tool {
        const val CLEAN = "clean"
        const val BOOST = "boost"
        const val DND = "dnd"
        const val BRIGHTNESS = "brightness"
        const val ROTATION = "rotation"
        const val AUDIO = "audio"
        const val RINGER = "ringer"
        const val WIFI = "wifi"
        const val BLUETOOTH = "bluetooth"
        const val PING = "ping"
        const val CROSSHAIR = "crosshair"
        const val STOPWATCH = "stopwatch"
        const val SCREENSHOT = "screenshot"
        const val TASKS = "tasks"
        const val VENDOR = "vendor"
        const val THERMAL = "thermal"
        const val HUD = "hud"
        const val VFPS = "vfps"
    }

    private val buttons = mutableMapOf<String, TextView>()
    private lateinit var toolGrid: GridLayout
    private lateinit var listView: LinearLayout
    private val statusLabel: TextView
    private var rotationIndex = 0
    private var brightnessLocked = false
    private var dndOn = false
    private var audioBoosted = false
    private var ringerSilent = false
    private var wifiOn = true
    private var bluetoothOn = true

    init {
        orientation = VERTICAL
        setPadding(
            OverlayTheme.dp(context, 12f),
            OverlayTheme.dp(context, 10f),
            OverlayTheme.dp(context, 12f),
            OverlayTheme.dp(context, 10f)
        )
        background = OverlayTheme.card(context, 26f, OverlayTheme.BG_DEEP, OverlayTheme.STROKE)
        elevation = OverlayTheme.dp(context, 14).toFloat()

        val header = LinearLayout(context).apply { orientation = HORIZONTAL }
        addView(header)

        header.addView(
            OverlayTheme.label(context, "MAUI GAME TOOLS", 11f, OverlayTheme.MAUI_CYAN, true),
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            OverlayTheme.label(context, "✕", 13f, OverlayTheme.TEXT_DIM, false).apply {
                setPadding(OverlayTheme.dp(context, 8), 0, OverlayTheme.dp(context, 4), 0)
                isClickable = true
                setOnClickListener { actions.onClose() }
            }
        )

        statusLabel = OverlayTheme.label(context, "", 10f, OverlayTheme.TEXT_DIM, false).apply {
            maxLines = 2
        }
        addView(statusLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val grid = GridLayout(context).apply {
            columnCount = 4
            rowCount = 5
        }
        toolGrid = grid
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        listView = LinearLayout(context).apply { orientation = VERTICAL }
        listView.visibility = View.GONE
        addView(listView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addTool(grid, Tool.CLEAN, "🧹", "Close apps")
        addTool(grid, Tool.BOOST, "⚡", "Boost")
        addTool(grid, Tool.HUD, "📊", "Monitor")
        addTool(grid, Tool.VFPS, "∞", "VFPS")
        addTool(grid, Tool.DND, "🔕", "Quiet")
        addTool(grid, Tool.BRIGHTNESS, "🔆", "Brightness")
        addTool(grid, Tool.ROTATION, "🔄", "Rotate")
        addTool(grid, Tool.AUDIO, "🔊", "Audio")
        addTool(grid, Tool.RINGER, "📵", "Ringer")
        addTool(grid, Tool.WIFI, "📶", "Wi-Fi")
        addTool(grid, Tool.BLUETOOTH, "🅑", "Bluetooth")
        addTool(grid, Tool.PING, "🌐", "Ping")
        addTool(grid, Tool.CROSSHAIR, "➕", "Crosshair")
        addTool(grid, Tool.STOPWATCH, "⏱", "Timer")
        addTool(grid, Tool.SCREENSHOT, "📷", "Capture")
        addTool(grid, Tool.TASKS, "🗂", "Tasks")
        addTool(grid, Tool.VENDOR, "🎮", "Booster")
        addTool(grid, Tool.THERMAL, "🌡", "Heat")
    }

    private fun addTool(grid: GridLayout, id: String, glyph: String, label: String) {
        val btn = OverlayTheme.label(context, "$glyph\n$label", 9f, OverlayTheme.TEXT, false).apply {
            gravity = Gravity.CENTER
            setPadding(
                OverlayTheme.dp(context, 4),
                OverlayTheme.dp(context, 8),
                OverlayTheme.dp(context, 4),
                OverlayTheme.dp(context, 8)
            )
            background = OverlayTheme.card(context, 100f, Color.argb(38, 255, 255, 255), Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { onToolClick(id) }
        }
        buttons[id] = btn
        val lp = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f)
        )
        lp.width = 0
        lp.height = OverlayTheme.dp(context, 52)
        lp.setMargins(OverlayTheme.dp(context, 2), OverlayTheme.dp(context, 2), OverlayTheme.dp(context, 2), OverlayTheme.dp(context, 2))
        grid.addView(btn, lp)
    }

    private fun onToolClick(id: String) {
        when (id) {
            Tool.CLEAN -> {
                val res = com.maui.system.core.GameOptimizer.releaseMemory(context)
                report("${res.title}: ${res.detail}")
            }
            Tool.BOOST -> {
                actions.onTool(Tool.BOOST)
            }
            Tool.HUD -> actions.onTool(Tool.HUD)
            Tool.VFPS -> actions.onTool(Tool.VFPS)
            Tool.DND -> {
                dndOn = !dndOn
                val res = com.maui.system.core.GameOptimizer.setDoNotDisturb(context)
                if (!res.applied) dndOn = false
                report(res.detail)
                mark(Tool.DND, dndOn)
            }
            Tool.BRIGHTNESS -> {
                brightnessLocked = !brightnessLocked
                val res = GameTools.lockBrightness(context, brightnessLocked)
                if (!res.applied) brightnessLocked = !brightnessLocked
                report(res.detail)
                mark(Tool.BRIGHTNESS, brightnessLocked)
            }
            Tool.ROTATION -> {
                rotationIndex = (rotationIndex + 1) % 3
                val mode = when (rotationIndex) {
                    0 -> GameTools.Rotation.FREE
                    1 -> GameTools.Rotation.PORTRAIT
                    else -> GameTools.Rotation.LANDSCAPE
                }
                val res = GameTools.lockRotation(context, mode)
                report(res.detail)
            }
            Tool.AUDIO -> {
                audioBoosted = !audioBoosted
                val res = GameTools.boostAudio(context, audioBoosted)
                if (!res.applied) audioBoosted = !audioBoosted
                report(res.detail)
                mark(Tool.AUDIO, audioBoosted)
            }
            Tool.RINGER -> {
                ringerSilent = !ringerSilent
                val res = GameTools.silentRinger(context, ringerSilent)
                if (!res.applied) ringerSilent = !ringerSilent
                report(res.detail)
                mark(Tool.RINGER, ringerSilent)
            }
            Tool.WIFI -> {
                wifiOn = !wifiOn
                val res = GameTools.toggleWifi(context, wifiOn)
                if (!res.applied) wifiOn = !wifiOn
                report(res.detail)
                mark(Tool.WIFI, wifiOn)
            }
            Tool.BLUETOOTH -> {
                bluetoothOn = !bluetoothOn
                val res = GameTools.toggleBluetooth(context, bluetoothOn)
                if (!res.applied) bluetoothOn = !bluetoothOn
                report(res.detail)
                mark(Tool.BLUETOOTH, bluetoothOn)
            }
            Tool.PING -> {
                actions.onStatus("measuring the latency…")
                Thread {
                    val l = GameTools.measureLatency()
                    post {
                        report(
                            if (l.ok) "${l.host}: ${l.avgMs} ms average (best ${l.minMs} ms)"
                            else "latency check failed: ${l.note}"
                        )
                    }
                }.start()
            }
            Tool.CROSSHAIR -> actions.onTool(Tool.CROSSHAIR)
            Tool.STOPWATCH -> actions.onTool(Tool.STOPWATCH)
            Tool.SCREENSHOT -> actions.onTool(Tool.SCREENSHOT)
            Tool.TASKS -> actions.onTool(Tool.TASKS)
            Tool.VENDOR -> {
                val res = GameTools.openVendorGameBooster(context)
                report(res.detail)
            }
            Tool.THERMAL -> {
                val level = GameTools.thermalLevel(context)
                report("the device is ${GameTools.thermalName(level)}")
            }
        }
    }

    /** Colours a tool button to show that it is active. */
    fun mark(id: String, on: Boolean) {
        val btn = buttons[id] ?: return
        btn.background = OverlayTheme.card(
            context,
            100f,
            if (on) Color.argb(90, 34, 211, 238) else Color.argb(38, 255, 255, 255),
            Color.TRANSPARENT
        )
    }

    /**
     * Replaces the grid with the list of running processes. Tapping one stops
     * it — a real task manager, not a decoration.
     */
    fun showProcesses(list: List<com.maui.system.core.GameTools.Process>, onKill: (com.maui.system.core.GameTools.Process) -> Unit) {
        toolGrid.visibility = View.GONE
        listView.removeAllViews()
        listView.visibility = View.VISIBLE
        if (list.isEmpty()) {
            val empty = TextView(context).apply {
                text = "no cached processes"
                textSize = 11f
                setTextColor(OverlayTheme.TEXT_DIM)
                setPadding(0, OverlayTheme.dp(context, 8), 0, 0)
            }
            listView.addView(empty)
            return
        }
        for (p in list) {
            val row = TextView(context).apply {
                text = p.name
                textSize = 11f
                setTextColor(OverlayTheme.TEXT)
                setPadding(
                    OverlayTheme.dp(context, 6),
                    OverlayTheme.dp(context, 8),
                    OverlayTheme.dp(context, 6),
                    OverlayTheme.dp(context, 8)
                )
                background = OverlayTheme.card(context, 100f, Color.argb(30, 255, 255, 255), Color.TRANSPARENT)
                isClickable = true
                setOnClickListener { onKill(p) }
            }
            listView.addView(row)
        }
    }

    /** Shows a line of feedback under the header. */
    fun report(text: String) {
        statusLabel.text = text
        actions.onStatus(text)
    }
}
