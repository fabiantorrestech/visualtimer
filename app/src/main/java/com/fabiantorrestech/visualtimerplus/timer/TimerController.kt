package com.fabiantorrestech.visualtimerplus.timer

import android.content.Context
import android.content.Intent
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.TimerLogEntity
import com.fabiantorrestech.visualtimerplus.backup.AutoBackupManager
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import com.fabiantorrestech.visualtimerplus.schedule.ScheduledTimerManager
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
        ScheduledTimerManager.reconcileAllAsync(appContext)
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
                val targetIndex = current.findNextAvailableTimerSlot() ?: return
                val reusableIndex = current.findReusableEmptyTimerIndex()
                TimerRepository.update { state ->
                    if (reusableIndex == targetIndex) {
                        val updated = state.timers.toMutableList()
                        updated[targetIndex] = state.createBlankTimer(id = targetIndex)
                        state.copy(
                            timers = updated,
                            activeTimerIndex = targetIndex,
                        )
                    } else {
                        state.copy(
                            timers = state.timers + state.createBlankTimer(id = targetIndex),
                            activeTimerIndex = targetIndex,
                        )
                    }
                }
                syncNotification()
            }

            is TimerAction.RemoveTimer -> {
                val current = TimerRepository.getState()
                val idx = action.timerIndex
                if (idx !in current.timers.indices) return
                val timer = current.timers[idx]
                val scheduleId = timer.scheduleId
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
                        timers = reindexed.ifEmpty { listOf(state.createBlankTimer(id = 0)) },
                        activeTimerIndex = newActive,
                    )
                }
                ScheduledTimerManager.handleTimerLifecycleExitAsync(appContext, scheduleId)
                syncNotification()
            }

            TimerAction.RemoveAllTimers -> {
                val current = TimerRepository.getState()
                val removedScheduleIds = current.timers.mapNotNull { it.scheduleId }.distinct()
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
                        timers = listOf(state.createBlankTimer(id = 0)),
                        activeTimerIndex = 0,
                    )
                }
                removedScheduleIds.forEach { ScheduledTimerManager.handleTimerLifecycleExitAsync(appContext, it) }
                syncNotification()
            }

            TimerAction.RemoveNonRunningTimers -> {
                val current = TimerRepository.getState()
                val removedScheduleIds = current.timers
                    .filter { it.status != TimerStatus.Running }
                    .mapNotNull { it.scheduleId }
                    .distinct()
                TimerRepository.update { state ->
                    val kept = state.timers
                        .filter { it.status == TimerStatus.Running }
                        .mapIndexed { i, t -> t.copy(id = i) }
                    state.copy(
                        timers = kept.ifEmpty { listOf(state.createBlankTimer(id = 0)) },
                        activeTimerIndex = 0,
                    )
                }
                removedScheduleIds.forEach { ScheduledTimerManager.handleTimerLifecycleExitAsync(appContext, it) }
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
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOledMode -> {
                TimerRepository.update { state -> state.copy(isOledMode = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetHideStatusBarEnabled -> {
                TimerRepository.update { state -> state.copy(hideStatusBarEnabled = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetHideStatusBarOnlyWhenRunning -> {
                TimerRepository.update { state -> state.copy(hideStatusBarOnlyWhenRunning = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetNotificationMode -> {
                TimerRepository.update { state -> state.copy(notificationMode = action.mode) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetHidePageDotsInCleanMode -> {
                TimerRepository.update { state -> state.copy(hidePageDotsInCleanMode = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetDefaultTimerSettings -> {
                TimerRepository.update { state ->
                    state.copy(defaultTimerSettings = action.settings)
                        .withIdleDefaultManagedTimersReset()
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetConfirmSwipeDelete -> {
                TimerRepository.update { state -> state.copy(confirmSwipeDelete = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetAppDefaultDuration -> {
                TimerRepository.update { state ->
                    state.copy(defaultDurationMillis = clampDuration(action.durationMillis))
                        .withIdleDefaultManagedTimersReset()
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetTapToToggleMinimalMode -> {
                TimerRepository.update { state -> state.copy(tapToToggleMinimalMode = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetNotificationUpdateInterval -> {
                val clamped = NOTIFICATION_UPDATE_INTERVAL_STEPS
                    .minByOrNull { kotlin.math.abs(it - action.seconds) } ?: 15
                TimerRepository.update { state -> state.copy(notificationUpdateIntervalSeconds = clamped) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOverlayEnabled -> {
                TimerRepository.update { state -> state.copy(overlayEnabled = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOverlaySize -> {
                TimerRepository.update { state -> state.copy(overlaySize = action.size) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOverlayStyle -> {
                TimerRepository.update { state -> state.copy(overlayStyle = action.style) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOverlayShowTimerName -> {
                TimerRepository.update { state -> state.copy(overlayShowTimerName = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOverlayTimerNamePosition -> {
                TimerRepository.update { state -> state.copy(overlayTimerNamePosition = action.position) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOverlayShowOnLockscreen -> {
                TimerRepository.update { state -> state.copy(overlayShowOnLockscreen = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetFinishedAlertMode -> {
                TimerRepository.update { state -> state.copy(finishedAlertMode = action.mode) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetShowMissingFinishedAlertPermissionsBanner -> {
                TimerRepository.update {
                    state -> state.copy(showMissingFinishedAlertPermissionsBanner = action.enabled)
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetAutoBackupEnabled -> {
                TimerRepository.update { state -> state.copy(autoBackupEnabled = action.enabled) }
            }

            is TimerAction.SetAutoOpenAppAfterQuickStart -> {
                TimerRepository.update { state -> state.copy(autoOpenAppAfterQuickStart = action.enabled) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetQuickTimerLandscapePlacement -> {
                TimerRepository.update { state -> state.copy(quickTimerLandscapePlacement = action.placement) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetCustomFont -> {
                TimerRepository.update { state ->
                    state.copy(customFontPath = action.path, customFontDisplayName = action.displayName)
                }
            }

            // ── Per-timer settings ─────────────────────────────────────────────
            is TimerAction.SetSoundEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(soundEnabled = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetFinishedSoundRoute -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(finishedSoundRoute = action.route)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetFinishedSoundVolumePercent -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(finishedSoundVolumePercent = action.percent.coerceIn(0, 100)))
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetOverrideMutedSystemVolume -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(overrideMutedSystemVolume = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetIgnoreSilentMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(ignoreSilentMode = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetFullClockMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(fullClockMode = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetFinishedVibrationMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(finishedVibrationMode = action.mode)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetKeepScreenAwakeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(keepScreenAwake = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetShowCurrentTimeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(
                        showCurrentTimeEnabled = action.enabled,
                        showClockSecondsEnabled = if (action.enabled) t.settings.showClockSecondsEnabled else false,
                    ))
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetShowClockSecondsEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(showClockSecondsEnabled = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetClockPosition -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(clockPosition = action.position)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetClockTextSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(clockTextSizeSp = action.sp.coerceIn(14f, 60f))) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetClockwiseModeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(clockwiseModeEnabled = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetShowDirectionIndicator -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(showDirectionIndicator = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetCleanModeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(cleanModeEnabled = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetCleanModeAutoDismissEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(cleanModeAutoDismissEnabled = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetCleanModeAutoDismissSeconds -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(cleanModeAutoDismissSeconds = clampCleanModeAutoDismissSeconds(action.seconds)))
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetHideClockInCleanMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(hideClockInCleanMode = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetTimerTitleEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(timerTitleEnabled = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetTimerTitleHideInCleanMode -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(timerTitleHideInCleanMode = action.enabled)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetTimerTitlePosition -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t -> t.copy(settings = t.settings.copy(timerTitlePosition = action.position)) }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetTimerTitleTextSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(timerTitleTextSizeSp = action.sp.coerceIn(10f, 48f)))
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetCenterTimeSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(centerTimeSizeSp = action.sp.coerceIn(20f, 80f)))
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetShowEndTimeEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(showEndTimeEnabled = action.enabled))
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetShowEndTimeSecondsEnabled -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(showEndTimeSecondsEnabled = action.enabled))
                }
                AutoBackupManager.scheduleBackup(appContext)
            }

            is TimerAction.SetEndTimeSizeSp -> {
                val idx = resolveIndex(action.timerIndex)
                TimerRepository.updateTimer(idx) { t ->
                    t.copy(settings = t.settings.copy(endTimeSizeSp = action.sp.coerceIn(14f, 60f)))
                }
                AutoBackupManager.scheduleBackup(appContext)
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
                if (timer.status == TimerStatus.Running) {
                    startService(
                        TimerService.ACTION_ADJUST_RUNNING,
                        foreground = true,
                        timerIndex = idx,
                        adjustDeltaMillis = action.deltaMillis,
                    )
                    return
                }

                TimerRepository.updateTimer(idx) { t ->
                    when (t.status) {
                        TimerStatus.Running -> t
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
                    TimerRepository.update { state ->
                        state.withTimer(idx) { t ->
                            if (t.isDefaultManaged()) {
                                state.resetToLatestDefaults(t)
                            } else {
                                val resetDuration = t.defaultDurationMillis.takeIf { it > 0L } ?: 0L
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
                                    scheduleId = null,
                                    totalAdjustmentMillis = 0L,
                                    timeToDismissAccumulatedMillis = 0L,
                                    overtimeStartedAtMillis = null,
                                )
                            }
                        }
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

    fun addTimeDuringOvertime(timerIndex: Int, deltaMillis: Long) {
        if (deltaMillis <= 0L) return
        val timer = TimerRepository.getTimer(timerIndex)
        if (timer.status != TimerStatus.Overtime) return
        startService(
            TimerService.ACTION_ADJUST_OVERTIME,
            foreground = true,
            timerIndex = timerIndex,
            adjustDeltaMillis = deltaMillis,
        )
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
                    scheduleId = timer.scheduleId,
                ),
            )
            TimerRepository.updateTimer(timerIndex) { t -> t.copy(activeLogEntryId = id) }
        }
    }

    companion object {
        val NOTIFICATION_UPDATE_INTERVAL_STEPS = listOf(5, 10, 15, 30, 45, 60)
    }

    private fun AppState.withIdleDefaultManagedTimersReset(): AppState =
        copy(
            timers = timers.map { timer ->
                if (isReusableEmptyTimer(timer)) resetToLatestDefaults(timer) else timer
            },
        )

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
