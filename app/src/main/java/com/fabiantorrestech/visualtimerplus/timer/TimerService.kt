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
    private var savedStreamVolume: Int = -1
    private var savedStreamType: Int = -1
    private var lastAlertedTimerIndex: Int = -1
    private var lastNotificationUpdateMs: Long = 0L

    private val replayToneRunnable = object : Runnable {
        override fun run() {
            val tone = finishTone ?: return
            if (!tone.isPlaying) tone.play()
            handler.postDelayed(this, 500L)
        }
    }

    private val stopFinishedVibrationRunnable = Runnable {
        stopFinishedAlertEffects()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val state = TimerRepository.getState()
            val now = System.currentTimeMillis()
            var hasActiveTickingTimer = false

            state.timers.forEachIndexed { idx, timer ->
                when (timer.status) {
                    TimerStatus.Running -> {
                        val targetEndTime = timer.targetEndTimeMillis ?: return@forEachIndexed
                        val remainingMillis = clampDuration(targetEndTime - now)
                        if (remainingMillis <= 0L) {
                            enterOvertime(idx, now)
                        } else {
                            hasActiveTickingTimer = true
                            TimerRepository.updateTimer(idx) { t ->
                                t.copy(
                                    status = TimerStatus.Running,
                                    remainingMillis = remainingMillis,
                                    pausedRemainingMillis = null,
                                )
                            }
                        }
                    }
                    TimerStatus.Overtime -> {
                        hasActiveTickingTimer = true
                        val overtimeStartedAt = timer.overtimeStartedAtMillis ?: now
                        val overtimeMillis = (now - overtimeStartedAt).coerceAtLeast(0L)
                        TimerRepository.updateTimer(idx) { t ->
                            t.copy(
                                status = TimerStatus.Overtime,
                                remainingMillis = overtimeMillis,
                                targetEndTimeMillis = null,
                                pausedRemainingMillis = null,
                            )
                        }
                    }
                    else -> Unit
                }
            }

            val intervalMs = state.notificationUpdateIntervalSeconds * 1000L
            if (now - lastNotificationUpdateMs >= intervalMs) {
                notificationManager.updateNotification(TimerRepository.getState())
                lastNotificationUpdateMs = now
            }
            if (
                hasActiveTickingTimer || TimerRepository.getState().timers.any {
                    it.status == TimerStatus.Running || it.status == TimerStatus.Overtime
                }
            ) {
                handler.postDelayed(this, TICK_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        TimerRepository.initialize(applicationContext)
        notificationManager = TimerNotificationManager(applicationContext)
        db = AppDatabase.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val timerIndex = intent?.getIntExtra(EXTRA_TIMER_INDEX, -1) ?: -1
        val resolvedIndex = if (timerIndex == -1) TimerRepository.getState().activeTimerIndex else timerIndex

        when (intent?.action) {
            ACTION_START -> startTimer(resolvedIndex)
            ACTION_PAUSE -> pauseTimer(resolvedIndex)
            ACTION_RESUME -> resumeTimer(resolvedIndex)
            ACTION_RESET -> resetTimer(resolvedIndex)
            ACTION_DISMISS_FINISHED -> dismissFinished(resolvedIndex)
            ACTION_RESTART -> restartTimer(resolvedIndex)
            ACTION_ADJUST_OVERTIME -> {
                val deltaMillis = intent.getLongExtra(EXTRA_ADJUST_DELTA_MILLIS, 0L)
                addTimeDuringOvertime(resolvedIndex, deltaMillis)
            }
            TimerNotificationManager.ACTION_CYCLE_TIMER -> {
                // Switch the active timer index so the notification updates to show the next timer
                TimerRepository.update { state -> state.copy(activeTimerIndex = resolvedIndex) }
                notificationManager.updateNotification(TimerRepository.getState())
            }
            else -> {
                val state = TimerRepository.getState()
                val anyActive = state.timers.any {
                    it.status == TimerStatus.Running ||
                        it.status == TimerStatus.Paused ||
                        it.status == TimerStatus.Overtime ||
                        it.status == TimerStatus.Finished
                }
                if (anyActive) {
                    promoteToForeground()
                    if (state.timers.any { it.status == TimerStatus.Running || it.status == TimerStatus.Overtime }) {
                        scheduleTick()
                    }
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

    private fun startTimer(index: Int) {
        val timer = TimerRepository.getTimer(index)
        val durationMillis = timer.selectedDurationMillis
        if (durationMillis <= 0L) return

        val targetEndTime = System.currentTimeMillis() + durationMillis
        TimerRepository.updateTimer(index) { t ->
            t.copy(
                status = TimerStatus.Running,
                remainingMillis = durationMillis,
                targetEndTimeMillis = targetEndTime,
                pausedRemainingMillis = null,
                originalDurationMillis = durationMillis,
                totalAdjustmentMillis = 0L,
                timeToDismissAccumulatedMillis = 0L,
                overtimeStartedAtMillis = null,
            )
        }
        promoteToForeground()
        scheduleTick()
    }

    private fun restartTimer(index: Int) {
        val timer = TimerRepository.getTimer(index)
        val originalDuration = timer.originalDurationMillis
        if (originalDuration <= 0L) return

        completeLogEntry(index)
        createLogEntry(index, originalDuration, timer.activeTimerName, timer.activePresetId)

        val targetEndTime = System.currentTimeMillis() + originalDuration
        TimerRepository.updateTimer(index) { t ->
            t.copy(
                status = TimerStatus.Running,
                selectedDurationMillis = originalDuration,
                remainingMillis = originalDuration,
                targetEndTimeMillis = targetEndTime,
                pausedRemainingMillis = null,
                originalDurationMillis = originalDuration,
                totalAdjustmentMillis = 0L,
                timeToDismissAccumulatedMillis = 0L,
                overtimeStartedAtMillis = null,
            )
        }
        promoteToForeground()
        scheduleTick()
    }

    private fun pauseTimer(index: Int) {
        val timer = TimerRepository.getTimer(index)
        if (timer.status != TimerStatus.Running) return

        val remainingMillis = timer.targetEndTimeMillis
            ?.let { clampDuration(it - System.currentTimeMillis()) }
            ?: timer.remainingMillis

        TimerRepository.updateTimer(index) { t ->
            t.copy(
                status = TimerStatus.Paused,
                remainingMillis = remainingMillis,
                pausedRemainingMillis = remainingMillis,
                targetEndTimeMillis = null,
            )
        }

        // If no more timers are running, stop the tick loop but stay in foreground
        if (TimerRepository.getState().timers.none { it.status == TimerStatus.Running || it.status == TimerStatus.Overtime }) {
            handler.removeCallbacks(ticker)
        }
        promoteToForeground()
    }

    private fun resumeTimer(index: Int) {
        val timer = TimerRepository.getTimer(index)
        val remainingMillis = timer.pausedRemainingMillis ?: timer.remainingMillis
        if (timer.status != TimerStatus.Paused || remainingMillis <= 0L) return

        val targetEndTime = System.currentTimeMillis() + remainingMillis
        TimerRepository.updateTimer(index) { t ->
            t.copy(
                status = TimerStatus.Running,
                remainingMillis = remainingMillis,
                targetEndTimeMillis = targetEndTime,
                pausedRemainingMillis = null,
            )
        }
        promoteToForeground()
        scheduleTick()
    }

    private fun resetTimer(index: Int) {
        completeLogEntry(index)
        val timer = TimerRepository.getTimer(index)
        val wasAlerted = lastAlertedTimerIndex == index

        val resetDuration = timer.defaultDurationMillis.takeIf { it > 0L } ?: 0L
        TimerRepository.updateTimer(index) { t ->
            t.copy(
                status = TimerStatus.Idle,
                selectedDurationMillis = resetDuration,
                remainingMillis = resetDuration,
                targetEndTimeMillis = null,
                pausedRemainingMillis = null,
                originalDurationMillis = 0L,
                activeTimerName = "",
                activePresetId = null,
                activeLogEntryId = -1L,
                totalAdjustmentMillis = 0L,
                timeToDismissAccumulatedMillis = 0L,
                overtimeStartedAtMillis = null,
            )
        }

        if (wasAlerted) {
            stopFinishedAlertEffects()
            lastAlertedTimerIndex = -1
        }

        val state = TimerRepository.getState()
        val anyActive = state.timers.any {
            it.status == TimerStatus.Running ||
                it.status == TimerStatus.Paused ||
                it.status == TimerStatus.Overtime ||
                it.status == TimerStatus.Finished
        }
        if (!anyActive) {
            notificationManager.cancelNotification()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            notificationManager.updateNotification(state)
        }
    }

    private fun dismissFinished(index: Int) {
        if (TimerRepository.getTimer(index).status !in setOf(TimerStatus.Finished, TimerStatus.Overtime)) return
        resetTimer(index)
    }

    private fun enterOvertime(index: Int, now: Long = System.currentTimeMillis()) {
        TimerRepository.updateTimer(index) { t ->
            t.copy(
                status = TimerStatus.Overtime,
                remainingMillis = 0L,
                targetEndTimeMillis = null,
                pausedRemainingMillis = null,
                overtimeStartedAtMillis = now,
            )
        }

        val timer = TimerRepository.getTimer(index)
        // Latest-timer override: if a previous alert is active, stop it first
        if (lastAlertedTimerIndex >= 0) {
            stopFinishedAlertEffects()
        }
        lastAlertedTimerIndex = index
        alertFinished(timer.settings)

        if (!TimerRepository.isAppForeground) {
            promoteToForeground()
        }
        notificationManager.updateNotification(TimerRepository.getState())
        scheduleTick()
    }

    private fun addTimeDuringOvertime(index: Int, deltaMillis: Long) {
        if (deltaMillis <= 0L) return
        val timer = TimerRepository.getTimer(index)
        if (timer.status != TimerStatus.Overtime) return

        val now = System.currentTimeMillis()
        val updatedTimeToDismiss = timer.timeToDismissAccumulatedMillis +
            (now - (timer.overtimeStartedAtMillis ?: now)).coerceAtLeast(0L)
        val updatedTotalAdjustment = timer.totalAdjustmentMillis + deltaMillis

        TimerRepository.updateTimer(index) { t ->
            t.copy(
                status = TimerStatus.Running,
                selectedDurationMillis = (t.originalDurationMillis + updatedTotalAdjustment).coerceAtLeast(0L),
                remainingMillis = deltaMillis,
                targetEndTimeMillis = now + deltaMillis,
                pausedRemainingMillis = null,
                totalAdjustmentMillis = updatedTotalAdjustment,
                timeToDismissAccumulatedMillis = updatedTimeToDismiss,
                overtimeStartedAtMillis = null,
            )
        }

        if (lastAlertedTimerIndex == index) {
            stopFinishedAlertEffects()
            lastAlertedTimerIndex = -1
        }

        promoteToForeground()
        notificationManager.updateNotification(TimerRepository.getState())
        scheduleTick()
    }

    private fun completeLogEntry(index: Int) {
        val timer = TimerRepository.getTimer(index)
        val logId = timer.activeLogEntryId
        if (logId < 0L) return
        TimerRepository.updateTimer(index) { t -> t.copy(activeLogEntryId = -1L) }
        val adjustedDuration = timer.adjustedDurationMillis
        val timeToDismissMillis = timer.timeToDismissMillis
        val cumulativeDurationMillis = timer.cumulativeDurationMillis.takeIf { it > 0L }
        serviceScope.launch {
            db.appDao().completeLogEntry(
                logId,
                System.currentTimeMillis(),
                adjustedDuration,
                timeToDismissMillis,
                cumulativeDurationMillis,
            )
        }
    }

    private fun createLogEntry(timerIndex: Int, durationMillis: Long, timerName: String, presetId: Long?) {
        serviceScope.launch {
            val count = db.appDao().getLogCount()
            if (count >= 100) db.appDao().deleteOldestLogEntry()
            val id = db.appDao().insertLogEntry(
                com.fabiantorrestech.visualtimerplus.db.TimerLogEntity(
                    startedAt = System.currentTimeMillis(),
                    originalDurationMillis = durationMillis,
                    timerName = timerName.ifBlank { "Default" },
                    presetId = presetId,
                ),
            )
            TimerRepository.updateTimer(timerIndex) { t -> t.copy(activeLogEntryId = id) }
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

    private fun alertFinished(settings: TimerSettings) {
        if (settings.soundEnabled) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val streamType = when {
                settings.ignoreSilentMode -> AudioManager.STREAM_ALARM
                settings.finishedSoundRoute == FinishedSoundRoute.Alarm -> AudioManager.STREAM_ALARM
                settings.finishedSoundRoute == FinishedSoundRoute.Media -> AudioManager.STREAM_MUSIC
                else -> AudioManager.STREAM_NOTIFICATION
            }
            if (audioManager != null) {
                try {
                    val maxVol = audioManager.getStreamMaxVolume(streamType)
                    val rawVol = (maxVol * settings.finishedSoundVolumePercent / 100f).toInt()
                    val targetVol = if (settings.overrideMutedSystemVolume) rawVol.coerceAtLeast(1) else rawVol
                    savedStreamVolume = audioManager.getStreamVolume(streamType)
                    savedStreamType = streamType
                    audioManager.setStreamVolume(streamType, targetVol, 0)
                } catch (_: SecurityException) {
                    // DnD policy can block setStreamVolume — continue without volume override
                }
            }
            val soundUri = when {
                settings.ignoreSilentMode || settings.finishedSoundRoute == FinishedSoundRoute.Alarm ->
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else ->
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            finishTone = RingtoneManager.getRingtone(applicationContext, soundUri)?.also { ringtone ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.isLooping = true
                    if (streamType == AudioManager.STREAM_ALARM) {
                        ringtone.audioAttributes = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    ringtone.streamType = streamType
                    handler.postDelayed(replayToneRunnable, 500L)
                }
                ringtone.play()
            }
        }
        if (settings.finishedVibrationMode != FinishedVibrationMode.Off) {
            Haptics.startTimerFinishedVibration(applicationContext)
            settings.finishedVibrationMode.durationMillis?.let { durationMillis ->
                handler.postDelayed(stopFinishedVibrationRunnable, durationMillis)
            }
        }
    }

    private fun stopFinishedAlertEffects() {
        handler.removeCallbacks(stopFinishedVibrationRunnable)
        handler.removeCallbacks(replayToneRunnable)
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
        const val ACTION_ADJUST_OVERTIME = "com.fabiantorrestech.visualtimerplus.action.ADJUST_OVERTIME"
        const val EXTRA_TIMER_INDEX = "timer_index"
        const val EXTRA_ADJUST_DELTA_MILLIS = "adjust_delta_millis"

        private const val TICK_INTERVAL_MILLIS = 250L
        // Notification refresh rate is now user-configurable; this constant is unused.
    }
}
