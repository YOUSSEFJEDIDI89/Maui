package com.maui.system.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.maui.system.overlay.MauiOverlayService

/**
 * Restarts the Maui layer after a reboot or an app update, but only if the
 * user had it switched on.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = Prefs.get(context)
        if (!prefs.overlayEnabled) return
        try {
            if (Settings.canDrawOverlays(context)) {
                MauiOverlayService.start(context)
            }
        } catch (t: Throwable) {
        }
    }
}
