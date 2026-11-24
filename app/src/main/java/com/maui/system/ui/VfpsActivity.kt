package com.maui.system.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.maui.system.databinding.ActivityVfpsBinding

/**
 * The VFPS screen on its own page.
 *
 * It is reachable from the Games tab and from the in-game tool panel, and it
 * keeps the bottom navigation at five items so the labels stay readable.
 */
class VfpsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVfpsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVfpsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(binding.vfpsContainer.id, VfpsFragment())
                .commitNow()
        }
    }

    companion object {
        /** Opens the VFPS screen. */
        fun open(context: Context) {
            context.startActivity(Intent(context, VfpsActivity::class.java))
        }
    }
}
