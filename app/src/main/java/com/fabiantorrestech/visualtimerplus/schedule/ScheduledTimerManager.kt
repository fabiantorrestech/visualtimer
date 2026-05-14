package com.fabiantorrestech.visualtimerplus.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerEntity
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerOutcome
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerTimingMode
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerType
import com.fabiantorrestech.visualtimerplus.db.containsWeekday
import com.fabiantorrestech.visualtimerplus.db.scheduleTimingMode
import com.fabiantorrestech.visualtimerplus.db.scheduleType
import com.fabiantorrestech.visualtimerplus.timer.MAX_DURATION_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.MAX_TIMERS
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ScheduledTimerManager {
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_SCHEDULED_DURATION_MILLIS = "scheduled_duration_millis"
    const val EXTRA_SCHEDULED_TIMER_NAME = "scheduled_timer_name"
    const val EXTRA_SCHEDULED_PRESET_ID = "scheduled_preset_id"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
    }

    fun reconcileAllAsync(context: Context) {
        scope.launch { reconcileAll(context.applicationContext) }
    }

    suspend fun reconcileAll(context: Context) {
        val dao = AppDatabase.getInstance(context).appDao()
        if (!canScheduleExactAlarms(context)) {
            dao.getAllScheduledTimers().forEach { schedule ->
                cancelAlarm(context, schedule.id)
            }
            return
        }
        dao.getAllScheduledTimers().forEach { schedule ->
            val preset = schedule.presetId?.let { dao.getPresetById(it) }
            if (shouldKeepAlarmArmed(schedule, presetExists = preset != null)) {
                scheduleNextAlarm(context, schedule)
            } else {
                cancelAlarm(context, schedule.id)
            }
        }
    }

    suspend fun upsertSchedule(context: Context, schedule: ScheduledTimerEntity): Long {
        val dao = AppDatabase.getInstance(context).appDao()
        val savedId = if (schedule.id == 0L) {
            dao.insertScheduledTimer(schedule)
        } else {
            dao.updateScheduledTimer(schedule)
            schedule.id
        }
        dao.getScheduledTimerById(savedId)?.let { scheduleNextAlarm(context, it) }
        return savedId
    }

    suspend fun deleteSchedule(context: Context, schedule: ScheduledTimerEntity) {
        cancelAlarm(context, schedule.id)
        AppDatabase.getInstance(context).appDao().deleteScheduledTimer(schedule)
    }

    suspend fun deleteScheduleById(context: Context, scheduleId: Long) {
        cancelAlarm(context, scheduleId)
        AppDatabase.getInstance(context).appDao().deleteScheduledTimerById(scheduleId)
    }

    fun handleAlarmFireAsync(context: Context, scheduleId: Long) {
        scope.launch { handleAlarmFire(context.applicationContext, scheduleId) }
    }

    suspend fun handleAlarmFire(context: Context, scheduleId: Long) {
        TimerRepository.initialize(context)
        val dao = AppDatabase.getInstance(context).appDao()
        val schedule = dao.getScheduledTimerById(scheduleId) ?: return
        val now = System.currentTimeMillis()
        val preset = schedule.presetId?.let { dao.getPresetById(it) }

        when {
            scheduleRequiresPreset(schedule) && preset == null -> {
                dao.updateScheduledTimer(
                    schedule.copy(
                        lastOutcome = ScheduledTimerOutcome.MissingPreset.name,
                        lastOutcomeAtMillis = now,
                    ),
                )
            }
            !hasLaunchCapacity(TimerRepository.getState()) -> {
                dao.updateScheduledTimer(
                    schedule.copy(
                        lastOutcome = ScheduledTimerOutcome.MissedCapacity.name,
                        lastOutcomeAtMillis = now,
                    ),
                )
            }
            else -> {
                val durationMillis = computeLaunchDurationMillis(schedule)
                if (durationMillis != null) {
                    val timerName = schedule.name.ifBlank { preset?.name ?: "Scheduled timer" }
                    launchScheduledTimer(context, schedule.id, timerName, schedule.presetId, durationMillis)
                    dao.updateScheduledTimer(
                        schedule.copy(
                            lastOutcome = ScheduledTimerOutcome.Started.name,
                            lastOutcomeAtMillis = now,
                        ),
                    )
                }
            }
        }

        if (schedule.scheduleType() == ScheduledTimerType.Repeating && shouldKeepAlarmArmed(schedule, presetExists = preset != null)) {
            scheduleNextAlarm(context, schedule)
        } else {
            cancelAlarm(context, schedule.id)
        }
    }

    fun handleTimerLifecycleExitAsync(context: Context, scheduleId: Long?) {
        if (scheduleId == null) return
        scope.launch { handleTimerLifecycleExit(context.applicationContext, scheduleId) }
    }

    suspend fun handleTimerLifecycleExit(context: Context, scheduleId: Long) {
        TimerRepository.initialize(context)
        val stillActive = TimerRepository.getState().timers.any { it.scheduleId == scheduleId && it.status != com.fabiantorrestech.visualtimerplus.timer.TimerStatus.Idle }
        if (stillActive) return
        val dao = AppDatabase.getInstance(context).appDao()
        val schedule = dao.getScheduledTimerById(scheduleId) ?: return
        if (schedule.scheduleType() == ScheduledTimerType.OneTime) {
            deleteScheduleById(context, scheduleId)
        }
    }

    fun nextOccurrenceMillis(schedule: ScheduledTimerEntity, nowMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        return when (schedule.scheduleType()) {
            ScheduledTimerType.OneTime -> {
                val epochDay = schedule.oneTimeDateEpochDay ?: return null
                val date = LocalDate.ofEpochDay(epochDay)
                val dateTime = LocalDateTime.of(date, minutesToLocalTime(schedule.startTimeMinutes))
                val triggerMillis = dateTime.atZone(zoneId).toInstant().toEpochMilli()
                triggerMillis.takeIf { it > nowMillis }
            }
            ScheduledTimerType.Repeating -> {
                val startTime = minutesToLocalTime(schedule.startTimeMinutes)
                val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
                (0..7).firstNotNullOfOrNull { offset ->
                    val date = now.toLocalDate().plusDays(offset.toLong())
                    val day = date.dayOfWeek
                    if (!schedule.weekdayMask.containsWeekday(day)) {
                        null
                    } else {
                        val candidate = LocalDateTime.of(date, startTime).atZone(zoneId).toInstant().toEpochMilli()
                        candidate.takeIf { it > nowMillis }
                    }
                }
            }
        }
    }

    fun computeLaunchDurationMillis(schedule: ScheduledTimerEntity): Long? {
        return when (schedule.scheduleTimingMode()) {
            ScheduledTimerTimingMode.Duration -> {
                schedule.durationMillis?.takeIf { it > 0L && it <= MAX_DURATION_MILLIS }
            }
            ScheduledTimerTimingMode.EndTime -> {
                val endMinutes = schedule.endTimeMinutes ?: return null
                val startMinutes = schedule.startTimeMinutes
                val baseDate = if (schedule.scheduleType() == ScheduledTimerType.OneTime) {
                    LocalDate.ofEpochDay(schedule.oneTimeDateEpochDay ?: LocalDate.now().toEpochDay())
                } else {
                    LocalDate.now()
                }
                val start = LocalTime.of(startMinutes / 60, startMinutes % 60)
                val end = LocalTime.of(endMinutes / 60, endMinutes % 60)
                val startDateTime = LocalDateTime.of(baseDate, start)
                val endDateTime = LocalDateTime.of(
                    baseDate.plusDays(if (endMinutes <= startMinutes) 1 else 0),
                    end,
                )
                val durationMillis = java.time.Duration.between(startDateTime, endDateTime).toMillis()
                durationMillis.takeIf { it > 0L && it <= MAX_DURATION_MILLIS }
            }
        }
    }

    fun minutesToLocalTime(totalMinutes: Int): LocalTime = LocalTime.of(totalMinutes / 60, totalMinutes % 60)

    fun scheduleRequiresPreset(schedule: ScheduledTimerEntity): Boolean =
        schedule.scheduleType() == ScheduledTimerType.Repeating

    fun shouldKeepAlarmArmed(schedule: ScheduledTimerEntity, presetExists: Boolean): Boolean {
        if (scheduleRequiresPreset(schedule) && !presetExists) return false
        return !(schedule.scheduleType() == ScheduledTimerType.OneTime && nextOccurrenceMillis(schedule) == null)
    }

    fun hasLaunchCapacity(state: com.fabiantorrestech.visualtimerplus.timer.AppState): Boolean =
        state.timers.size < MAX_TIMERS || state.timers.any { it.status == TimerStatus.Idle }

    fun weekdaySummary(mask: Int): String {
        val days = DayOfWeek.entries.filter { mask.containsWeekday(it) }
        if (days.size == DayOfWeek.entries.size) return "Every day"
        if (days.isEmpty()) return "No days"
        return days.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::titlecase) }
    }

    private fun launchScheduledTimer(
        context: Context,
        scheduleId: Long,
        timerName: String,
        presetId: Long?,
        durationMillis: Long,
    ) {
        val intent = Intent(context, com.fabiantorrestech.visualtimerplus.timer.TimerService::class.java)
            .setAction(com.fabiantorrestech.visualtimerplus.timer.TimerService.ACTION_START_SCHEDULED)
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            .putExtra(EXTRA_SCHEDULED_TIMER_NAME, timerName)
            .putExtra(EXTRA_SCHEDULED_PRESET_ID, presetId ?: -1L)
            .putExtra(EXTRA_SCHEDULED_DURATION_MILLIS, durationMillis)
        context.startForegroundService(intent)
    }

    private suspend fun scheduleNextAlarm(context: Context, schedule: ScheduledTimerEntity) {
        val nextTrigger = nextOccurrenceMillis(schedule) ?: run {
            cancelAlarm(context, schedule.id)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context, schedule.id)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
    }

    private fun cancelAlarm(context: Context, scheduleId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, scheduleId))
    }

    private fun pendingIntent(context: Context, scheduleId: Long): PendingIntent {
        val intent = Intent(context, ScheduledTimerReceiver::class.java)
            .setAction("${context.packageName}.SCHEDULED_TIMER_FIRE")
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        return PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
