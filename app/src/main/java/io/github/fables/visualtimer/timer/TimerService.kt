package com.fabiantorrestech.visualtimerplus.timer

import android.app.Service
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.util.Haptics

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: TimerNotificationManager
    private var finishTone: Ringtone? = null
    private var hasFinishedAlerted = false
    private val stopFinishedVibrationRunnable = Runnable {
        Haptics.stopTimerFinishedVibration(applicationContext)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val current = TimerRepository.getState()
            val targetEndTime = current.targetEndTimeMillis ?: return
            val remainingMillis = clampDuration(targetEndTime - System.currentTimeMillis())
            if (remainingMillis <= 0L) {
                finishTimer()
                return
            }

            TimerRepository.update { state ->
                state.copy(
                    status = TimerStatus.Running,
                    remainingMillis = remainingMillis,
                    pausedRemainingMillis = null,
                )
            }
            notificationManager.updateNotification(TimerRepository.getState())
            handler.postDelayed(this, TICK_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        TimerRepository.initialize(applicationContext)
        notificationManager = TimerNotificationManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_RESET -> resetTimer()
            ACTION_DISMISS_FINISHED -> dismissFinished()
            else -> {
                when (TimerRepository.getState().status) {
                    TimerStatus.Running -> {
                        promoteToForeground()
                        scheduleTick()
                    }

                    TimerStatus.Paused,
                    TimerStatus.Finished,
                    -> promoteToForeground()

                    TimerStatus.Idle -> Unit
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopFinishedAlertEffects()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTimer() {
        handler.removeCallbacksAndMessages(null)
        val current = TimerRepository.getState()
        val durationMillis = current.selectedDurationMillis
        if (durationMillis <= 0L) return

        val targetEndTime = System.currentTimeMillis() + durationMillis
        hasFinishedAlerted = false
        stopFinishedAlertEffects()
        TimerRepository.update { state ->
            state.copy(
                status = TimerStatus.Running,
                remainingMillis = durationMillis,
                targetEndTimeMillis = targetEndTime,
                pausedRemainingMillis = null,
            )
        }
        promoteToForeground()
        scheduleTick()
    }

    private fun pauseTimer() {
        val current = TimerRepository.getState()
        if (current.status != TimerStatus.Running) return

        handler.removeCallbacksAndMessages(null)
        val remainingMillis = current.targetEndTimeMillis
            ?.let { clampDuration(it - System.currentTimeMillis()) }
            ?: current.remainingMillis

        TimerRepository.update { state ->
            state.copy(
                status = TimerStatus.Paused,
                remainingMillis = remainingMillis,
                pausedRemainingMillis = remainingMillis,
                targetEndTimeMillis = null,
            )
        }
        promoteToForeground()
    }

    private fun resumeTimer() {
        handler.removeCallbacksAndMessages(null)
        val current = TimerRepository.getState()
        val remainingMillis = current.pausedRemainingMillis ?: current.remainingMillis
        if (current.status != TimerStatus.Paused || remainingMillis <= 0L) return

        hasFinishedAlerted = false
        val targetEndTime = System.currentTimeMillis() + remainingMillis
        TimerRepository.update { state ->
            state.copy(
                status = TimerStatus.Running,
                remainingMillis = remainingMillis,
                targetEndTimeMillis = targetEndTime,
                pausedRemainingMillis = null,
            )
        }
        promoteToForeground()
        scheduleTick()
    }

    private fun resetTimer() {
        handler.removeCallbacksAndMessages(null)
        stopFinishedAlertEffects()
        hasFinishedAlerted = false
        TimerRepository.update { state ->
            state.copy(
                status = TimerStatus.Idle,
                remainingMillis = state.selectedDurationMillis,
                targetEndTimeMillis = null,
                pausedRemainingMillis = null,
            )
        }
        notificationManager.cancelNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun dismissFinished() {
        if (TimerRepository.getState().status != TimerStatus.Finished) return
        resetTimer()
    }

    private fun finishTimer() {
        handler.removeCallbacksAndMessages(null)
        TimerRepository.update { state ->
            state.copy(
                status = TimerStatus.Finished,
                remainingMillis = 0L,
                targetEndTimeMillis = null,
                pausedRemainingMillis = null,
            )
        }
        if (!hasFinishedAlerted) {
            hasFinishedAlerted = true
            alertFinished()
        }
        promoteToForeground()
    }

    private fun promoteToForeground() {
        val notification = notificationManager.buildServiceNotification(TimerRepository.getState())
        ServiceCompat.startForeground(
            this,
            TimerNotificationManager.NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun scheduleTick() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun alertFinished() {
        val state = TimerRepository.getState()
        if (state.soundEnabled) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            finishTone = RingtoneManager.getRingtone(applicationContext, soundUri)?.also { ringtone ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.isLooping = false
                }
                ringtone.play()
            }
        }
        if (state.finishedVibrationMode != FinishedVibrationMode.Off) {
            Haptics.startTimerFinishedVibration(applicationContext)
            state.finishedVibrationMode.durationMillis?.let { durationMillis ->
                handler.postDelayed(stopFinishedVibrationRunnable, durationMillis)
            }
        }
    }

    private fun stopFinishedAlertEffects() {
        handler.removeCallbacks(stopFinishedVibrationRunnable)
        Haptics.stopTimerFinishedVibration(applicationContext)
        finishTone?.stop()
        finishTone = null
    }

    companion object {
        const val ACTION_START = "com.fabiantorrestech.visualtimerplus.action.START"
        const val ACTION_PAUSE = "com.fabiantorrestech.visualtimerplus.action.PAUSE"
        const val ACTION_RESUME = "com.fabiantorrestech.visualtimerplus.action.RESUME"
        const val ACTION_RESET = "com.fabiantorrestech.visualtimerplus.action.RESET"
        const val ACTION_DISMISS_FINISHED = "com.fabiantorrestech.visualtimerplus.action.DISMISS_FINISHED"

        private const val TICK_INTERVAL_MILLIS = 250L
    }
}
