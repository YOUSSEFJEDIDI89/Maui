package com.maui.system.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maui.system.R
import com.maui.system.core.AppHelper
import com.maui.system.core.AppStorage
import com.maui.system.databinding.ActivityAppsBinding
import kotlinx.coroutines.launch

class AppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppsBinding
    private lateinit var adapter: AppAdapter
    private var all: List<AppStorage> = emptyList()
    private var bySize = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppAdapter { app ->
            startActivity(
                Intent(this, AppDetailsActivity::class.java)
                    .putExtra(AppDetailsActivity.EXTRA_PACKAGE, app.packageName)
            )
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.btnSort.setOnClickListener {
            bySize = !bySize
            binding.btnSort.setText(if (bySize) R.string.apps_sort_size else R.string.apps_sort_name)
            render()
        }

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render()
            }
        })

        binding.status.setOnClickListener {
            if (!AppHelper.hasUsageAccess(this)) {
                AppHelper.openUsageAccessSettings(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        binding.status.text = getString(R.string.loading)
        lifecycleScope.launch {
            if (!AppHelper.hasUsageAccess(this@AppsActivity)) {
                // No usage access: still list the installed apps, but without sizes.
                val ctx = this@AppsActivity
                val fallback = AppHelper.userApps(ctx).map {
                    AppStorage(
                        packageName = it.packageName,
                        label = AppHelper.labelOf(ctx, it.packageName),
                        appBytes = 0,
                        cacheBytes = 0,
                        dataBytes = 0,
                        isSystem = false,
                        uid = it.uid
                    )
                }.sortedBy { it.label.lowercase() }
                all = fallback
                render()
                binding.status.text = getString(R.string.apps_usage_access_needed)
                binding.status.setOnClickListener {
                    AppHelper.openUsageAccessSettings(this@AppsActivity)
                }
                return@launch
            }
            val stats = AppHelper.storageStats(this@AppsActivity)
            all = stats
            render()
            binding.status.text = getString(R.string.apps_count, all.size)
        }
    }

    private fun render() {
        val query = binding.search.text?.toString()?.trim()?.lowercase() ?: ""
        var list = all
        if (query.isNotEmpty()) {
            list = list.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
        list = if (bySize) {
            list.sortedByDescending { it.totalBytes }
        } else {
            list.sortedBy { it.label.lowercase() }
        }
        adapter.submit(list)
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.apps_empty, Toast.LENGTH_SHORT).show()
        }
    }
}
