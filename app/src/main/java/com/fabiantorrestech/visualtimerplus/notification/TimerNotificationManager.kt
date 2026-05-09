package com.fabiantorrestech.visualtimerplus.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fabiantorrestech.visualtimerplus.MainActivity
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.NotificationMode
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerService
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatClockTime

class TimerNotificationManager(
    private val context: Context,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val individualNotificationMax = 5

    init {
        ensureChannel()
    }

    fun buildServiceNotification(state: AppState): Notification {
        val activeTimer = state.activeTimer
        val channelId = if (activeTimer.status == TimerStatus.Finished || activeTimer.status == TimerStatus.Overtime) {
            FINISHED_CHANNEL_ID
        } else {
            RUNNING_CHANNEL_ID
        }
        return when (state.notificationMode) {
            NotificationMode.Consolidated -> buildConsolidatedNotification(state, channelId)
            NotificationMode.Individual -> {
                val primary = primaryTimer(state)
                val primaryChannelId = if (primary.status == TimerStatus.Finished || primary.status == TimerStatus.Overtime) {
                    FINISHED_CHANNEL_ID
                } else {
                    RUNNING_CHANNEL_ID
                }
                buildIndividualNotification(primary, primaryChannelId)
            }
        }
    }

    fun updateNotification(state: AppState) {
        val activeTimers = state.timers.filter { it.status != TimerStatus.Idle }
        if (activeTimers.isEmpty()) {
            cancelNotification()
            return
        }

        when (state.notificationMode) {
            NotificationMode.Consolidated -> {
                val channelId = if (state.timers.any { it.status == TimerStatus.Finished || it.status == TimerStatus.Overtime })
                    FINISHED_CHANNEL_ID else RUNNING_CHANNEL_ID
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildConsolidatedNotification(state, channelId),
                )
            }
            NotificationMode.Individual -> {
                // Pin NOTIFICATION_ID to the first running timer (not activeTimerIndex).
                // activeTimerIndex tracks UI pager focus; notification priority must follow
                // timer state — only the foreground service slot is exempt from OS throttling.
                val primary = primaryTimer(state)
                val activeChannelId = if (primary.status == TimerStatus.Finished || primary.status == TimerStatus.Overtime) {
                    FINISHED_CHANNEL_ID
                } else {
                    RUNNING_CHANNEL_ID
                }
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildIndividualNotification(primary, activeChannelId),
                )
                notificationManager.cancel(INDIVIDUAL_BASE + primary.id)

                activeTimers
                    .filter { it.id != primary.id }
                    .take(individualNotificationMax - 1)
                    .forEach { timer ->
                        val channelId = if (timer.status == TimerStatus.Finished || timer.status == TimerStatus.Overtime) {
                            FINISHED_CHANNEL_ID
                        } else {
                            RUNNING_CHANNEL_ID
                        }
                        notificationManager.notify(
                            INDIVIDUAL_BASE + timer.id,
                            buildIndividualNotification(timer, channelId),
                        )
                    }
                state.timers.filter { it.status == TimerStatus.Idle }.forEach { timer ->
                    notificationManager.cancel(INDIVIDUAL_BASE + timer.id)
                }
                for (i in state.timers.size until 20) {
                    notificationManager.cancel(INDIVIDUAL_BASE + i)
                }
            }
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        for (i in 0 until 20) {
            notificationManager.cancel(INDIVIDUAL_BASE + i)
        }
    }

    private fun buildConsolidatedNotification(state: AppState, channelId: String): Notification {
        val activeTimer = state.activeTimer
        val runningCount = state.timers.count {
            it.status == TimerStatus.Running ||
                it.status == TimerStatus.Paused ||
                it.status == TimerStatus.Overtime ||
                it.status == TimerStatus.Finished
        }
        val timerLabel = if (runningCount > 1) {
            val name = activeTimer.activeTimerName.ifBlank { "Timer ${state.activeTimerIndex + 1}" }
            "$name — ${state.activeTimerIndex + 1}/$runningCount"
        } else {
            activeTimer.activeTimerName.ifBlank { context.getString(R.string.notification_title) }
        }

        val builder = Notification.Builder(context, channelId)
            .setContentTitle(timerLabel)
            .setContentText(contentTextForTimer(activeTimer))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(activeTimer.status != TimerStatus.Idle)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent(state.activeTimerIndex))

        when (activeTimer.status) {
            TimerStatus.Running -> {
                builder
                    .addAction(Notification.Action.Builder(
                        null, context.getString(R.string.pause),
                        servicePendingIntent(TimerService.ACTION_PAUSE, state.activeTimerIndex),
                    ).build())
                    .addAction(Notification.Action.Builder(
                        null, context.getString(R.string.reset),
                        servicePendingIntent(TimerService.ACTION_RESET, state.activeTimerIndex),
                    ).build())
                if (runningCount > 1) {
                    builder.addAction(Notification.Action.Builder(
                        null, context.getString(R.string.notification_next_timer),
                        nextTimerPendingIntent(state),
                    ).build())
                }
            }
            TimerStatus.Paused -> {
                builder
                    .addAction(Notification.Action.Builder(
                        null, context.getString(R.string.resume),
                        servicePendingIntent(TimerService.ACTION_RESUME, state.activeTimerIndex),
                    ).build())
                    .addAction(Notification.Action.Builder(
                        null, context.getString(R.string.reset),
                        servicePendingIntent(TimerService.ACTION_RESET, state.activeTimerIndex),
                    ).build())
                if (runningCount > 1) {
                    builder.addAction(Notification.Action.Builder(
                        null, context.getString(R.string.notification_next_timer),
                        nextTimerPendingIntent(state),
                    ).build())
                }
            }
            TimerStatus.Overtime,
            TimerStatus.Finished -> {
                builder
                    .setCategory(Notification.CATEGORY_ALARM)
                    .addAction(Notification.Action.Builder(
                        null, context.getString(R.string.dismiss),
                        servicePendingIntent(TimerService.ACTION_DISMISS_FINISHED, state.activeTimerIndex),
                    ).build())
                if (runningCount > 1) {
                    builder.addAction(Notification.Action.Builder(
                        null, context.getString(R.string.notification_next_timer),
                        nextTimerPendingIntent(state),
                    ).build())
                }
            }
            TimerStatus.Idle -> builder.setOngoing(false)
        }

        return builder.build()
    }

    private fun buildIndividualNotification(timer: TimerInstance, channelId: String): Notification {
        val title = timer.activeTimerName.ifBlank { context.getString(R.string.notification_title) }
        val builder = Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(contentTextForTimer(timer))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(timer.status != TimerStatus.Idle)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent(timer.id))
            .setGroup("timer_${timer.id}")

        when (timer.status) {
            TimerStatus.Running -> {
                val endTime = timer.targetEndTimeMillis
                    ?: (System.currentTimeMillis() + timer.remainingMillis)
                builder
                    .setWhen(endTime)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
                    .setContentText(approximateContentText(timer.remainingMillis))
                    .addAction(Notification.Action.Builder(
                        null, context.getString(R.string.pause),
                        servicePendingIntent(TimerService.ACTION_PAUSE, timer.id),
                    ).build())
                    .addAction(Notification.Action.Builder(
                        null, context.getString(R.string.reset),
                        servicePendingIntent(TimerService.ACTION_RESET, timer.id),
                    ).build())
            }
            TimerStatus.Paused -> builder
                .addAction(Notification.Action.Builder(
                    null, context.getString(R.string.resume),
                    servicePendingIntent(TimerService.ACTION_RESUME, timer.id),
                ).build())
                .addAction(Notification.Action.Builder(
                    null, context.getString(R.string.reset),
                    servicePendingIntent(TimerService.ACTION_RESET, timer.id),
                ).build())
            TimerStatus.Overtime -> builder
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(Notification.Action.Builder(
                    null, context.getString(R.string.dismiss),
                    servicePendingIntent(TimerService.ACTION_DISMISS_FINISHED, timer.id),
                ).build())
            TimerStatus.Finished -> builder
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(Notification.Action.Builder(
                    null, context.getString(R.string.dismiss),
                    servicePendingIntent(TimerService.ACTION_DISMISS_FINISHED, timer.id),
                ).build())
            TimerStatus.Idle -> builder.setOngoing(false)
        }

        return builder.build()
    }

    private fun contentTextForTimer(timer: TimerInstance): String = when (timer.status) {
        TimerStatus.Idle -> context.getString(R.string.notification_idle)
        TimerStatus.Running -> context.getString(R.string.notification_running, timer.displayMillis.formatClockTime())
        TimerStatus.Paused -> context.getString(R.string.notification_paused, timer.displayMillis.formatClockTime())
        TimerStatus.Overtime -> context.getString(R.string.notification_overtime, timer.displayMillis.formatClockTime())
        TimerStatus.Finished -> context.getString(R.string.notification_finished)
    }

    private fun contentPendingIntent(timerIndex: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TARGET_TIMER_INDEX, timerIndex)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, timerIndex, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(action: String, timerIndex: Int): PendingIntent {
        val intent = Intent(context, TimerService::class.java)
            .setAction(action)
            .putExtra(TimerService.EXTRA_TIMER_INDEX, timerIndex)
        return PendingIntent.getForegroundService(
            context,
            (action + timerIndex).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextTimerPendingIntent(state: AppState): PendingIntent {
        val nonIdleIndices = state.timers.indices.filter { state.timers[it].status != TimerStatus.Idle }
        val currentPos = nonIdleIndices.indexOf(state.activeTimerIndex)
        val nextIndex = if (nonIdleIndices.isEmpty()) state.activeTimerIndex
        else nonIdleIndices[(currentPos + 1) % nonIdleIndices.size]

        val intent = Intent(context, TimerService::class.java)
            .setAction(ACTION_CYCLE_TIMER)
            .putExtra(TimerService.EXTRA_TIMER_INDEX, nextIndex)
        return PendingIntent.getForegroundService(
            context,
            ACTION_CYCLE_TIMER.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun approximateContentText(remainingMillis: Long): String {
        val totalSeconds = ((remainingMillis + 999L) / 1000L).coerceAtLeast(0L)
        return if (totalSeconds > 60L) {
            val roundedSeconds = ((totalSeconds + 15L) / 30L) * 30L
            val mins = roundedSeconds / 60L
            val secs = roundedSeconds % 60L
            if (secs == 0L)
                context.getString(R.string.notification_approx_minutes, mins)
            else
                context.getString(R.string.notification_approx_minutes_seconds, mins, secs)
        } else {
            val roundedSeconds = ((totalSeconds + 7L) / 15L) * 15L
            when {
                roundedSeconds == 0L -> context.getString(R.string.notification_finished)
                roundedSeconds >= 60L -> context.getString(R.string.notification_approx_minutes, 1L)
                else -> context.getString(R.string.notification_approx_seconds, roundedSeconds)
            }
        }
    }

    private fun primaryTimer(state: AppState): TimerInstance =
        state.timers.firstOrNull { it.status == TimerStatus.Running }
            ?: state.timers.firstOrNull { it.status == TimerStatus.Overtime }
            ?: state.timers.firstOrNull { it.status == TimerStatus.Paused }
            ?: state.timers.firstOrNull { it.status == TimerStatus.Finished }
            ?: state.activeTimer

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val runningChannel = NotificationChannel(
            RUNNING_CHANNEL_ID,
            context.getString(R.string.notification_running_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_running_channel_description)
            setShowBadge(false)
        }
        val finishedChannel = NotificationChannel(
            FINISHED_CHANNEL_ID,
            context.getString(R.string.notification_finished_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_finished_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannels(listOf(runningChannel, finishedChannel))
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val INDIVIDUAL_BASE = 2100
        const val RUNNING_CHANNEL_ID = "visual_timer_running_channel"
        const val FINISHED_CHANNEL_ID = "visual_timer_finished_channel"
        const val ACTION_CYCLE_TIMER = "com.fabiantorrestech.visualtimerplus.action.CYCLE_TIMER"
        const val EXTRA_TARGET_TIMER_INDEX = "target_timer_index"
    }
}
