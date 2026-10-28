package com.maui.system.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Shell access layer.
 *
 * Maui works in two tiers:
 *  - Tier 1 (no root): everything reachable through public Android APIs.
 *  - Tier 2 (root):   extra power — real cache trimming via `pm trim-caches`,
 *                     killing background processes, and true FPS sampling
 *                     through SurfaceFlinger.
 *
 * Root is detected once and cached; every command has a hard timeout so a
 * hanging `su` prompt can never freeze the UI.
 */
object RootShell {

    @Volatile
    private var rootAvailable: Boolean? = null

    val isRooted: Boolean
        get() {
            rootAvailable?.let { return it }
            val detected = detect()
            rootAvailable = detected
            return detected
        }

    private fun detect(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/system/sbin/su", "/vendor/bin/su", "/magisk/.core/bin/su"
        )
        for (p in paths) {
            if (java.io.File(p).exists() && java.io.File(p).canExecute()) {
                // A real root shell must answer `id` as uid=0
                val res = runRoot("id", timeoutMs = 6000)
                if (res.first == 0 && res.second.contains("uid=0")) return true
            }
        }
        return false
    }

    data class Result(val exitCode: Int, val output: String, val error: String = "")

    /** Runs a command as root. Returns exit code + combined output. */
    fun runRoot(command: String, timeoutMs: Long = 15_000): Pair<Int, String> {
        return try {
            val pb = ProcessBuilder("su", "-c", command)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroy()
                -1 to "timeout"
            } else {
                p.exitValue() to out
            }
        } catch (t: Throwable) {
            -1 to (t.message ?: "no root")
        }
    }

    /** Runs a command in the app's own shell (no special permissions). */
    fun runShell(command: String, timeoutMs: Long = 10_000): Pair<Int, String> {
        return try {
            val pb = ProcessBuilder("sh", "-c", command)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroy()
                -1 to "timeout"
            } else {
                p.exitValue() to out
            }
        } catch (t: Throwable) {
            -1 to (t.message ?: "shell failed")
        }
    }

    /** Reads a system file, returning null when it is not readable. */
    fun readFile(path: String): String? = try {
        val f = java.io.File(path)
        if (f.exists() && f.canRead()) BufferedReader(InputStreamReader(f.inputStream())).use { it.readText() } else null
    } catch (t: Throwable) {
        null
    }
}
