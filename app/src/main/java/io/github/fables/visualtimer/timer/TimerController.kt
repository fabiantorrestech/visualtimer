package com.fabiantorrestech.visualtimerplus.timer

import android.content.Context
import android.content.Intent
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import kotlinx.coroutines.flow.StateFlow

class TimerController(context: Context) {
    private val appContext = context.applicationContext
    val uiState: StateFlow<TimerState> = TimerRepository.state

    init {
        TimerRepository.initialize(appContext)
        if (TimerRepository.getState().status == TimerStatus.Running) {
            appContext.startForegroundService(Intent(appContext, TimerService::class.java))
        }
    }

    fun dispatch(action: TimerAction) {
        when (action) {
            is TimerAction.SetDuration -> {
                if (TimerRepository.getState().status == TimerStatus.Running) return
                TimerRepository.update { state ->
                    val snappedDuration = snapDuration(action.durationMillis)
                    state.copy(
                        status = TimerStatus.Idle,
                        selectedDurationMillis = snappedDuration,
                        remainingMillis = snappedDuration,
                        targetEndTimeMillis = null,
                        pausedRemainingMillis = null,
                    )
                }
                syncNotification()
            }

            is TimerAction.AdjustDuration -> {
                TimerRepository.update { state ->
                    when (state.status) {
                        TimerStatus.Running -> {
                            val currentRemaining = state.targetEndTimeMillis
                                ?.let { clampDuration(it - System.currentTimeMillis()) }
                                ?: state.remainingMillis
                            val updatedRemaining = clampDuration(currentRemaining + action.deltaMillis)
                            state.copy(
                                selectedDurationMillis = updatedRemaining,
                                remainingMillis = updatedRemaining,
                                targetEndTimeMillis = System.currentTimeMillis() + updatedRemaining,
                                pausedRemainingMillis = null,
                            )
                        }

                        TimerStatus.Paused -> {
                            val updatedDuration = clampDuration(
                                (state.pausedRemainingMillis ?: state.remainingMillis) + action.deltaMillis,
                            )
                            state.copy(
                                selectedDurationMillis = updatedDuration,
                                remainingMillis = updatedDuration,
                                targetEndTimeMillis = null,
                                pausedRemainingMillis = updatedDuration,
                            )
                        }

                        TimerStatus.Idle,
                        TimerStatus.Finished,
                        -> {
                            val updatedDuration = clampDuration(state.selectedDurationMillis + action.deltaMillis)
                            state.copy(
                                status = TimerStatus.Idle,
                                selectedDurationMillis = updatedDuration,
                                remainingMillis = updatedDuration,
                                targetEndTimeMillis = null,
                                pausedRemainingMillis = null,
                            )
                        }
                    }
                }
                syncNotification()
            }

            is TimerAction.SetSoundEnabled -> {
                TimerRepository.update { state -> state.copy(soundEnabled = action.enabled) }
            }

            is TimerAction.SetFinishedVibrationMode -> {
                TimerRepository.update { state -> state.copy(finishedVibrationMode = action.mode) }
            }

            is TimerAction.SetKeepScreenAwakeEnabled -> {
                TimerRepository.update { state -> state.copy(keepScreenAwakeEnabled = action.enabled) }
            }

            is TimerAction.SetHideStatusBarEnabled -> {
                TimerRepository.update { state ->
                    state.copy(
                        hideStatusBarEnabled = action.enabled,
                        showCurrentTimeEnabled = if (action.enabled) state.showCurrentTimeEnabled else false,
                        showClockSecondsEnabled = if (action.enabled) state.showClockSecondsEnabled else false,
                    )
                }
            }

            is TimerAction.SetHideStatusBarOnlyWhenRunning -> {
                TimerRepository.update { state -> state.copy(hideStatusBarOnlyWhenRunning = action.enabled) }
            }

            is TimerAction.SetShowCurrentTimeEnabled -> {
                TimerRepository.update { state ->
                    state.copy(
                        showCurrentTimeEnabled = action.enabled,
                        showClockSecondsEnabled = if (action.enabled) state.showClockSecondsEnabled else false,
                    )
                }
            }

            is TimerAction.SetShowClockSecondsEnabled -> {
                TimerRepository.update { state -> state.copy(showClockSecondsEnabled = action.enabled) }
            }

            is TimerAction.SetClockPosition -> {
                TimerRepository.update { state -> state.copy(clockPosition = action.position) }
            }

            is TimerAction.SetClockTextSize -> {
                TimerRepository.update { state -> state.copy(clockTextSize = action.size) }
            }

            is TimerAction.SetClockwiseModeEnabled -> {
                TimerRepository.update { state -> state.copy(clockwiseModeEnabled = action.enabled) }
            }

            is TimerAction.SetCleanModeEnabled -> {
                TimerRepository.update { state -> state.copy(cleanModeEnabled = action.enabled) }
            }

            is TimerAction.SetHideClockInCleanMode -> {
                TimerRepository.update { state -> state.copy(hideClockInCleanMode = action.enabled) }
            }

            is TimerAction.SetThemeMode -> {
                TimerRepository.update { state -> state.copy(themeMode = action.mode) }
            }

            TimerAction.Start -> {
                val current = TimerRepository.getState()
                val durationMillis = current.selectedDurationMillis
                if (durationMillis <= 0L || current.status == TimerStatus.Running) return
                startService(TimerService.ACTION_START, foreground = true)
            }

            TimerAction.Pause -> {
                if (TimerRepository.getState().status == TimerStatus.Running) {
                    startService(TimerService.ACTION_PAUSE, foreground = false)
                }
            }

            TimerAction.Resume -> {
                if (TimerRepository.getState().status == TimerStatus.Paused) {
                    startService(TimerService.ACTION_RESUME, foreground = true)
                }
            }

            TimerAction.Reset -> {
                val current = TimerRepository.getState()
                if (current.status != TimerStatus.Idle) {
                    startService(TimerService.ACTION_RESET, foreground = false)
                } else {
                    TimerRepository.update { state ->
                        state.copy(
                            status = TimerStatus.Idle,
                            remainingMillis = state.selectedDurationMillis,
                            targetEndTimeMillis = null,
                            pausedRemainingMillis = null,
                        )
                    }
                    syncNotification()
                }
            }

            TimerAction.DismissFinished -> {
                if (TimerRepository.getState().status == TimerStatus.Finished) {
                    startService(TimerService.ACTION_DISMISS_FINISHED, foreground = false)
                }
            }
        }
    }

    fun setOledMode(enabled: Boolean) {
        TimerRepository.update { state -> state.copy(isOledMode = enabled) }
    }

    fun syncNotification() {
        TimerNotificationManager(appContext).updateNotification(TimerRepository.getState())
    }

    fun dismissFinishedIfActive() {
        dispatch(TimerAction.DismissFinished)
    }

    private fun startService(action: String, foreground: Boolean) {
        val intent = Intent(appContext, TimerService::class.java).setAction(action)
        if (foreground) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }
}
