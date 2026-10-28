package com.maui.system.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.maui.system.R
import com.maui.system.core.AppHelper
import com.maui.system.core.Prefs
import com.maui.system.core.RootShell
import com.maui.system.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs.get(this)

        binding.version.text = getString(R.string.settings_version, appVersion())
        binding.rootStatus.text = getString(R.string.settings_root_status) + ": " + getString(
            if (RootShell.isRooted) R.string.settings_root_available else R.string.settings_root_unavailable
        )

        buildPermissionRows()

        binding.switchHud.isChecked = prefs.hudEnabled
        binding.switchHud.setOnCheckedChangeListener { _, checked ->
            prefs.hudEnabled = checked
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    android.widget.Toast.makeText(this, getString(R.string.settings_overlay_desc), android.widget.Toast.LENGTH_LONG).show()
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                    return@setOnCheckedChangeListener
                }
                com.maui.system.overlay.MauiOverlayService.start(this)
            }
        }

        binding.switchToolbar.isChecked = prefs.gameToolbarEnabled
        binding.switchToolbar.setOnCheckedChangeListener { _, checked ->
            prefs.gameToolbarEnabled = checked
        }

        binding.sliderSize.value = prefs.bubbleSize.toFloat().coerceIn(40f, 96f)
        binding.sliderSize.addOnChangeListener { _, value, _ ->
            prefs.bubbleSize = value.toInt()
        }

        binding.sliderOpacity.value = prefs.bubbleOpacity.toFloat().coerceIn(30f, 100f)
        binding.sliderOpacity.addOnChangeListener { _, value, _ ->
            prefs.bubbleOpacity = value.toInt()
        }

        when (prefs.themeMode) {
            1 -> binding.themeLight.isChecked = true
            2 -> binding.themeDark.isChecked = true
            else -> binding.themeSystem.isChecked = true
        }
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.theme_light -> 1
                R.id.theme_dark -> 2
                else -> 0
            }
            prefs.themeMode = mode
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun buildPermissionRows() {
        val container: LinearLayout = binding.permissionsContainer
        container.removeAllViews()

        addPermissionRow(
            container,
            getString(R.string.settings_overlay),
            getString(R.string.settings_overlay_desc),
            isGranted = { Settings.canDrawOverlays(this) },
            onGrant = {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        )

        addPermissionRow(
            container,
            getString(R.string.settings_all_files),
            getString(R.string.settings_all_files_desc),
            isGranted = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    EnvAccess.isExternalStorageManager()
                } else {
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                }
            },
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:$packageName"))
                    startActivity(i)
                } else {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 77)
                }
            }
        )

        addPermissionRow(
            container,
            getString(R.string.settings_usage),
            getString(R.string.settings_usage_desc),
            isGranted = { AppHelper.hasUsageAccess(this) },
            onGrant = { AppHelper.openUsageAccessSettings(this) }
        )

        addPermissionRow(
            container,
            getString(R.string.settings_notifications),
            getString(R.string.settings_notifications_desc),
            isGranted = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            },
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 78)
                }
            }
        )

        addPermissionRow(
            container,
            getString(R.string.settings_battery),
            getString(R.string.settings_battery_desc),
            isGranted = { isIgnoringBatteryOptimizations() },
            onGrant = {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (t: Throwable) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        )

        addPermissionRow(
            container,
            getString(R.string.settings_write_settings),
            getString(R.string.settings_write_settings_desc),
            isGranted = { Settings.System.canWrite(this) },
            onGrant = {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (t: Throwable) {
                }
            }
        )
    }

    private fun addPermissionRow(
        container: LinearLayout,
        title: String,
        desc: String,
        isGranted: () -> Boolean,
        onGrant: () -> Unit
    ) {
        val ctx = this
        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            radius = 18f * ctx.resources.displayMetrics.density
            cardElevation = 0f
            setCardBackgroundColor(0x1FFFFFFF)
            val pad = (14 * ctx.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val titleView = TextView(ctx).apply {
            text = title
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val descView = TextView(ctx).apply {
            text = desc
            textSize = 11f
            alpha = 0.65f
        }
        texts.addView(titleView)
        texts.addView(descView)

        val button = MaterialButton(ctx).apply {
            text = getString(R.string.settings_grant)
            textSize = 11.5f
            minWidth = 0
            minimumWidth = 0
            setOnClickListener {
                onGrant()
                refreshPermissionStates()
            }
        }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button)
        card.addView(row)
        container.addView(card)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = (10 * ctx.resources.displayMetrics.density).toInt()
        card.layoutParams = lp
    }

    private fun refreshPermissionStates() {
        val container: LinearLayout = binding.permissionsContainer
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i) as? ViewGroup ?: continue
            val row = card.getChildAt(0) as? ViewGroup ?: continue
            val button = row.getChildAt(row.childCount - 1) as? MaterialButton ?: continue
            button.text = getString(R.string.settings_grant)
            button.isEnabled = true
        }
        // Update each button state by re-evaluating the same checks
        val checks = listOf<() -> Boolean>(
            { Settings.canDrawOverlays(this) },
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) EnvAccess.isExternalStorageManager()
                else checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            },
            { AppHelper.hasUsageAccess(this) },
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                else true
            },
            { isIgnoringBatteryOptimizations() },
            { Settings.System.canWrite(this) }
        )
        for (i in 0 until minOf(container.childCount, checks.size)) {
            val card = container.getChildAt(i) as? ViewGroup ?: continue
            val row = card.getChildAt(0) as? ViewGroup ?: continue
            val button = row.getChildAt(row.childCount - 1) as? MaterialButton ?: continue
            if (checks[i]()) {
                button.text = getString(R.string.settings_granted)
                button.isEnabled = false
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = packageManager
        val pkg = packageName
        try {
            pm.javaClass.getMethod("isRequestPackageBatteryExemptSupported")
        } catch (t: Throwable) {
            return true
        }
        val manager = getSystemService(android.os.PowerManager::class.java)
        return manager?.isIgnoringBatteryOptimizations(pkg) == true
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (t: Throwable) {
        "1.0"
    }

}

/** Tiny helper so the environment check stays readable. */
object EnvAccess {
    fun isExternalStorageManager(): Boolean = try {
        android.os.Environment.isExternalStorageManager()
    } catch (t: Throwable) {
        false
    }
}

