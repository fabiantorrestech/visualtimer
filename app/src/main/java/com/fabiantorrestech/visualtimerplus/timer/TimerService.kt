package com.fabiantorrestech.visualtimerplus.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: TimerNotificationManager
    private lateinit var db: AppDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var finishTone: Ringtone? = null
    private var hasFinishedAlerted = false
    private var savedStreamVolume: Int = -1
    private var savedStreamType: Int = -1
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
        db = AppDatabase.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_RESET -> resetTimer()
            ACTION_DISMISS_FINISHED -> dismissFinished()
            ACTION_RESTART -> restartTimer()
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
                originalDurationMillis = durationMillis,
            )
        }
        promoteToForeground()
        scheduleTick()
    }

    private fun restartTimer() {
        handler.removeCallbacksAndMessages(null)
        val current = TimerRepository.getState()
        val originalDuration = current.originalDurationMillis
        if (originalDuration <= 0L) return

        val targetEndTime = System.currentTimeMillis() + originalDuration
        hasFinishedAlerted = false
        stopFinishedAlertEffects()
        TimerRepository.update { state ->
            state.copy(
                status = TimerStatus.Running,
                selectedDurationMillis = originalDuration,
                remainingMillis = originalDuration,
                targetEndTimeMillis = targetEndTime,
                pausedRemainingMillis = null,
                originalDurationMillis = originalDuration,
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
        completeLogEntry()
        val resetDuration = TimerRepository.getState().defaultDurationMillis.takeIf { it > 0L } ?: 0L
        TimerRepository.update { state ->
            state.copy(
                status = TimerStatus.Idle,
                selectedDurationMillis = resetDuration,
                remainingMillis = resetDuration,
                targetEndTimeMillis = null,
                pausedRemainingMillis = null,
                originalDurationMillis = 0L,
                activeTimerName = "",
                activePresetId = null,
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
        completeLogEntry()
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
        if (!TimerRepository.isAppForeground) {
            promoteToForeground()
        }
    }

    private fun completeLogEntry() {
        val logId = TimerRepository.activeLogEntryId
        if (logId < 0L) return
        TimerRepository.activeLogEntryId = -1L
        val state = TimerRepository.getState()
        val adjustedDuration = if (
            state.originalDurationMillis > 0L &&
            state.selectedDurationMillis != state.originalDurationMillis
        ) state.selectedDurationMillis else null
        serviceScope.launch {
            db.appDao().completeLogEntry(logId, System.currentTimeMillis(), adjustedDuration)
        }
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
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val streamType = when (state.finishedSoundRoute) {
                FinishedSoundRoute.Alarm -> AudioManager.STREAM_ALARM
                FinishedSoundRoute.Media -> AudioManager.STREAM_MUSIC
                else -> AudioManager.STREAM_NOTIFICATION
            }
            if (audioManager != null) {
                val maxVol = audioManager.getStreamMaxVolume(streamType)
                val rawVol = (maxVol * state.finishedSoundVolumePercent / 100f).toInt()
                // overrideMutedSystemVolume ensures volume is ≥1 even when system is muted
                val targetVol = if (state.overrideMutedSystemVolume) rawVol.coerceAtLeast(1) else rawVol
                savedStreamVolume = audioManager.getStreamVolume(streamType)
                savedStreamType = streamType
                audioManager.setStreamVolume(streamType, targetVol, 0)
            }
            val soundUri = when (state.finishedSoundRoute) {
                FinishedSoundRoute.Alarm -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
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
        if (savedStreamVolume >= 0 && savedStreamType >= 0) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.setStreamVolume(savedStreamType, savedStreamVolume, 0)
            savedStreamVolume = -1
            savedStreamType = -1
        }
    }

    companion object {
        const val ACTION_START = "com.fabiantorrestech.visualtimerplus.action.START"
        const val ACTION_PAUSE = "com.fabiantorrestech.visualtimerplus.action.PAUSE"
        const val ACTION_RESUME = "com.fabiantorrestech.visualtimerplus.action.RESUME"
        const val ACTION_RESET = "com.fabiantorrestech.visualtimerplus.action.RESET"
        const val ACTION_DISMISS_FINISHED = "com.fabiantorrestech.visualtimerplus.action.DISMISS_FINISHED"
        const val ACTION_RESTART = "com.fabiantorrestech.visualtimerplus.action.RESTART"

        private const val TICK_INTERVAL_MILLIS = 250L
    }
}
