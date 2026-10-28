package com.maui.system.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.maui.system.R
import com.maui.system.ui.MainActivity
import com.maui.system.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground cleaner. Runs a real cache cleanup away from the UI and
 * reports exactly how much space came back.
 */
class CleanerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channelId = "maui_cleaner"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            val report = Cleaner.cleanOwnCaches(this@CleanerService)
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            am.getMemoryInfo(android.app.ActivityManager.MemoryInfo())
            notify(
                "Maui Cleaner",
                "Reclaimed ${Format.size(report.freedBytes)} from app caches"
            )
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                getString(R.string.channel_overlay),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun startForegroundSafely() {
        val open = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.clean_title))
            .setContentText(getString(R.string.loading))
            .setSmallIcon(R.drawable.ic_maui_stat)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1001, n)
            }
        } catch (t: Throwable) {
        }
    }

    private fun notify(title: String, text: String) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1002, NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_maui_stat)
                .setAutoCancel(true)
                .build())
        } catch (t: Throwable) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
