package com.maui.system.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maui.system.R
import com.maui.system.core.Cleaner
import com.maui.system.core.DeviceProfile
import com.maui.system.core.DeviceProfiler
import com.maui.system.core.PerfSnapshot
import com.maui.system.core.PerformanceSampler
import com.maui.system.core.Prefs
import com.maui.system.core.RootShell
import com.maui.system.databinding.FragmentDashboardBinding
import com.maui.system.overlay.MauiOverlayService
import com.maui.system.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var sampler: PerformanceSampler

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs.get(requireContext())
        sampler = PerformanceSampler(requireContext())

        val profile = DeviceProfiler.read(requireContext())
        binding.headerDevice.text = "${profile.displayName} • Android ${profile.androidVersion} • ${profile.cpuCores} cores"
        binding.headerRoot.text = if (RootShell.isRooted) {
            getString(R.string.dash_root_on)
        } else {
            getString(R.string.dash_root_off)
        }

        binding.tileRam.statLabel.text = getString(R.string.stat_ram)
        binding.tileCpu.statLabel.text = getString(R.string.stat_cpu)
        binding.tileTemp.statLabel.text = getString(R.string.stat_temp)
        binding.tileFps.statLabel.text = getString(R.string.stat_fps)
        binding.tileBattery.statLabel.text = getString(R.string.stat_battery)
        binding.tileNet.statLabel.text = getString(R.string.stat_net)

        binding.btnBoost.setOnClickListener { boost() }

        binding.switchLayers.setOnCheckedChangeListener { btn, checked ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            setLayers(checked)
        }
        binding.switchGameMode.setOnCheckedChangeListener { btn, checked ->
            if (!btn.isPressed) return@setOnCheckedChangeListener
            setGameMode(checked)
        }

        refreshSwitches()
        updateStorage(profile)
    }

    private fun updateStorage(profile: DeviceProfile) {
        binding.storageText.text = Format.size(profile.internalUsed) + " / " + Format.size(profile.internalTotal)
        binding.storageBar.progress = profile.internalUsedPercent
        binding.storageExtra.text = buildString {
            append("Free ").append(Format.size(profile.internalFree))
            if (profile.externalTotal > 0) {
                append("  •  SD card ").append(Format.size(profile.externalFree))
                append(" free of ").append(Format.size(profile.externalTotal))
            }
        }
    }

    private fun refreshSwitches() {
        binding.switchLayers.isChecked = prefs.overlayEnabled
        binding.switchGameMode.isChecked = prefs.gameMode
        binding.layersStatus.text = if (prefs.overlayEnabled) {
            "Maui Layers is running — look for the M bubble on your screen"
        } else {
            "Enable to get the floating Maui panel over any app"
        }
    }

    private fun setLayers(enabled: Boolean) {
        if (enabled) {
            if (!Settings.canDrawOverlays(requireContext())) {
                binding.switchLayers.isChecked = false
                prefs.overlayEnabled = false
                Toast.makeText(
                    requireContext(),
                    "Allow Maui to draw over other apps",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                )
                return
            }
            prefs.overlayEnabled = true
            MauiOverlayService.start(requireContext())
            Toast.makeText(requireContext(), R.string.dash_starting_layers, Toast.LENGTH_SHORT).show()
        } else {
            prefs.overlayEnabled = false
            MauiOverlayService.stop(requireContext())
        }
        refreshSwitches()
    }

    private fun setGameMode(enabled: Boolean) {
        prefs.gameMode = enabled
        val scale = if (enabled) 0f else prefs.animationScale
        var applied = false
        try {
            Settings.System.putFloat(requireContext().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
            Settings.System.putFloat(requireContext().contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, scale)
            Settings.System.putFloat(requireContext().contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, scale)
            applied = true
        } catch (t: Throwable) {
        }
        if (enabled) {
            viewLifecycleOwner.lifecycleScope.launch { Cleaner.boostMemory(requireContext()) }
            if (!applied) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.settings_write_settings_desc),
                    Toast.LENGTH_LONG
                ).show()
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${requireContext().packageName}")
                        )
                    )
                } catch (t: Throwable) {
                }
            }
        }
        refreshSwitches()
    }

    private fun boost() {
        binding.btnBoost.isEnabled = false
        binding.btnBoost.text = getString(R.string.dash_boosting)
        viewLifecycleOwner.lifecycleScope.launch {
            val report = Cleaner.boostMemory(requireContext())
            binding.btnBoost.isEnabled = true
            binding.btnBoost.text = getString(R.string.dash_boost_now)
            Toast.makeText(requireContext(), report.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSwitches()
        viewLifecycleOwner.lifecycleScope.launch {
            sampler.start()
            while (isActive) {
                val snap = sampler.sample()
                render(snap)
                delay(1000)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sampler.stop()
    }

    internal fun render(s: PerfSnapshot) {
        binding.tileRam.statValue.text = Format.size(s.ramUsed)
        binding.tileRam.statExtra.text = "${s.ramPercent}% \u2022 ${Format.size(s.ramAvailable)} free"
        binding.tileCpu.statValue.text = "${s.cpuPercent}%"
        binding.tileCpu.statExtra.text = s.cpuGovernor.ifEmpty { "—" }
        binding.tileTemp.statValue.text = if (s.cpuTempC > 0) String.format("%.0f°C", s.cpuTempC) else "—"
        binding.tileTemp.statExtra.text = if (s.cpuTempC > 45) "High" else "Normal"
        binding.tileFps.statValue.text = if (s.fps > 0) s.fps.toString() else "—"
        binding.tileFps.statExtra.text = s.fpsSource.ifEmpty { "Maui UI" }
        binding.tileBattery.statValue.text = "${s.batteryPercent}%"
        binding.tileBattery.statExtra.text = if (s.charging) "Charging" else "Discharging"
        binding.tileNet.statValue.text = Format.speed(s.netDownBps)
        binding.tileNet.statExtra.text = s.networkType
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
