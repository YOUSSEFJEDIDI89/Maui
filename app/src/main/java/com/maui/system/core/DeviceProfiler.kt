package com.maui.system.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.PowerManager
import java.io.File

/** A single immutable snapshot of the device's hardware + storage state. */
data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abi: String,
    val cpuModel: String,
    val cpuCores: Int,
    val totalRam: Long,
    val availableRam: Long,
    val lowRamDevice: Boolean,
    val internalTotal: Long,
    val internalUsed: Long,
    val internalFree: Long,
    val externalTotal: Long,
    val externalUsed: Long,
    val externalFree: Long,
    val batteryLevel: Int,
    val batteryTempC: Float,
    val charging: Boolean,
    val thermalStatus: Int
) {
    val internalUsedPercent: Int get() = com.maui.system.util.Format.percent(internalUsed, internalTotal)
    val ramUsed: Long get() = (totalRam - availableRam).coerceAtLeast(0)
    val ramUsedPercent: Int get() = com.maui.system.util.Format.percent(ramUsed, totalRam)
    val displayName: String get() = "$manufacturer $model"
}

object DeviceProfiler {

    fun read(context: Context): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val internal = storageOf(Environment.getDataDirectory().path)
        val externalPath = Environment.getExternalStorageDirectory()
        val external = if (externalPath != null) storageOf(externalPath.path) else null

        val battery = batterySnapshot(context)

        return DeviceProfile(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            androidVersion = Build.VERSION.RELEASE ?: "?",
            sdkInt = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown",
            cpuModel = readCpuModel(),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            totalRam = mi.totalMem,
            availableRam = mi.availMem,
            lowRamDevice = mi.lowMemory,
            internalTotal = internal.first,
            internalUsed = internal.second,
            internalFree = internal.third,
            externalTotal = external?.first ?: 0L,
            externalUsed = external?.second ?: 0L,
            externalFree = external?.third ?: 0L,
            batteryLevel = battery.first,
            batteryTempC = battery.second,
            charging = battery.third,
            thermalStatus = thermalStatus(context)
        )
    }

    /** total, used, free */
    fun storageOf(path: String): Triple<Long, Long, Long> = try {
        val stat = StatFs(path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        Triple(total, (total - free).coerceAtLeast(0), free)
    } catch (t: Throwable) {
        Triple(0L, 0L, 0L)
    }

    fun batterySnapshot(context: Context): Triple<Int, Float, Boolean> {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        var pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        if (pct < 0) {
            // Fallback: the framework property works even when the sticky
            // broadcast is unavailable (some ROMs, and headless environments).
            pct = try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (cap in 0..100) cap else -1
            } catch (t: Throwable) {
                -1
            }
        }
        return Triple(pct, temp, charging)
    }

    fun thermalStatus(context: Context): Int = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus else 0
    } catch (t: Throwable) {
        0
    }

    fun readCpuModel(): String {
        val info = RootShell.readFile("/proc/cpuinfo") ?: return "Unknown"
        val line = info.lines().firstOrNull { it.contains("model name", true) || it.contains("Hardware", true) }
        return line?.substringAfter(":")?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown"
    }

    /** Available CPU governors, when the kernel exposes them. */
    fun availableGovernors(): List<String> {
        val raw = RootShell.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
        return raw?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun currentGovernor(): String? =
        RootShell.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")?.trim()

    fun packageCount(context: Context): Int = try {
        context.packageManager.getInstalledApplications(0).size
    } catch (t: Throwable) {
        0
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
