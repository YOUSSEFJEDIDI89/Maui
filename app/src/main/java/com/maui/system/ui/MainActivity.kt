package com.maui.system.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.maui.system.R
import com.maui.system.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fragments = mutableMapOf<Int, Fragment>()
    private var currentId: Int = R.id.nav_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                binding.bottomNav.menu.findItem(R.id.nav_settings).isChecked = false
                binding.bottomNav.menu.findItem(currentId).isChecked = true
                true
            } else {
                showFragment(item.itemId)
                true
            }
        }

        if (savedInstanceState == null) {
            val tab = intent.getStringExtra("tab")
            val target = when (tab) {
                "cleaner" -> R.id.nav_cleaner
                "files" -> R.id.nav_files
                "games" -> R.id.nav_games
                else -> R.id.nav_dashboard
            }
            binding.bottomNav.selectedItemId = target
            showFragment(target)
        } else {
            currentId = savedInstanceState.getInt("current", R.id.nav_dashboard)
        }
    }

    private fun showFragment(id: Int) {
        if (currentId == id && supportFragmentManager.findFragmentById(R.id.fragment_container) != null) return
        val tx = supportFragmentManager.beginTransaction()
        tx.setReorderingAllowed(true)
        val existing = fragments[id]
        if (existing == null) {
            val f = create(id)
            fragments[id] = f
            tx.replace(R.id.fragment_container, f, "f$id")
        } else {
            tx.replace(R.id.fragment_container, existing, "f$id")
        }
        tx.commitNow()
        currentId = id
    }

    private fun create(id: Int): Fragment = when (id) {
        R.id.nav_cleaner -> CleanerFragment()
        R.id.nav_files -> FilesFragment()
        R.id.nav_games -> GamesFragment()
        else -> DashboardFragment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current", currentId)
    }
}
