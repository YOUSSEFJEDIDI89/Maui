package com.maui.system.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maui.system.R
import com.maui.system.core.Cleaner
import com.maui.system.core.Prefs
import com.maui.system.databinding.FragmentCleanerBinding
import com.maui.system.scanner.JunkItem
import com.maui.system.scanner.StorageScanner
import com.maui.system.util.Format
import kotlinx.coroutines.launch

class CleanerFragment : Fragment() {

    private var _binding: FragmentCleanerBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var scanner: StorageScanner
    private lateinit var adapter: JunkAdapter
    private var scanned = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCleanerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs.get(requireContext())
        scanner = StorageScanner(requireContext())

        adapter = JunkAdapter { _, _ -> updateSummary() }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.btnScan.setOnClickListener { scan() }
        binding.btnClean.setOnClickListener { clean() }

        val selectAll = TextView(requireContext()).apply {
            text = getString(R.string.clean_select_all)
            setTextColor(requireContext().getColor(R.color.maui_cyan))
            textSize = 12.5f
            setPadding(0, 12, 0, 0)
            setOnClickListener {
                val allSelected = adapter.selectedItems().size == adapter.itemCount
                adapter.selectAll(!allSelected)
                updateSummary()
            }
        }
        (binding.summaryCard.getChildAt(0) as? ViewGroup)?.addView(selectAll)

        updateSummary()
        if (!scanner.hasAllFilesAccess()) {
            binding.status.text = getString(R.string.clean_need_access)
        }
    }

    private fun scan() {
        binding.btnScan.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.status.text = getString(R.string.clean_scanning)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = scanner.scan(
                includeHidden = prefs.includeHidden,
                includeDuplicates = prefs.includeDuplicates
            ) { path ->
                binding.status.text = path
            }
            scanned = true
            adapter.submit(result.items)
            binding.progress.visibility = View.GONE
            binding.btnScan.isEnabled = true
            binding.status.text = getString(
                R.string.clean_roots,
                result.scannedRoots.size,
                Format.duration(result.durationMs)
            )
            updateSummary()
            if (result.items.isEmpty()) {
                Toast.makeText(requireContext(), R.string.clean_empty, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateSummary() {
        val selected = adapter.selectedItems()
        val bytes = selected.sumOf { it.sizeBytes }
        binding.totalSize.text = Format.size(bytes)
        binding.totalSub.text = if (adapter.itemCount == 0) {
            getString(R.string.clean_none)
        } else {
            "${selected.size} of ${adapter.itemCount} items selected"
        }
        binding.btnClean.isEnabled = selected.isNotEmpty()
    }

    private fun clean() {
        val selected: List<JunkItem> = adapter.selectedItems()
        if (selected.isEmpty()) return
        binding.btnClean.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val report = Cleaner.clean(requireContext(), selected)
            prefs.lastCleanSize = report.freedBytes
            prefs.lastCleanTime = System.currentTimeMillis()
            binding.btnClean.isEnabled = true
            val msg = getString(R.string.clean_done, "${report.deletedFiles} items", Format.size(report.freedBytes))
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            // Re-scan so the numbers on screen stay true.
            scan()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
