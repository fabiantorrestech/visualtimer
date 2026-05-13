package com.fabiantorrestech.visualtimerplus.timer

import android.content.Context
import android.content.Intent
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.TimerLogEntity
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimerController(context: Context) {
    private val appContext = context.applicationContext
    val uiState: StateFlow<AppState> = TimerRepository.state
    private val db = AppDatabase.getInstance(appContext)
    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        TimerRepository.initialize(appContext)
        TimerOverlayManager.initialize(appContext)
        val state = TimerRepository.getState()
        if (state.timers.any { it.status == TimerStatus.Running || it.status == TimerStatus.Overtime }) {
            appContext.startForegroundService(Intent(appContext, TimerService::class.java))
        }
    }

    fun dispatch(action: TimerAction) {
        val appState = TimerRepository.getState()

        // Resolve -1 timerIndex to the active timer index
        fun resolveIndex(raw: Int): Int =
            if (raw == -1) appState.activeTimerIndex else raw

        when (action) {
            // ── Multi-timer management ─────────────────────────────────────────
            TimerAction.AddTimer -> {
                val current = TimerRepository.getState()
                if (current.timers.size >= 20) return
                val newId = current.timers.size
                val newTimer = TimerInstance(
                    id = newId,
                    settings = current.defaultTimerSettings,
                    defaultDurationMillis = current.defaultDurationMillis,
                    selectedDurationMillis = current.defaultDurationMillis,
                    remainingMillis = current.defaultDurationMillis,
                )
                TimerRepository.update { state ->
                    state.copy(timers = state.timers + newTimer, activeTimerIndex = newId)
                }
                syncNotification()
            }

            is TimerAction.RemoveTimer -> {
                val current = TimerRepository.getState()
                val idx = action.timerIndex
                if (idx !in current.timers.indices) return
                val timer = current.timers[idx]
                // Stop service for this timer if running
                if (
                    timer.status == TimerStatus.Running ||
                    timer.status == TimerStatus.Paused ||
                    timer.status == TimerStatus.Overtime
                ) {
                    startService(TimerService.ACTION_RESET, foreground = false, timerIndex = idx)
                }
                TimerRepository.update { state ->
                    val updated = state.timers.toMutableList()
                    updated.removeAt(idx)
                    // Re-assign IDs to keep them sequential
                    val reindexed = updated.mapIndexed { i, t -> t.copy(id = i) }
                    val newActive = state.activeTimerIndex.coerceAtMost((reindexed.size - 1).coerceAtLeast(0))
                    state.copy(
                        timers = reindexed.ifEmpty { listOf(TimerInstance(id = 0, settings = state.defaultTimerSettings)) },
                        activeTimerIndex = newActive,
                    )
                }
                syncNotification()
            }

            TimerAction.RemoveAllTimers -> {
                val current = TimerRepository.getState()
                current.timers.forEachIndexed { idx, timer ->
                    if (
                        timer.status == TimerStatus.Running ||
                        timer.status == TimerStatus.Paused ||
                        timer.status == TimerStatus.Overtime
                    ) {
                        startService(TimerService.ACTION_RESET, foreground = false, timerIndex = idx)
                    }
                }
                TimerRepository.update { state ->
                    state.copy(
                        timers = listOf(TimerInstance(id = 0, settings = state.defaultTimerSettings)),
                        activeTimerIndex = 0,
                    )
                }
                syncNotification()
            }

            TimerAction.RemoveNonRunningTimers -> {
                val current = TimerRepository.getState()
                val running = current.timers.filter { it.status == TimerStatus.Running }
                TimerRepository.update { state ->
                    val kept = state.timers
                        .filter { it.status == TimerStatus.Running }
                        .mapIndexed { i, t -> t.copy(id = i) }
                    state.copy(
                        timers = kept.ifEmpty { listOf(TimerInstance(id = 0, settings = state.defaultTimerSettings)) },
                        activeTimerIndex = 0,
                    )
                }
                syncNotification()
            }

            is TimerAction.SetActiveTimer -> {
                val current = TimerRepository.getState()
                if (action.index !in current.timers.indices) return
                TimerRepository.update { state -> state.copy(activeTimerIndex = action.index) }
                syncNotification()
            }

            // ── App-global settings ────────────────────────────────────────────
            is TimerAction.SetThemeMode -> {
                TimerRepository.update { state -> state.copy(themeMode = action.mode) }
            }

            is TimerAction.SetOledMode -> {
                TimerRepository.update { state -> state.copy(isOledMode = action.enabled) }
            }

            is TimerAction.SetHideStatusBarEnabled -> {
                TimerRepository.update { state -> state.copy(hideStatusBarEnabled = action.enabled) }
            }

            is TimerAction.SetHideStatusBarOnlyWhenRunning -> {
                TimerRepository.update { state -> state.copy(hideStatusBarOnlyWhenRunning = action.enabled) }
            }

            is TimerAction.SetNotificationMode -> {
                TimerRepository.update { state -> state.copy(notificationMode = action.mode) }
            }

            is TimerAction.SetHidePageDotsInCleanMode -> {
                TimerRepository.update { state -> state.copy(hidePageDotsInCleanMode = action.enabled) }
            }

            is TimerAction.SetDefaultTimerSettings -> {
                TimerRepository.update { state -> state.copy(defaultTimerSettings = action.settings) }
            }

            is TimerAction.SetConfirmSwipeDelete -> {
                TimerRepository.update { state -> state.copy(confirmSwipeDelete = action.enabled) }
            }

            is TimerAction.SetAppDefaultDuration -> {
                TimerRepository.update { state -> state.copy(defaultDurationMillis = action.durationMillis) }
            }

            is TimerAction.SetTapToToggleMinimalMode -> {
                TimerRepository.update { state -> state.copy(tapToToggleMinimalMode = action.enabled) }
            }

            is TimerAction.SetNotificationUpdateInterval -> {
                val clamped = NOTIFICATION_UPDATE_INTERVAL_STEPS
                    .minByOrNull { kotlin.math.abs(it - action.seconds) } ?: 15
                TimerRepository.update { state -> state.copy(notificationUpdateIntervalSeconds = clamped) }
            }

            is TimerAction.SetOverlayEnabled -> {
                TimerRepository.update { state -> state.copy(overlayEnabled = action.enabled) }
            }

            is TimerAction.SetOverlaySize -> {
                TimerRepository.update { state -> state.copy(overlaySize = action.size) }
            }

            is TimerAction.SetOverlayStyle -> {
                TimerRepository.update { state -> state.copy(overlayStyle = action.style) }
            }

            is TimerAction.SetOverlayShowOnLockscreen -> {
                TimerRepository.update { state -> state.copy(overlayShowOnLockscreen = action.enabled) }
            }

            // ── Per-timer settings ─────────────────────────────────────────────
            is TimerAction.SetSoundEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(soundEnabled = action.enabled)) }
            }

            is TimerAction.SetFinishedSoundRoute -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(finishedSoundRoute = action.route)) }
            }

            is TimerAction.SetFinishedSoundVolumePercent -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(finishedSoundVolumePercent = action.percent.coerceIn(0, 100)))
                }
            }

            is TimerAction.SetOverrideMutedSystemVolume -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(overrideMutedSystemVolume = action.enabled)) }
            }

            is TimerAction.SetIgnoreSilentMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(ignoreSilentMode = action.enabled)) }
            }

            is TimerAction.SetFullClockMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(fullClockMode = action.enabled)) }
            }

            is TimerAction.SetFinishedVibrationMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(finishedVibrationMode = action.mode)) }
            }

            is TimerAction.SetKeepScreenAwakeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(keepScreenAwake = action.enabled)) }
            }

            is TimerAction.SetShowCurrentTimeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(
                        showCurrentTimeEnabled = action.enabled,
                        showClockSecondsEnabled = if (action.enabled) t.settings.showClockSecondsEnabled else false,
                    ))
                }
            }

            is TimerAction.SetShowClockSecondsEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(showClockSecondsEnabled = action.enabled)) }
            }

            is TimerAction.SetClockPosition -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(clockPosition = action.position)) }
            }

            is TimerAction.SetClockTextSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(clockTextSizeSp = action.sp.coerceIn(14f, 60f))) }
            }

            is TimerAction.SetClockwiseModeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(clockwiseModeEnabled = action.enabled)) }
            }

            is TimerAction.SetCleanModeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(cleanModeEnabled = action.enabled)) }
            }

            is TimerAction.SetCleanModeAutoDismissEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(cleanModeAutoDismissEnabled = action.enabled)) }
            }

            is TimerAction.SetCleanModeAutoDismissSeconds -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(cleanModeAutoDismissSeconds = clampCleanModeAutoDismissSeconds(action.seconds)))
                }
            }

            is TimerAction.SetHideClockInCleanMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(hideClockInCleanMode = action.enabled)) }
            }

            is TimerAction.SetTimerTitleEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(timerTitleEnabled = action.enabled)) }
            }

            is TimerAction.SetTimerTitleHideInCleanMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(timerTitleHideInCleanMode = action.enabled)) }
            }

            is TimerAction.SetTimerTitlePosition -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(timerTitlePosition = action.position)) }
            }

            is TimerAction.SetTimerTitleTextSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(timerTitleTextSizeSp = action.sp.coerceIn(10f, 48f)))
                }
            }

            is TimerAction.SetCenterTimeSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(centerTimeSizeSp = action.sp.coerceIn(20f, 80f)))
                }
            }

            is TimerAction.SetShowEndTimeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(showEndTimeEnabled = action.enabled))
                }
            }

            is TimerAction.SetShowEndTimeSecondsEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(showEndTimeSecondsEnabled = action.enabled))
                }
            }

            is TimerAction.SetEndTimeSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(endTimeSizeSp = action.sp.coerceIn(14f, 60f)))
                }
            }

            // ── Per-timer data ─────────────────────────────────────────────────
            is TimerAction.SetDuration -> {
                val idx = resolveIndex(action.timerIndex)
                val timer = TimerRepository.getTimer(idx)
                if (timer.status == TimerStatus.Running || timer.status == TimerStatus.Overtime) return
                val snapped = snapDuration(action.durationMillis)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(
                        status = TimerStatus.Idle,
                        selectedDurationMillis = snapped,
                        remainingMillis = snapped,
                        targetEndTimeMillis = null,
                        pausedRemainingMillis = null,
                        originalDurationMillis = 0L,
                        activeLogEntryId = -1L,
                        totalAdjustmentMillis = 0L,
                        timeToDismissAccumulatedMillis = 0L,
                        overtimeStartedAtMillis = null,
                    )
                }
                syncNotification()
            }

            is TimerAction.SetDurationExact -> {
                val idx = resolveIndex(action.timerIndex)
                val timer = TimerRepository.getTimer(idx)
                if (timer.status == TimerStatus.Running || timer.status == TimerStatus.Overtime) return
                val clamped = clampDuration(action.durationMillis)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(
                        status = TimerStatus.Idle,
                        selectedDurationMillis = clamped,
                        remainingMillis = clamped,
                        targetEndTimeMillis = null,
                        pausedRemainingMillis = null,
                        originalDurationMillis = 0L,
                        activeLogEntryId = -1L,
                        totalAdjustmentMillis = 0L,
                        timeToDismissAccumulatedMillis = 0L,
                        overtimeStartedAtMillis = null,
                    )
                }
                syncNotification()
            }

            is TimerAction.AdjustDuration -> {
                val idx = resolveIndex(action.timerIndex)
                val timer = TimerRepository.getTimer(idx)
                if (timer.status == TimerStatus.Overtime) {
                    if (action.deltaMillis > 0L) {
                        startService(
                            TimerService.ACTION_ADJUST_OVERTIME,
                            foreground = true,
                            timerIndex = idx,
                            adjustDeltaMillis = action.deltaMillis,
                        )
                    }
                    return
                }

                TimerRepository.updateTimer(idx) { t ->
                    when (t.status) {
                        TimerStatus.Running -> {
                            val currentRemaining = t.targetEndTimeMillis
                                ?.let { clampDuration(it - System.currentTimeMillis()) }
                                ?: t.remainingMillis
                            val updated = clampDuration(currentRemaining + action.deltaMillis).coerceAtMost(DRAG_MAX_MILLIS)
                            val appliedDelta = updated - currentRemaining
                            val totalAdjustment = t.totalAdjustmentMillis + appliedDelta
                            t.copy(
                                selectedDurationMillis = (t.originalDurationMillis + totalAdjustment).coerceAtLeast(0L),
                                remainingMillis = updated,
                                targetEndTimeMillis = System.currentTimeMillis() + updated,
                                pausedRemainingMillis = null,
                                totalAdjustmentMillis = totalAdjustment,
                            )
                        }
                        TimerStatus.Paused -> {
                            val currentRemaining = t.pausedRemainingMillis ?: t.remainingMillis
                            val updated = clampDuration(currentRemaining + action.deltaMillis).coerceAtMost(DRAG_MAX_MILLIS)
                            val appliedDelta = updated - currentRemaining
                            val totalAdjustment = t.totalAdjustmentMillis + appliedDelta
                            t.copy(
                                selectedDurationMillis = (t.originalDurationMillis + totalAdjustment).coerceAtLeast(0L),
                                remainingMillis = updated,
                                targetEndTimeMillis = null,
                                pausedRemainingMillis = updated,
                                totalAdjustmentMillis = totalAdjustment,
                            )
                        }
                        TimerStatus.Idle, TimerStatus.Finished -> {
                            val updated = clampDuration(t.selectedDurationMillis + action.deltaMillis)
                                .coerceAtMost(DRAG_MAX_MILLIS)
                            t.copy(
                                status = TimerStatus.Idle,
                                selectedDurationMillis = updated,
                                remainingMillis = updated,
                                targetEndTimeMillis = null,
                                pausedRemainingMillis = null,
                                activeLogEntryId = -1L,
                                totalAdjustmentMillis = 0L,
                                timeToDismissAccumulatedMillis = 0L,
                                overtimeStartedAtMillis = null,
                            )
                        }
                        TimerStatus.Overtime -> t
                    }
                }
                syncNotification()
            }

            is TimerAction.SetDefaultDuration -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(defaultDurationMillis = clampDuration(action.durationMillis))
                }
            }

            is TimerAction.SetActiveTimerName -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(activeTimerName = action.name) }
            }

            is TimerAction.SetActivePresetId -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(activePresetId = action.id) }
            }

            is TimerAction.SetPromptBeforeStart -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(promptBeforeStart = action.enabled)) }
            }

            // ── Timer lifecycle ────────────────────────────────────────────────
            is TimerAction.Start -> {
                val idx = resolveIndex(action.timerIndex)
                val timer = TimerRepository.getTimer(idx)
                if (
                    timer.selectedDurationMillis <= 0L ||
                    timer.status == TimerStatus.Running ||
                    timer.status == TimerStatus.Overtime
                ) return
                createLogEntry(idx)
                startService(TimerService.ACTION_START, foreground = true, timerIndex = idx)
            }

            is TimerAction.Pause -> {
                val idx = resolveIndex(action.timerIndex)
                if (TimerRepository.getTimer(idx).status == TimerStatus.Running) {
                    startService(TimerService.ACTION_PAUSE, foreground = false, timerIndex = idx)
                }
            }

            is TimerAction.Resume -> {
                val idx = resolveIndex(action.timerIndex)
                if (TimerRepository.getTimer(idx).status == TimerStatus.Paused) {
                    startService(TimerService.ACTION_RESUME, foreground = true, timerIndex = idx)
                }
            }

            is TimerAction.Reset -> {
                val idx = resolveIndex(action.timerIndex)
                val timer = TimerRepository.getTimer(idx)
                if (timer.status != TimerStatus.Idle) {
                    startService(TimerService.ACTION_RESET, foreground = false, timerIndex = idx)
                } else {
                    val resetDuration = timer.defaultDurationMillis.takeIf { it > 0L } ?: 0L
                    TimerRepository.updateTimer(idx) { t ->
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
                    syncNotification()
                }
            }

            is TimerAction.DismissFinished -> {
                val idx = resolveIndex(action.timerIndex)
                if (TimerRepository.getTimer(idx).status in setOf(TimerStatus.Finished, TimerStatus.Overtime)) {
                    startService(TimerService.ACTION_DISMISS_FINISHED, foreground = false, timerIndex = idx)
                }
            }

            is TimerAction.Restart -> {
                val idx = resolveIndex(action.timerIndex)
                val timer = TimerRepository.getTimer(idx)
                if (timer.status !in setOf(TimerStatus.Finished, TimerStatus.Overtime) || timer.originalDurationMillis <= 0L) return
                startService(TimerService.ACTION_RESTART, foreground = true, timerIndex = idx)
            }
        }
    }

    fun syncNotification() {
        TimerNotificationManager(appContext).updateNotification(TimerRepository.getState())
    }

    fun dismissFinishedIfActive() {
        val state = TimerRepository.getState()
        state.timers.forEachIndexed { idx, timer ->
            if (timer.status == TimerStatus.Finished || timer.status == TimerStatus.Overtime) {
                dispatch(TimerAction.DismissFinished(timerIndex = idx))
            }
        }
    }

    private fun createLogEntry(timerIndex: Int) {
        val timer = TimerRepository.getTimer(timerIndex)
        controllerScope.launch {
            val count = db.appDao().getLogCount()
            if (count >= 100) db.appDao().deleteOldestLogEntry()
            val id = db.appDao().insertLogEntry(
                TimerLogEntity(
                    startedAt = System.currentTimeMillis(),
                    originalDurationMillis = timer.selectedDurationMillis,
                    timerName = timer.activeTimerName.ifBlank { "Default" },
                    presetId = timer.activePresetId,
                ),
            )
            TimerRepository.updateTimer(timerIndex) { t -> t.copy(activeLogEntryId = id) }
        }
    }

    companion object {
        val NOTIFICATION_UPDATE_INTERVAL_STEPS = listOf(5, 10, 15, 30, 45, 60)
    }

    private fun startService(
        action: String,
        foreground: Boolean,
        timerIndex: Int = -1,
        adjustDeltaMillis: Long = 0L,
    ) {
        val intent = Intent(appContext, TimerService::class.java)
            .setAction(action)
            .putExtra(TimerService.EXTRA_TIMER_INDEX, timerIndex)
            .putExtra(TimerService.EXTRA_ADJUST_DELTA_MILLIS, adjustDeltaMillis)
        if (foreground) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }
}
