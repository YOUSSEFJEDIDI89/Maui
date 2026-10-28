package com.maui.system.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.maui.system.R
import com.maui.system.core.DeviceProfiler
import com.maui.system.databinding.FragmentFilesBinding
import com.maui.system.scanner.StorageScanner
import com.maui.system.util.Format
import kotlinx.coroutines.launch

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private lateinit var scanner: StorageScanner

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        scanner = StorageScanner(requireContext())

        addRow(getString(R.string.files_internal), "📱") {
            open(FileExplorerActivity.MODE_INTERNAL, null)
        }
        addRow(getString(R.string.files_external), "💾") {
            open(FileExplorerActivity.MODE_INTERNAL, null)
        }
        addRow(getString(R.string.files_hidden), "🕵️") {
            open(FileExplorerActivity.MODE_HIDDEN, null)
        }
        addRow(getString(R.string.files_big), "🐘") {
            open(FileExplorerActivity.MODE_BIG, null)
        }
        addRow(getString(R.string.files_dupes), "📑") {
            open(FileExplorerActivity.MODE_DUPLICATES, null)
        }
        addRow(getString(R.string.apps_title), "📦") {
            startActivity(Intent(requireContext(), AppsActivity::class.java))
        }

        binding.btnScanFiles.setOnClickListener { scan() }
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val profile = DeviceProfiler.read(requireContext())
        binding.status.text = buildString {
            append("Internal: ").append(Format.size(profile.internalFree)).append(" free of ")
            append(Format.size(profile.internalTotal))
            if (profile.externalTotal > 0) {
                append("\nSD card: ").append(Format.size(profile.externalFree)).append(" free of ")
                append(Format.size(profile.externalTotal))
            }
            if (!scanner.hasAllFilesAccess()) {
                append("\n").append(getString(R.string.clean_need_access))
            }
        }
    }

    private fun addRow(title: String, glyph: String, onClick: () -> Unit) {
        val ctx = requireContext()
        val row = com.google.android.material.card.MaterialCardView(ctx).apply {
            radius = 18f * ctx.resources.displayMetrics.density
            cardElevation = 0f
            setCardBackgroundColor(0x1FFFFFFF)
            val pad = (16 * ctx.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            setOnClickListener { onClick() }
        }
        val line = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val icon = android.widget.TextView(ctx).apply {
            text = glyph
            textSize = 18f
        }
        val titleView = android.widget.TextView(ctx).apply {
            text = title
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val chevron = android.widget.TextView(ctx).apply {
            text = "›"
            textSize = 20f
            alpha = 0.5f
        }
        line.addView(icon)
        line.addView(
            titleView,
            android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (14 * ctx.resources.displayMetrics.density).toInt()
            }
        )
        line.addView(chevron)
        row.addView(line)
        binding.categories.addView(row)
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = (10 * ctx.resources.displayMetrics.density).toInt()
        row.layoutParams = lp
    }

    private fun open(mode: Int, path: String?) {
        startActivity(
            Intent(requireContext(), FileExplorerActivity::class.java)
                .putExtra(FileExplorerActivity.EXTRA_MODE, mode)
                .putExtra(FileExplorerActivity.EXTRA_PATH, path)
        )
    }

    private fun scan() {
        binding.btnScanFiles.isEnabled = false
        binding.status.text = getString(R.string.clean_scanning)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = scanner.scan()
            binding.btnScanFiles.isEnabled = true
            val hidden = result.categorySize(com.maui.system.scanner.JunkCategory.HIDDEN)
            val big = result.categorySize(com.maui.system.scanner.JunkCategory.BIG_FILES)
            val dupes = result.categorySize(com.maui.system.scanner.JunkCategory.DUPLICATES)
            binding.status.text = buildString {
                append("Hidden: ").append(Format.size(hidden)).append("\n")
                append("Large files: ").append(Format.size(big)).append("\n")
                append("Duplicates: ").append(Format.size(dupes)).append("\n")
                append("Scanned in ").append(Format.duration(result.durationMs))
            }
            if (result.errors.isNotEmpty()) {
                Toast.makeText(
                    requireContext(),
                    result.errors.first(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
