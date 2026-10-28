package com.maui.system

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.maui.system.core.Prefs

class MauiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs.get(this)
        AppCompatDelegate.setDefaultNightMode(
            when (prefs.themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
