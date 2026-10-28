package com.maui.system.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maui.system.R
import com.maui.system.core.AppHelper
import com.maui.system.core.Cleaner
import com.maui.system.core.Prefs
import com.maui.system.databinding.FragmentGamesBinding
import com.maui.system.overlay.MauiOverlayService
import kotlinx.coroutines.launch

class GamesFragment : Fragment() {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var adapter: GameAdapter

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
        adapter = GameAdapter { game ->
            AppHelper.launchApp(requireContext(), game.packageName)
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.switchGameMode.isChecked = prefs.gameMode
        binding.switchGameMode.setOnCheckedChangeListener { btn, checked ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            prefs.gameMode = checked
            if (checked) {
                viewLifecycleOwner.lifecycleScope.launch {
                    Cleaner.boostMemory(requireContext())
                }
                MauiOverlayService.start(requireContext())
            }
        }

        loadGames()
    }

    override fun onResume() {
        super.onResume()
        loadGames()
        binding.switchGameMode.isChecked = prefs.gameMode
    }

    private fun loadGames() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val games = AppHelper.userApps(ctx)
                .filter { AppHelper.isGamePackage(it.packageName) }
                .map {
                    GameEntry(
                        packageName = it.packageName,
                        label = AppHelper.labelOf(ctx, it.packageName),
                        icon = AppHelper.iconOf(ctx, it.packageName),
                        installed = true
                    )
                }
                .sortedBy { it.label.lowercase() }
            adapter.submit(games)
            binding.listTitle.text = getString(R.string.games_detected, games.size)
            binding.status.text = if (games.isEmpty()) {
                getString(R.string.games_none)
            } else {
                getString(R.string.games_toolbar_desc)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
