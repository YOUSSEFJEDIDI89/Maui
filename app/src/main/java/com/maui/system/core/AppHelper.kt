package com.maui.system.core

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.storage.StorageManager
import java.util.concurrent.TimeUnit

/** Per-application storage + process insight. */
data class AppStorage(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val cacheBytes: Long,
    val dataBytes: Long,
    val isSystem: Boolean,
    val uid: Int
) {
    val totalBytes: Long get() = appBytes + cacheBytes + dataBytes
}

data class ForegroundApp(val packageName: String, val label: String, val since: Long)

object AppHelper {

    /**
     * Storage usage per package. StorageStatsManager is the only honest API
     * for "how much space does app X use"; it needs the Usage Access grant.
     */
    fun storageStats(context: Context): List<AppStorage> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return emptyList()
        return try {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val uid = Process.myUid()
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .filter { it.uid != uid }
                .map { appInfo ->
                    try {
                        val stats = ssm.queryStatsForPackage(
                            StorageManager.UUID_DEFAULT,
                            appInfo.packageName,
                            Process.myUserHandle()
                        )
                        AppStorage(
                            packageName = appInfo.packageName,
                            label = pm.getApplicationLabel(appInfo).toString(),
                            appBytes = stats.appBytes,
                            cacheBytes = stats.cacheBytes,
                            dataBytes = stats.dataBytes,
                            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            uid = appInfo.uid
                        )
                    } catch (t: Throwable) {
                        null
                    }
                }
                .filterNotNull()
                .filter { it.totalBytes > 0 }
                .sortedByDescending { it.totalBytes }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Foreground app, detected through Usage Access (no root needed). */
    fun foregroundApp(context: Context): ForegroundApp? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5 * 60 * 1000L, now)
            val pm = context.packageManager
            var last: ForegroundApp? = null
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    val pkg = event.packageName ?: continue
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (t: Throwable) {
                        pkg
                    }
                    last = ForegroundApp(pkg, label, event.timeStamp)
                }
            }
            last
        } catch (t: Throwable) {
            null
        }
    }

    /** True when the user granted Usage Access to Maui. */
    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (t: Throwable) {
        false
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
        }
    }

    /**
     * Frees the RAM of a background app. This is the real deal: the system
     * kills the process, the memory really goes back to the pool.
     */
    fun killBackground(context: Context, packageName: String): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)
        true
    } catch (t: Throwable) {
        false
    }

    /** Kills every background process we are allowed to kill. */
    fun killAllBackground(context: Context): Int {
        var killed = 0
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val procs = am.runningAppProcesses ?: emptyList()
            for (p in procs) {
                if (p.uid == Process.myUid()) continue
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) continue
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE) continue
                am.killBackgroundProcesses(p.processName)
                killed++
            }
        } catch (t: Throwable) {
        }
        return killed
    }

    /** Root-assisted full trim of every app's cache (safe: data is kept). */
    fun trimCachesRoot(): String? {
        if (!RootShell.isRooted) return null
        val res = RootShell.runRoot("pm trim-caches 999999999", timeoutMs = 60_000)
        return if (res.first == 0) res.second.trim() else null
    }

    /** Root-assisted clear of a single app's cache directory. */
    fun clearAppCacheRoot(packageName: String): Boolean {
        if (!RootShell.isRooted) return false
        val cmd = "pm clear $packageName"
        val res = RootShell.runRoot(cmd, timeoutMs = 30_000)
        return res.first == 0
    }

    /** Root-assisted force stop of any package. */
    fun forceStopRoot(packageName: String): Boolean {
        if (!RootShell.isRooted) return false
        val res = RootShell.runRoot("am force-stop $packageName", timeoutMs = 15_000)
        return res.first == 0
    }

    fun installedApps(context: Context): List<ApplicationInfo> = try {
        context.packageManager.getInstalledApplications(0)
    } catch (t: Throwable) {
        emptyList()
    }

    fun userApps(context: Context): List<ApplicationInfo> = installedApps(context).filter {
        (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.uid != Process.myUid()
    }

    fun labelOf(context: Context, pkg: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        pkg
    }

    fun iconOf(context: Context, pkg: String): android.graphics.drawable.Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (t: Throwable) {
        try {
            context.packageManager.getDefaultActivityIcon()
        } catch (t2: Throwable) {
            null
        }
    }

    fun launchApp(context: Context, pkg: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
        }
    }

    fun openAppInfo(context: Context, pkg: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
        }
    }

    fun uninstallApp(context: Context, pkg: String) {
        try {
            val intent = Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
        }
    }

    /** Heuristic game detection — the package prefixes real games ship with. */
    private val gamePrefixes = listOf(
        "com.tencent.", "com.ea.", "com.activision.", "com.garena.", "com.riotgames.",
        "com.ubisoft.", "com.blizzard.", "com.krafton.", "com.pubg.", "com.playrix.",
        "com.supercell.", "com.mojang.", "com.gameloft.", "com.madfingergames.",
        "com.netease.", "com.miHoYo.", "com.hypergryph.", "com.proximabeta.",
        "com.pearlabyss.", "com.dts.", "com.zhiguoyuan.", "com.wandoujia.",
        "com.sdo.sg.", "com.zhimi.", "com.dragonest.", "com.vng.", "com.gametaiwan",
        "com.brawlstar", "com.miniclip.", "com.kiloo.", "com.halfbrick.",
        "com.Rovio.", "com.ea.gp.fifamobile", "com.gameloft.anmp", "jp.co.mi",
        "com.sega.sonic", "com.netease.gb", "com.netease.idv", "com.lg.hive",
        "com.gametaiwan.jk", "com.luna.game", "com.moonton", "com.voodoo",
        "com.peak.deckbuilders", "com.riot", "com.nintendo"
    )

    fun isGamePackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (gamePrefixes.any { p.startsWith(it.lowercase()) }) return true
        return false
    }
}
