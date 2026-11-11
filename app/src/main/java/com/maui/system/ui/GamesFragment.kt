package com.maui.system.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.maui.system.R
import com.maui.system.core.AppHelper
import com.maui.system.core.GameCatalog
import com.maui.system.core.GameEntry
import com.maui.system.core.GameOptimizer
import com.maui.system.core.GameProfile
import com.maui.system.core.Prefs
import com.maui.system.databinding.FragmentGamesBinding
import com.maui.system.overlay.MauiOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The game launcher.
 *
 * Every card is a real game on the device. Pressing Play applies the chosen
 * profile to the OS (memory, animations, refresh rate, DND and — with root —
 * governor, GPU boost and doze protection), then launches the game and opens
 * the in-game HUD with the real frame rate.
 */
class GamesFragment : Fragment() {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var adapter: GameAdapter
    private var all: List<GameEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs.get(requireContext())

        adapter = GameAdapter(
            onPlay = { game -> play(game) },
            onProfile = { game -> cycleProfile(game) }
        )
        binding.list.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.list.adapter = adapter

        binding.btnAdd.setOnClickListener { showAddGameDialog() }
        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = render()
        })
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val games = withContext(Dispatchers.IO) {
                val detected = GameCatalog.detect(requireContext(), prefs.userGamePackages)
                val overrides = prefs.gameProfileOverrides
                detected.map {
                    it.copy(profile = GameProfile.fromKey(overrides[it.packageName]))
                }
            }
            all = games
            render()
        }
    }

    private fun render() {
        val query = binding.search.text?.toString()?.trim()?.lowercase() ?: ""
        val list = if (query.isEmpty()) {
            all
        } else {
            all.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
        adapter.submit(list)
        binding.emptyCard.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.list.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

        val ff = all.count { it.freeFire }
        binding.status.text = if (all.isEmpty()) {
            getString(R.string.games_none)
        } else {
            buildString {
                append(getString(R.string.games_detected, all.size))
                if (ff > 0) append(" • ").append(getString(R.string.games_freefire_count, ff))
                append(" • ").append(getString(R.string.games_root_hint))
            }
        }
    }

    /** Cycles the profile of one game and remembers the choice. */
    private fun cycleProfile(game: GameEntry) {
        val next = when (game.profile) {
            GameProfile.PERFORMANCE -> GameProfile.BALANCED
            GameProfile.BALANCED -> GameProfile.SAVER
            GameProfile.SAVER -> GameProfile.PERFORMANCE
        }
        val overrides = prefs.gameProfileOverrides.toMutableMap()
        overrides[game.packageName] = next.key
        prefs.gameProfileOverrides = overrides
        load()
    }

    /**
     * Applies the profile, launches the game and opens the in-game HUD.
     * The report tells the user exactly which changes took effect.
     */
    private fun play(game: GameEntry) {
        binding.status.text = getString(R.string.games_optimising)
        viewLifecycleOwner.lifecycleScope.launch {
            val report = withContext(Dispatchers.Default) {
                GameOptimizer.optimize(
                    context = requireContext(),
                    packageName = game.packageName,
                    profile = game.profile,
                    keepScreenOn = prefs.keepScreenOnInGame,
                    isFreeFire = game.freeFire
                )
            }
            AppHelper.launchApp(requireContext(), game.packageName)
            if (prefs.showGameHud) {
                MauiOverlayService.startGameHud(requireContext())
            }
            binding.status.text = getString(R.string.games_optimised)
            showReport(game, report)
        }
    }

    /** A dialog that lists exactly what worked and what did not. */
    private fun showReport(game: GameEntry, report: com.maui.system.core.OptimizationReport) {
        val ctx = requireContext()
        val lines = report.steps.joinToString("\n") { step ->
            val mark = if (step.applied) "✅" else "•"
            "$mark ${step.title} — ${step.detail}"
        }
        AlertDialog.Builder(ctx)
            .setTitle("${game.label} · ${profileName(game.profile)}")
            .setMessage(lines)
            .setPositiveButton(R.string.games_play_now) { _, _ ->
                AppHelper.launchApp(ctx, game.packageName)
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun profileName(profile: GameProfile): String = when (profile) {
        GameProfile.PERFORMANCE -> getString(R.string.games_profile_performance)
        GameProfile.BALANCED -> getString(R.string.games_profile_balanced)
        GameProfile.SAVER -> getString(R.string.games_profile_saver)
    }

    /** Lets the user add any installed app as a game. */
    private fun showAddGameDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                val known = (GameCatalog.detect(requireContext(), prefs.userGamePackages) +
                        GameCatalog.suggestions(requireContext(), prefs.userGamePackages))
                    .map { it.packageName }
                    .toSet()
                AppHelper.userApps(requireContext())
                    .filter { it.packageName !in known }
                    .map { AppHelper.labelOf(requireContext(), it.packageName) to it.packageName }
                    .sortedBy { it.first.lowercase() }
            }
            if (candidates.isEmpty()) {
                Toast.makeText(requireContext(), R.string.games_none, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = candidates.map { it.first }
            val listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.games_choose)
                .setAdapter(listAdapter) { _, which ->
                    val pkg = candidates[which].second
                    prefs.userGamePackages = prefs.userGamePackages + pkg
                    load()
                    Toast.makeText(requireContext(), R.string.games_added, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
