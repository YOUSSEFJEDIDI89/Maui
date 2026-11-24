package com.maui.system.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One gaming session, tracked with real numbers.
 *
 * A session starts when Maui launches a game and ends when the game leaves
 * the foreground. Everything stored here was measured during the session:
 * the play time, the average frame rate, the jank share, the hottest
 * temperature and how much battery the game cost.
 */
data class GameSession(
    val packageName: String = "",
    val gameName: String = "",
    val startedAt: Long = 0L,
    var endedAt: Long = 0L,
    var framesSampled: Int = 0,
    var avgFps: Int = 0,
    var peakFps: Int = 0,
    var jankPercent: Float = 0f,
    var peakTempC: Float = 0f,
    val batteryStart: Int = 0,
    var batteryEnd: Int = 0,
    var ramFreed: Long = 0L,
    val profile: String = ""
) {
    /** The session length in seconds. */
    val seconds: Long get() = ((endedAt - startedAt) / 1000L).coerceAtLeast(0L)

    /** The battery the session used, in percent (never negative). */
    val batteryUsed: Int get() = (batteryStart - batteryEnd).coerceAtLeast(0)

    /** A one-line summary for the history list. */
    fun summary(): String {
        val fps = if (avgFps > 0) "$avgFps fps" else "no frame data"
        return "${seconds / 60}m ${seconds % 60}s • $fps • ${"%.1f".format(jankPercent)}% jank"
    }
}

/**
 * Keeps the running session and the history of the finished ones.
 *
 * The history is small and local — a JSON array in the app's own
 * preferences. No account, no server, no tracking.
 */
class GameSessionStore private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: GameSessionStore? = null

        /** The shared store — the launcher and the overlay service share it. */
        fun get(context: Context): GameSessionStore = instance ?: synchronized(this) {
            instance ?: GameSessionStore(context.applicationContext).also { instance = it }
        }
    }

    private val sp = context.applicationContext.getSharedPreferences("maui_sessions", Context.MODE_PRIVATE)

    private var current: GameSession? = null

    /** The session that is running right now, if any. */
    fun current(): GameSession? = current

    /** Starts a new session for [packageName]. */
    fun start(packageName: String, gameName: String, profile: String, batteryStart: Int) {
        current = GameSession(
            packageName = packageName,
            gameName = gameName,
            startedAt = System.currentTimeMillis(),
            batteryStart = batteryStart,
            profile = profile
        )
    }

    /**
     * Folds a measurement into the running session.
     *
     * The running average is computed from the real sample count, and the
     * peak temperature is the hottest value that was actually seen.
     */
    fun sample(fps: Int, jankPercent: Float, tempC: Float) {
        val s = current ?: return
        if (fps > 0) {
            val n = s.framesSampled
            s.avgFps = if (n == 0) fps else (s.avgFps * n + fps) / (n + 1)
            s.peakFps = maxOf(s.peakFps, fps)
            s.framesSampled = n + 1
        }
        if (jankPercent > 0f) s.jankPercent = maxOf(s.jankPercent, jankPercent)
        if (tempC > s.peakTempC) s.peakTempC = tempC
    }

    /** Ends the running session and stores it in the history. */
    fun finish(batteryEnd: Int, ramFreed: Long): GameSession? {
        val s = current ?: return null
        s.endedAt = System.currentTimeMillis()
        s.batteryEnd = batteryEnd
        s.ramFreed = ramFreed
        current = null
        val history = history().toMutableList()
        history.add(0, s)
        save(history.take(50))
        return s
    }

    /** The stored history, newest first. */
    fun history(): List<GameSession> {
        val raw = sp.getString("history", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GameSession(
                    packageName = o.optString("pkg"),
                    gameName = o.optString("name"),
                    startedAt = o.optLong("start"),
                    endedAt = o.optLong("end"),
                    framesSampled = o.optInt("samples"),
                    avgFps = o.optInt("fps"),
                    peakFps = o.optInt("peak"),
                    jankPercent = o.optDouble("jank", 0.0).toFloat(),
                    peakTempC = o.optDouble("temp", 0.0).toFloat(),
                    batteryStart = o.optInt("b0"),
                    batteryEnd = o.optInt("b1"),
                    ramFreed = o.optLong("ram"),
                    profile = o.optString("profile")
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Total play time across the whole history. */
    fun totalPlaySeconds(): Long = history().sumOf { it.seconds }

    /** The average frame rate across every session that produced one. */
    fun averageFps(): Int {
        val withFps = history().filter { it.avgFps > 0 }
        if (withFps.isEmpty()) return 0
        return withFps.sumOf { it.avgFps } / withFps.size
    }

    private fun save(list: List<GameSession>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(JSONObject().apply {
                put("pkg", s.packageName)
                put("name", s.gameName)
                put("start", s.startedAt)
                put("end", s.endedAt)
                put("samples", s.framesSampled)
                put("fps", s.avgFps)
                put("peak", s.peakFps)
                put("jank", s.jankPercent.toDouble())
                put("temp", s.peakTempC.toDouble())
                put("b0", s.batteryStart)
                put("b1", s.batteryEnd)
                put("ram", s.ramFreed)
                put("profile", s.profile)
            })
        }
        sp.edit().putString("history", arr.toString()).apply()
    }
}
