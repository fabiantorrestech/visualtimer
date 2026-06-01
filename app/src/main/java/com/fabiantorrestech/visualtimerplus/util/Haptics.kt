package com.fabiantorrestech.visualtimerplus.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    private val timerFinishedEffect = VibrationEffect.createWaveform(
        longArrayOf(0L, 350L, 200L, 350L, 400L),
        0,
    )

    fun startTimerFinishedVibration(context: Context) {
        val vibrator = context.findVibrator() ?: return

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                timerFinishedEffect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } else {
            vibrator.vibrate(timerFinishedEffect)
        }
    }

    fun stopTimerFinishedVibration(context: Context) {
        context.findVibrator()?.cancel()
    }

    private fun Context.findVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
