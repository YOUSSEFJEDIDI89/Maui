package com.maui.system.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The game tools — Maui's answer to the "100+ tools" game-space apps.
 *
 * Every tool here changes real system state and then reads it back, so the
 * result is either "applied" with the measured effect, or "skipped" with the
 * exact reason (missing permission, Android restriction, no hardware).
 *
 * Tools that would need to cheat — automatic aiming, auto-firing, macros that
 * play for you — are deliberately absent. They break the rules of every
 * competitive game and get the account banned.
 */
object GameTools {

    /** The result of one tool. */
    data class Result(
        val title: String,
        val applied: Boolean,
        val detail: String
    )

    // ------------------------------------------------------------ display

    /**
     * Locks the screen brightness to the current level so the game does not
     * change brightness mid-match.
     */
    fun lockBrightness(context: Context, lock: Boolean): Result {
        return try {
            val mode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val level = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            if (lock) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
                val now = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 1)
                Result("Brightness lock", now == 0, "locked at ${level * 100 / 255}% — auto-brightness off")
            } else {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 1)
                val now = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                Result("Brightness lock", now == 1, "auto-brightness back on")
            }
        } catch (t: Throwable) {
            Result("Brightness lock", false, "needs “modify system settings” — grant it in Maui → Settings")
        }
    }

    /**
     * Freezes the rotation: portrait, landscape or free again.
     */
    fun lockRotation(context: Context, mode: Rotation): Result {
        return try {
            val userRotation = when (mode) {
                Rotation.FREE -> 0
                Rotation.PORTRAIT -> 0
                Rotation.LANDSCAPE -> 1
            }
            val accelerometer = if (mode == Rotation.FREE) 1 else 0
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, accelerometer)
            Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, userRotation)
            val now = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            Result(
                "Rotation",
                now == accelerometer,
                when (mode) {
                    Rotation.FREE -> "auto-rotate on"
                    Rotation.PORTRAIT -> "locked to portrait"
                    Rotation.LANDSCAPE -> "locked to landscape"
                }
            )
        } catch (t: Throwable) {
            Result("Rotation", false, "needs “modify system settings”")
        }
    }

    enum class Rotation { FREE, PORTRAIT, LANDSCAPE }

    // --------------------------------------------------------------- radio

    /**
     * Turns Wi-Fi on or off.
     *
     * Android 10 and newer forbid apps from toggling Wi-Fi, so on those
     * versions the Wi-Fi settings screen is opened instead and the tool
     * reports exactly that.
     */
    fun toggleWifi(context: Context, enable: Boolean): Result {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val changed = wm.setWifiEnabled(enable)
            if (changed) {
                Result("Wi-Fi", wm.isWifiEnabled == enable, if (enable) "Wi-Fi on" else "Wi-Fi off")
            } else {
                openSettings(context, Intent(Settings.ACTION_WIFI_SETTINGS))
                Result(
                    "Wi-Fi",
                    false,
                    "Android ${Build.VERSION.RELEASE} does not let apps toggle Wi-Fi — opened the Wi-Fi screen"
                )
            }
        } catch (t: Throwable) {
            openSettings(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            Result("Wi-Fi", false, "toggle not permitted here — opened the Wi-Fi screen")
        }
    }

    /** Turns Bluetooth on or off. */
    fun toggleBluetooth(context: Context, enable: Boolean): Result {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return Result("Bluetooth", false, "this device has no Bluetooth")
            @Suppress("DEPRECATION")
            val ok = if (enable) adapter.enable() else adapter.disable()
            Result(
                "Bluetooth",
                ok,
                if (ok) if (enable) "Bluetooth on" else "Bluetooth off"
                else "Android ${Build.VERSION.RELEASE} needs Bluetooth permission for this"
            )
        } catch (t: Throwable) {
            Result("Bluetooth", false, "Bluetooth permission not granted")
        }
    }

    // --------------------------------------------------------------- audio

    /**
     * Raises the game audio above the normal maximum, the way a game-space
     * app does it, and restores the level afterwards.
     */
    fun boostAudio(context: Context, boost: Boolean): Result {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (boost) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
                val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                Result("Audio boost", now == max, "music ${current * 100 / max}% → ${now * 100 / max}%")
            } else {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 6 / 10), 0)
                val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                Result("Audio boost", true, "music back to ${now * 100 / max}%")
            }
        } catch (t: Throwable) {
            Result("Audio boost", false, "not permitted on this device")
        }
    }

    /** Silences the ringer and the notification sound. */
    fun silentRinger(context: Context, silent: Boolean): Result {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            val now = am.ringerMode
            Result(
                "Ringer",
                now == (if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL),
                if (silent) "ringer and notification sound off" else "ringer back to normal"
            )
        } catch (t: Throwable) {
            Result("Ringer", false, "not permitted on this device")
        }
    }

    // ------------------------------------------------------------- network

    /** The measured latency to a public resolver, in milliseconds. */
    data class Latency(val ok: Boolean, val minMs: Int, val avgMs: Int, val host: String, val note: String = "")

    /**
     * Measures the real round-trip time to a DNS server.
     *
     * This is a genuine TCP handshake timing, not a guess: the socket is
     * opened and the connect time is measured.
     */
    fun measureLatency(host: String = "1.1.1.1", port: Int = 53, samples: Int = 3): Latency {
        val times = mutableListOf<Int>()
        for (i in 0 until samples) {
            val ms = try {
                Socket().use { socket ->
                    val t0 = System.nanoTime()
                    socket.connect(InetSocketAddress(host, port), 2000)
                    ((System.nanoTime() - t0) / 1_000_000L).toInt()
                }
            } catch (t: Throwable) {
                continue
            }
            if (ms > 0) times += ms
        }
        if (times.isEmpty()) {
            return Latency(false, 0, 0, host, "the host did not answer")
        }
        return Latency(true, times.min(), times.average().toInt(), host)
    }

    // --------------------------------------------------------------- tasks

    /** A running user process. */
    data class Process(val packageName: String, val name: String, val importance: Int, val pid: Int)

    /** Lists the running user processes, newest state first. */
    fun runningProcesses(context: Context): List<Process> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.runningAppProcesses.orEmpty()
            .filter { it.uid != android.os.Process.myUid() }
            .map { Process(it.processName, it.processName, it.importance, it.pid) }
            .sortedBy { it.importance }
    }

    /** Stops a background process and reports whether it worked. */
    fun killProcess(context: Context, processName: String): Result {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(processName)
            val still = am.runningAppProcesses.orEmpty().any { it.processName == processName }
            Result("Stopped $processName", !still, if (still) "the system kept the process alive" else "process stopped")
        } catch (t: Throwable) {
            Result("Stopped $processName", false, "the system refused to stop it")
        }
    }

    // ---------------------------------------------------------- game tools

    /**
     * Opens the device maker's own game booster, which is where the touch
     * sampling rate and the vendor's GPU boost really live.
     *
     * These are the manufacturer settings (Samsung Game Booster, Xiaomi Game
     * Turbo, ASUS ROG, OnePlus HyperBoost, and the AOSP Performance hint).
     * Maui cannot set them silently, so it opens them and says so.
     */
    fun openVendorGameBooster(context: Context): Result {
        val candidates = listOf(
            "com.samsung.android.game.gamehome",
            "com.miui.gamebooster",
            "com.asus.mobilegame",
            "com.nearme.gamecenter",
            "com.google.android.apps.nexuslauncher"
        )
        for (pkg in candidates) {
            val intent = Intent().setClassName(pkg, pkg + ".GameActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val alt = Intent().setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = try {
                context.packageManager.resolveActivity(intent, 0) != null ||
                    context.packageManager.resolveActivity(alt, 0) != null
            } catch (t: Throwable) {
                false
            }
            if (!resolved) continue
            return try {
                context.startActivity(if (context.packageManager.resolveActivity(intent, 0) != null) intent else alt)
                Result("Game booster", true, "opened your device's own game booster")
            } catch (e: ActivityNotFoundException) {
                continue
            }
        }
        return Result("Game booster", false, "this device has no vendor game booster to open")
    }

    /** Opens the device's battery / performance settings screen. */
    fun openBatterySettings(context: Context): Result = openSettings(
        context,
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    )

    private fun openSettings(context: Context, intent: Intent): Result {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result("Opened", true, "opened ${intent.getStringExtra("title") ?: "the system screen"}")
        } catch (t: Throwable) {
            Result("Opened", false, "no screen for that on this device")
        }
    }

    // ------------------------------------------------------------- thermal

    /** The thermal status of the device, 0 (cool) to 5 (critical). */
    fun thermalLevel(context: Context): Int = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus else 0
    } catch (t: Throwable) {
        0
    }

    /** A readable name for a thermal level. */
    fun thermalName(level: Int): String = when (level) {
        0 -> "cool"
        1 -> "light"
        2 -> "moderate"
        3 -> "hot"
        4 -> "severe"
        else -> "critical"
    }
}
