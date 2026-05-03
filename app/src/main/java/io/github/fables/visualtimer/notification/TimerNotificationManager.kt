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
import com.fabiantorrestech.visualtimerplus.timer.TimerService
import com.fabiantorrestech.visualtimerplus.timer.TimerState
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.util.formatClockTime

class TimerNotificationManager(
    private val context: Context,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        ensureChannel()
    }

    fun buildServiceNotification(state: TimerState): Notification =
        buildNotification(
            state = state,
            channelId = if (state.status == TimerStatus.Finished) FINISHED_CHANNEL_ID else RUNNING_CHANNEL_ID,
        )

    fun showFinishedNotification(state: TimerState) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, FINISHED_CHANNEL_ID))
    }

    fun updateNotification(state: TimerState) {
        when (state.status) {
            TimerStatus.Idle -> cancelNotification()
            TimerStatus.Finished -> showFinishedNotification(state)
            TimerStatus.Running, TimerStatus.Paused -> {
                notificationManager.notify(NOTIFICATION_ID, buildServiceNotification(state))
            }
        }
    }

    private fun buildNotification(
        state: TimerState,
        channelId: String,
    ): Notification {
        val builder = Notification.Builder(context, channelId)
            .setContentTitle(titleForState(state.status))
            .setContentText(contentTextForState(state))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(
                state.status == TimerStatus.Running ||
                    state.status == TimerStatus.Paused ||
                    state.status == TimerStatus.Finished,
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent())

        when (state.status) {
            TimerStatus.Running -> {
                builder
                    .addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.pause),
                            servicePendingIntent(TimerService.ACTION_PAUSE),
                        ).build(),
                    )
                    .addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.reset),
                            servicePendingIntent(TimerService.ACTION_RESET),
                        ).build(),
                    )
            }

            TimerStatus.Paused -> {
                builder
                    .addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.resume),
                            servicePendingIntent(TimerService.ACTION_RESUME),
                        ).build(),
                    )
                    .addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.reset),
                            servicePendingIntent(TimerService.ACTION_RESET),
                        ).build(),
                    )
            }

            TimerStatus.Finished -> {
                builder
                    .setCategory(Notification.CATEGORY_ALARM)
                    .addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.dismiss),
                            servicePendingIntent(TimerService.ACTION_DISMISS_FINISHED),
                        ).build(),
                    )
            }

            TimerStatus.Idle -> {
                builder
                    .setOngoing(false)
            }
        }

        return builder.build()
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, TimerService::class.java).setAction(action)
        return PendingIntent.getForegroundService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

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

    private fun titleForState(status: TimerStatus): String = when (status) {
        TimerStatus.Idle -> context.getString(R.string.notification_title)
        TimerStatus.Running -> context.getString(R.string.notification_title)
        TimerStatus.Paused -> context.getString(R.string.notification_paused_title)
        TimerStatus.Finished -> context.getString(R.string.notification_finished_title)
    }

    private fun contentTextForState(state: TimerState): String = when (state.status) {
        TimerStatus.Idle -> context.getString(R.string.notification_idle)
        TimerStatus.Running -> context.getString(
            R.string.notification_running,
            state.displayMillis.formatClockTime(),
        )
        TimerStatus.Paused -> context.getString(
            R.string.notification_paused,
            state.displayMillis.formatClockTime(),
        )
        TimerStatus.Finished -> context.getString(R.string.notification_finished)
    }

    companion object {
        const val RUNNING_CHANNEL_ID = "visual_timer_running_channel"
        const val FINISHED_CHANNEL_ID = "visual_timer_finished_channel"
        const val NOTIFICATION_ID = 2001
    }
}
