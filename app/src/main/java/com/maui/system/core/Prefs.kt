package com.maui.system.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Maui preferences store. Holds every tunable of the Maui layer:
 * overlay behaviour, game mode, theme, and scan preferences.
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    companion object {
        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(
                context.applicationContext.getSharedPreferences("maui_prefs", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }

    // ---- Maui Layers overlay ---------------------------------------------
    var overlayEnabled: Boolean
        get() = sp.getBoolean("overlay_enabled", false)
        set(v) = sp.edit().putBoolean("overlay_enabled", v).apply()

    var overlayX: Int
        get() = sp.getInt("overlay_x", -1)
        set(v) = sp.edit().putInt("overlay_x", v).apply()

    var overlayY: Int
        get() = sp.getInt("overlay_y", -1)
        set(v) = sp.edit().putInt("overlay_y", v).apply()

    var overlayEdge: String
        get() = sp.getString("overlay_edge", "right") ?: "right"
        set(v) = sp.edit().putString("overlay_edge", v).apply()

    var hudEnabled: Boolean
        get() = sp.getBoolean("hud_enabled", false)
        set(v) = sp.edit().putBoolean("hud_enabled", v).apply()

    var gameToolbarEnabled: Boolean
        get() = sp.getBoolean("game_toolbar_enabled", true)
        set(v) = sp.edit().putBoolean("game_toolbar_enabled", v).apply()

    var bubbleSize: Int
        get() = sp.getInt("bubble_size", 58)
        set(v) = sp.edit().putInt("bubble_size", v).apply()

    var bubbleOpacity: Int
        get() = sp.getInt("bubble_opacity", 90)
        set(v) = sp.edit().putInt("bubble_opacity", v).apply()

    // ---- Game mode -------------------------------------------------------
    var gameMode: Boolean
        get() = sp.getBoolean("game_mode", false)
        set(v) = sp.edit().putBoolean("game_mode", v).apply()

    var autoBoost: Boolean
        get() = sp.getBoolean("auto_boost", false)
        set(v) = sp.edit().putBoolean("auto_boost", v).apply()

    var blockNotificationsInGame: Boolean
        get() = sp.getBoolean("block_notifications", true)
        set(v) = sp.edit().putBoolean("block_notifications", v).apply()

    var animationScale: Float
        get() = sp.getFloat("animation_scale", 1.0f)
        set(v) = sp.edit().putFloat("animation_scale", v).apply()

    // ---- Cleaner ---------------------------------------------------------
    var autoClean: Boolean
        get() = sp.getBoolean("auto_clean", false)
        set(v) = sp.edit().putBoolean("auto_clean", v).apply()

    var includeHidden: Boolean
        get() = sp.getBoolean("include_hidden", true)
        set(v) = sp.edit().putBoolean("include_hidden", v).apply()

    var includeDuplicates: Boolean
        get() = sp.getBoolean("include_duplicates", true)
        set(v) = sp.edit().putBoolean("include_duplicates", v).apply()

    var lastCleanSize: Long
        get() = sp.getLong("last_clean_size", 0L)
        set(v) = sp.edit().putLong("last_clean_size", v).apply()

    var lastCleanTime: Long
        get() = sp.getLong("last_clean_time", 0L)
        set(v) = sp.edit().putLong("last_clean_time", v).apply()

    // ---- Appearance ------------------------------------------------------
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0) // 0 system, 1 light, 2 dark
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var accent: String
        get() = sp.getString("accent", "maui") ?: "maui"
        set(v) = sp.edit().putString("accent", v).apply()

    var firstRun: Boolean
        get() = sp.getBoolean("first_run", true)
        set(v) = sp.edit().putBoolean("first_run", v).apply()
}
