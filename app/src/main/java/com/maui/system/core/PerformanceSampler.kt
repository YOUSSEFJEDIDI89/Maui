package com.maui.system.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.view.Choreographer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Live performance sampler. Every number it produces comes from the OS:
 *  - RAM        : ActivityManager.MemoryInfo
 *  - CPU load   : /proc/stat jiffy diff
 *  - CPU temp   : sysfs thermal zones (fallback: battery temperature)
 *  - Network    : TrafficStats byte counters
 *  - FPS        : Choreographer (this app) / SurfaceFlinger (root, any app)
 */
data class PerfSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val ramTotal: Long = 0,
    val ramUsed: Long = 0,
    val ramAvailable: Long = 0,
    val ramPercent: Int = 0,
    val cpuPercent: Int = 0,
    val cpuTempC: Float = 0f,
    val cpuGovernor: String = "",
    val batteryPercent: Int = 0,
    val batteryTempC: Float = 0f,
    val charging: Boolean = false,
    val netDownBps: Long = 0,
    val netUpBps: Long = 0,
    val networkType: String = "Offline",
    val fps: Int = 0,
    val fpsSource: String = "",
    val lowMemory: Boolean = false
)

class PerformanceSampler(private val context: Context) {

    private var lastCpu: Pair<Long, Long>? = null
    private var lastNetRx = -1L
    private var lastNetTx = -1L
    private var lastNetTime = 0L
    private var lastChoreoFrame = 0L
    private var frameCount = 0

    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            lastChoreoFrame = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }

    @Volatile
    private var sampling = false

    fun start() {
        if (sampling) return
        sampling = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        if (!sampling) return
        sampling = false
        choreographer.removeFrameCallback(frameCallback)
    }

    suspend fun sample(): PerfSnapshot = withContext(Dispatchers.Default) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val cpu = readCpuPercent()
        val battery = DeviceProfiler.batterySnapshot(context)
        val net = readNetwork()
        val fps = readFps()

        val ramTotal = mi.totalMem
        val ramUsed = (ramTotal - mi.availMem).coerceAtLeast(0)

        PerfSnapshot(
            ramTotal = ramTotal,
            ramUsed = ramUsed,
            ramAvailable = mi.availMem,
            ramPercent = com.maui.system.util.Format.percent(ramUsed, ramTotal),
            cpuPercent = cpu,
            cpuTempC = readCpuTemp(),
            cpuGovernor = DeviceProfiler.currentGovernor() ?: "",
            batteryPercent = battery.first,
            batteryTempC = battery.second,
            charging = battery.third,
            netDownBps = net.first,
            netUpBps = net.second,
            networkType = net.third,
            fps = fps.first,
            fpsSource = fps.second,
            lowMemory = mi.lowMemory
        )
    }

    /** Percentage of CPU time spent outside idle, from /proc/stat. */
    private fun readCpuPercent(): Int {
        val raw = try {
            File("/proc/stat").readText().lines().firstOrNull { it.startsWith("cpu ") }
        } catch (t: Throwable) {
            null
        } ?: return 0
        val parts = raw.trim().split(Regex("\\s+")).drop(1)
        if (parts.size < 4) return 0
        var idle = 0L
        var total = 0L
        parts.forEachIndexed { index, p ->
            val v = p.toLongOrNull() ?: 0L
            total += v
            if (index == 3 || index == 4) idle += v // idle + iowait
        }
        val prev = lastCpu
        lastCpu = total to idle
        if (prev == null) return 0
        val dTotal = total - prev.first
        val dIdle = idle - prev.second
        if (dTotal <= 0) return 0
        return (((dTotal - dIdle) * 100.0) / dTotal).toInt().coerceIn(0, 100)
    }

    /** CPU package temperature from sysfs, else battery temperature. */
    fun readCpuTemp(): Float {
        val zones = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/power_supply/battery/temp"
        )
        for (z in zones) {
            val v = RootShell.readFile(z)?.trim()?.toDoubleOrNull() ?: continue
            // sysfs reports millidegrees
            val c = if (v > 1000) (v / 1000.0).toFloat() else v.toFloat()
            if (c in -20f..150f) return c
        }
        return 0f
    }

    private fun readNetwork(): Triple<Long, Long, String> {
        val rx = try { TrafficStats.getTotalRxBytes() } catch (t: Throwable) { -1L }
        val tx = try { TrafficStats.getTotalTxBytes() } catch (t: Throwable) { -1L }
        val now = System.currentTimeMillis()
        var down = 0L
        var up = 0L
        if (rx >= 0 && tx >= 0 && lastNetRx >= 0) {
            val dt = now - lastNetTime
            if (dt > 0) {
                down = ((rx - lastNetRx) * 1000 / dt).coerceAtLeast(0)
                up = ((tx - lastNetTx) * 1000 / dt).coerceAtLeast(0)
            }
        }
        lastNetRx = rx
        lastNetTx = tx
        lastNetTime = now
        return Triple(down, up, networkType())
    }

    fun networkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(net) ?: return "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Online"
            }
        } catch (t: Throwable) {
            "Offline"
        }
    }

    /**
     * FPS. Inside Maui we can measure our own frame rate precisely; for other
     * apps SurfaceFlinger is the only accurate source and it needs root.
     */
    private fun readFps(): Pair<Int, String> {
        val root = RootShell.isRooted
        if (root) {
            val sf = runSfLatency()
            if (sf > 0) return sf to "SurfaceFlinger"
        }
        val since = lastChoreoFrame
        if (since == 0L) return 0 to ""
        val elapsed = System.nanoTime() - since
        if (elapsed <= 0) return 0 to ""
        // rough: frames counted in the last window
        val fps = (frameCount * 1_000_000_000L / elapsed).toInt()
        return fps.coerceAtMost(120) to "Choreographer"
    }

    /** Real frame rate of the foreground surface — root only. */
    fun runSfLatency(): Int {
        val res = RootShell.runRoot("dumpsys SurfaceFlinger --latency", timeoutMs = 8000)
        if (res.first != 0) return 0
        val lines = res.second.lines()
        if (lines.size < 2) return 0
        // first line: refresh period in ns; then a block of timestamps
        val refresh = lines[0].trim().toLongOrNull() ?: return 0
        if (refresh <= 0) return 0
        val stamps = lines.drop(1).mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }
        if (stamps.size < 5) return 0
        val window = stamps.takeLast(60)
        val span = window.last() - window.first()
        if (span <= 0) return 0
        val frames = window.size - 1
        return ((frames.toLong() * 1_000_000_000L) / span).toInt().coerceIn(1, 240)
    }

    fun governorOptions(): List<String> = DeviceProfiler.availableGovernors()

    fun isLowRam(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.lowMemory
    }

    suspend fun waitSeconds(s: Long) = delay(s * 1000)

    val deviceApi: Int get() = Build.VERSION.SDK_INT
}
