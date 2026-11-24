package com.maui.system.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maui.system.R
import com.maui.system.core.DeviceProfiler
import com.maui.system.core.FrameMetrics
import com.maui.system.core.GameCatalog
import com.maui.system.core.GameEntry
import com.maui.system.core.GameOptimizer
import com.maui.system.core.GameProfile
import com.maui.system.core.GameSessionStore
import com.maui.system.core.Prefs
import com.maui.system.core.VfpsEngine
import com.maui.system.databinding.FragmentVfpsBinding
import com.maui.system.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * The VFPS screen — Maui's frame provider.
 *
 * It shows the three numbers that actually describe how smooth a game is:
 *
 *  - **VFPS** — the ceiling the panel can reach (the physical limit).
 *  - **RFPS** — the frames the game really produced, measured from the
 *    compositor's own frame timeline.
 *  - the **1% low**, the **jank share** and the **frame-pacing deviation**,
 *    which is what the eye feels.
 *
 * Nothing here is a guess: when the frame timeline cannot be read, the screen
 * says so instead of printing a number.
 */
class VfpsFragment : Fragment() {

    private var _binding: FragmentVfpsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var sessions: GameSessionStore
    private val handler = Handler(Looper.getMainLooper())

    private var selectedGame: GameEntry? = null
    private var games: List<GameEntry> = emptyList()

    /** The measured frame times of the selected game, for the graph. */
    private var gameFrames = emptyList<Double>()

    private val frameProbe = object : ChoreographerFrameProbe() {
        override fun onFrame(frameMs: Double) {
            _binding?.let {
                it.graph.addFrame(frameMs)
                updateGraphStats()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVfpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs.get(requireContext())
        sessions = GameSessionStore.get(requireContext())

        binding.btnBoost.setOnClickListener { runBoost() }
        binding.btnRestore.setOnClickListener { restoreDefaults() }
        binding.btnPickGame.setOnClickListener { pickGame() }

        refreshCeiling()
        refreshGames()
        refreshSessions()
    }

    override fun onResume() {
        super.onResume()
        startSelfProbe()
    }

    override fun onPause() {
        super.onPause()
        frameProbe.stop()
    }

    // ------------------------------------------------------------- ceiling

    private fun refreshCeiling() {
        val ceiling = VfpsEngine.panelCeiling(requireContext())
        val now = VfpsEngine.currentRefresh(requireContext())
        binding.ceilingValue.text = if (ceiling > 0) "$ceiling" else "--"
        binding.ceilingNote.text = if (ceiling > 0) {
            getString(R.string.vfps_ceiling_now, Format.hz(now))
        } else {
            getString(R.string.vfps_ceiling_unknown)
        }
        binding.graph.setBudget(FrameMetrics.budgetOf(ceiling.coerceAtLeast(1)), "$ceiling Hz")
    }

    // --------------------------------------------------------------- games

    private fun refreshGames() {
        games = GameCatalog.detect(requireContext(), prefs.userGamePackages)
        selectedGame = games.firstOrNull { it.packageName == selectedGame?.packageName } ?: games.firstOrNull()
        binding.gameName.text = selectedGame?.label ?: getString(R.string.vfps_no_game)
        measureSelectedGame()
    }

    private fun pickGame() {
        if (games.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vfps_no_game)
                .setMessage(R.string.vfps_no_game_desc)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = games.map { "${it.label}\n${it.packageName}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.vfps_pick_game)
            .setItems(labels) { _, which ->
                selectedGame = games[which]
                binding.gameName.text = selectedGame?.label
                measureSelectedGame()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Reads the selected game's frame timeline — root only, and honest. */
    private fun measureSelectedGame() {
        val pkg = selectedGame?.packageName
        if (pkg == null) {
            binding.rfpsValue.text = "--"
            binding.rfpsNote.setText(R.string.vfps_rfps_note)
            binding.usage.text = getString(R.string.vfps_usage_none)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = VfpsEngine.measure(requireContext(), pkg)
            val frames = if (snapshot.measured) {
                val layer = com.maui.system.core.SurfaceFlingerProbe.layerFor(pkg)
                if (layer != null) {
                    FrameMetrics.durationsMs(com.maui.system.core.SurfaceFlingerProbe.timestampsFor(layer))
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
            handler.post {
                if (_binding == null) return@post
                applyMeasurement(
                    rfps = snapshot.rfps,
                    source = snapshot.source,
                    ceiling = snapshot.vfps,
                    frames = frames,
                    measured = snapshot.measured,
                    note = snapshot.note
                )
            }
        }
    }

    /**
     * Publishes a measurement on the screen. It is the single place where a
     * number reaches the UI, so what is shown is always what was measured.
     */
    internal fun applyMeasurement(
        rfps: Int,
        source: String,
        ceiling: Int,
        frames: List<Double>,
        measured: Boolean,
        note: String
    ) {
        val b = _binding ?: return
        gameFrames = frames
        // The ceiling that is shown is the one the measurement was taken
        // against, so the two numbers on the card always belong together.
        if (ceiling > 0) {
            b.ceilingValue.text = "$ceiling"
            b.ceilingNote.text = getString(R.string.vfps_ceiling_now, "$ceiling Hz")
        }
        b.rfpsValue.text = if (measured) "$rfps" else "--"
        b.rfpsNote.text = if (measured) {
            getString(R.string.vfps_rfps_measured, source, ceiling)
        } else {
            note
        }
        b.usage.text = if (measured) {
            getString(R.string.vfps_usage, FrameMetrics.ceilingUsage(rfps, ceiling), ceiling)
        } else {
            getString(R.string.vfps_usage_none)
        }
        if (frames.size > 2) {
            b.graph.setFrames(frames, FrameMetrics.budgetOf(ceiling.coerceAtLeast(1)), "$ceiling Hz")
        }
        updateGraphStats()
    }

    // --------------------------------------------------------------- graph

    private fun startSelfProbe() {
        frameProbe.start()
    }

    /** Stops the repeating frame probe — used by the screenshot renderer. */
    internal fun stopSelfProbe() {
        frameProbe.stop()
    }

    private fun updateGraphStats() {
        val b = _binding ?: return
        val ceiling = VfpsEngine.panelCeiling(requireContext()).coerceAtLeast(1)
        val samples = if (gameFrames.size > 2) gameFrames else b.graph.samples()
        val effective = FrameMetrics.stats(samples, ceiling)
        b.statLow.text = getString(R.string.vfps_stat_low, effective.low1Percent)
        b.statJank.text = getString(R.string.vfps_stat_jank, effective.jankPercent)
        b.statPacing.text = getString(R.string.vfps_stat_pacing, FrameMetrics.pacingScore(effective))
    }

    // --------------------------------------------------------------- boost

    private fun runBoost() {
        val pkg = selectedGame?.packageName
        binding.btnBoost.isEnabled = false
        binding.report.text = getString(R.string.vfps_boost_running)
        lifecycleScope.launch(Dispatchers.IO) {
            val report = VfpsEngine.boost(requireContext(), pkg, GameProfile.PERFORMANCE)
            handler.post {
                if (_binding == null) return@post
                binding.btnBoost.isEnabled = true
                binding.report.text = buildString {
                    append(getString(R.string.vfps_report_summary, report.appliedCount, report.skippedCount))
                    append("\n\n")
                    for (step in report.steps) {
                        append(if (step.applied) "✅ " else "⛔ ")
                        append(step.title)
                        append(" — ")
                        append(step.detail)
                        append("\n")
                    }
                }
                refreshCeiling()
                measureSelectedGame()
            }
        }
    }

    private fun restoreDefaults() {
        GameOptimizer.restore(requireContext())
        VfpsEngine.restoreRefresh(requireContext())
        binding.report.text = getString(R.string.vfps_restored)
        refreshCeiling()
    }

    // ------------------------------------------------------------ sessions

    private fun refreshSessions() {
        val list = sessions.history()
        binding.sessions.removeAllViews()
        if (list.isEmpty()) {
            binding.sessionsSummary.setText(R.string.vfps_no_sessions)
            return
        }
        val total = sessions.totalPlaySeconds()
        binding.sessionsSummary.text = getString(
            R.string.vfps_sessions_summary,
            list.size,
            total / 3600,
            (total % 3600) / 60,
            sessions.averageFps()
        )
        val density = resources.displayMetrics.density
        for (s in list.take(8)) {
            val row = TextView(requireContext())
            row.textSize = 12f
            row.setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            row.text = getString(
                R.string.vfps_session_row,
                s.gameName.ifEmpty { s.packageName },
                s.summary(),
                s.batteryUsed
            )
            binding.sessions.addView(row)
        }
    }

    override fun onStart() {
        super.onStart()
        refreshCeiling()
        refreshSessions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        frameProbe.stop()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }

    /**
     * A tiny Choreographer probe that measures the real frame duration of the
     * UI. It is what fills the graph when no game is being measured.
     */
    private open class ChoreographerFrameProbe : android.view.Choreographer.FrameCallback {
        private var last = 0L
        private var running = false

        open fun onFrame(frameMs: Double) {}

        fun start() {
            if (running) return
            running = true
            last = 0L
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }

        fun stop() {
            running = false
            android.view.Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (last != 0L) {
                val ms = (frameTimeNanos - last) / 1_000_000.0
                if (ms > 0 && ms < 500) onFrame(ms)
            }
            last = frameTimeNanos
            if (running) android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
