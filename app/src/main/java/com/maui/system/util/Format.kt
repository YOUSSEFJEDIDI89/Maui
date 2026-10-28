package com.maui.system.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return if (i <= 1) String.format(Locale.US, "%.0f %s", value, units[i])
        else String.format(Locale.US, "%.2f %s", value, units[i])
    }

    fun percent(part: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((part.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    fun duration(millis: Long): String {
        if (millis < 1000) return "${millis}ms"
        val s = TimeUnit.MILLISECONDS.toSeconds(millis)
        if (s < 60) return "${s}s"
        val m = s / 60
        val rs = s % 60
        if (m < 60) return "${m}m ${rs}s"
        val h = m / 60
        return "${h}h ${m % 60}m"
    }

    fun date(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    fun speed(bytesPerSecond: Long): String = size(bytesPerSecond) + "/s"
}
