package com.maui.system.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.maui.system.R
import com.maui.system.core.AppHelper
import com.maui.system.core.RootShell
import com.maui.system.databinding.ActivityAppDetailsBinding
import com.maui.system.util.Format
import kotlinx.coroutines.launch

class AppDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "package"
    }

    private lateinit var binding: ActivityAppDetailsBinding
    private var pkg: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }

        binding.name.text = AppHelper.labelOf(this, pkg)
        binding.icon.setImageDrawable(AppHelper.iconOf(this, pkg))

        binding.btnInfo.setOnClickListener { AppHelper.openAppInfo(this, pkg) }
        binding.btnStop.setOnClickListener {
            AppHelper.killBackground(this, pkg)
            AppHelper.forceStopRoot(pkg)
            Toast.makeText(this, R.string.apps_stop, Toast.LENGTH_SHORT).show()
        }
        binding.btnUninstall.setOnClickListener { AppHelper.uninstallApp(this, pkg) }
        binding.btnClearCache.setOnClickListener { clearCache() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val stats = AppHelper.storageStats(this@AppDetailsActivity).firstOrNull { it.packageName == pkg }
            if (stats == null) {
                binding.total.text = "—"
                binding.breakdown.text = getString(R.string.apps_usage_access_needed)
                binding.barApp.progress = 0
                return@launch
            }
            val total = stats.totalBytes
            binding.total.text = Format.size(total)
            binding.breakdown.text = buildString {
                append(getString(R.string.details_app)).append(": ").append(Format.size(stats.appBytes)).append("\n")
                append(getString(R.string.details_cache)).append(": ").append(Format.size(stats.cacheBytes)).append("\n")
                append(getString(R.string.details_data)).append(": ").append(Format.size(stats.dataBytes))
            }
            val cachePct = if (total > 0) ((stats.cacheBytes * 100) / total).toInt() else 0
            binding.barApp.progress = cachePct
        }
    }

    private fun clearCache() {
        if (RootShell.isRooted) {
            lifecycleScope.launch {
                val ok = AppHelper.clearAppCacheRoot(pkg)
                Toast.makeText(
                    this@AppDetailsActivity,
                    if (ok) R.string.details_clear_cache_done else R.string.details_root_required,
                    Toast.LENGTH_LONG
                ).show()
                load()
            }
        } else {
            Toast.makeText(this, R.string.details_root_required, Toast.LENGTH_LONG).show()
            AppHelper.openAppInfo(this, pkg)
        }
    }
}
