package com.fabiantorrestech.visualtimerplus.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Long.formatClockTime(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutesWithinHour = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val totalMinutes = totalSeconds / 60L

    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutesWithinHour, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", totalMinutes, seconds)
    }
}

fun formatWallClockEndTime(endTimeMillis: Long, showSeconds: Boolean = false): String {
    val pattern = if (showSeconds) "h:mm:ss a" else "h:mm a"
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return Instant.ofEpochMilli(endTimeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}

fun formatEndTimeFromNow(
    durationMillis: Long,
    showSeconds: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
): String = formatWallClockEndTime(nowMillis + durationMillis, showSeconds)

// Floors remaining time to the nearest 30-second boundary for display while running.
// Below 30 s, shows exact seconds. Returns a string prefixed with "~".
fun Long.formatApproxTime(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    return if (totalSeconds >= 30L) {
        val flooredSeconds = (totalSeconds / 30L) * 30L
        val mins = flooredSeconds / 60L
        val secs = flooredSeconds % 60L
        "~%02d:%02d".format(mins, secs)
    } else {
        "%02d:%02d".format(0L, totalSeconds)
    }
}
