package com.fabiantorrestech.visualtimerplus.util

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
