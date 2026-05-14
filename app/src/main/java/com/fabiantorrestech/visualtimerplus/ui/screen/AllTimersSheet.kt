package com.fabiantorrestech.visualtimerplus.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.PresetEntity
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerEntity
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerOutcome
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerTimingMode
import com.fabiantorrestech.visualtimerplus.db.ScheduledTimerType
import com.fabiantorrestech.visualtimerplus.db.scheduleOutcome
import com.fabiantorrestech.visualtimerplus.db.scheduleTimingMode
import com.fabiantorrestech.visualtimerplus.db.scheduleType
import com.fabiantorrestech.visualtimerplus.db.toWeekdaySet
import com.fabiantorrestech.visualtimerplus.db.weekdayMaskFor
import com.fabiantorrestech.visualtimerplus.schedule.ScheduledTimerManager
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.MAX_DURATION_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.component.DurationPickerSheet
import com.fabiantorrestech.visualtimerplus.ui.component.VisualTimerCanvas
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TimerSheetTab { Active, Scheduled }

private enum class ScheduledStatus {
    Scheduled,
    Running,
    Missed,
    MissingPreset,
}

private data class ScheduledTimerDraft(
    val id: Long = 0L,
    val name: String = "",
    val presetId: Long? = null,
    val type: ScheduledTimerType = ScheduledTimerType.OneTime,
    val oneTimeDate: LocalDate = LocalDate.now().plusDays(1),
    val weekdays: Set<DayOfWeek> = setOf(LocalDate.now().dayOfWeek),
    val startTime: LocalTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0),
    val timingMode: ScheduledTimerTimingMode = ScheduledTimerTimingMode.Duration,
    val durationMillis: Long = 30L * 60L * 1000L,
    val endTime: LocalTime = LocalTime.now().plusHours(1).withSecond(0).withNano(0),
) {
    val requiresPreset: Boolean
        get() = type == ScheduledTimerType.Repeating

    val selectedStartDateTime: LocalDateTime
        get() = LocalDateTime.of(oneTimeDate, startTime)

    val isStartTimeInPast: Boolean
        get() = type == ScheduledTimerType.OneTime &&
            !selectedStartDateTime.atZone(ZoneId.systemDefault()).toInstant().isAfter(Instant.now())

    fun toEntity(): ScheduledTimerEntity = ScheduledTimerEntity(
        id = id,
        name = name.trim(),
        presetId = if (requiresPreset) requireNotNull(presetId) else presetId,
        type = type.name,
        oneTimeDateEpochDay = if (type == ScheduledTimerType.OneTime) oneTimeDate.toEpochDay() else null,
        weekdayMask = if (type == ScheduledTimerType.Repeating) weekdayMaskFor(weekdays) else 0,
        startTimeMinutes = startTime.hour * 60 + startTime.minute,
        timingMode = timingMode.name,
        durationMillis = if (timingMode == ScheduledTimerTimingMode.Duration) durationMillis else null,
        endTimeMinutes = if (timingMode == ScheduledTimerTimingMode.EndTime) endTime.hour * 60 + endTime.minute else null,
        lastOutcome = ScheduledTimerOutcome.None.name,
        lastOutcomeAtMillis = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTimersSheet(
    db: AppDatabase,
    appState: AppState,
    onNavigateToTimer: (Int) -> Unit,
    onDeleteTimer: (Int) -> Unit,
    onDeleteAllTimers: () -> Unit,
    onDeleteNonRunningTimers: () -> Unit,
    onDismiss: () -> Unit,
    confirmSwipeDelete: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val presets by db.appDao().observePresets().collectAsState(emptyList())
    val schedules by db.appDao().observeScheduledTimers().collectAsState(emptyList())
    val presetMap = remember(presets) { presets.associateBy { it.id } }
    var exactAlarmsAvailable by remember { mutableStateOf(ScheduledTimerManager.canScheduleExactAlarms(context)) }

    var selectedTab by remember { mutableStateOf(TimerSheetTab.Active) }
    var activeSearchQuery by remember { mutableStateOf("") }
    var scheduledSearchQuery by remember { mutableStateOf("") }
    var deleteTimerIndex by remember { mutableStateOf<Int?>(null) }
    var deleteSchedule by remember { mutableStateOf<ScheduledTimerEntity?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var editorDraft by remember { mutableStateOf<ScheduledTimerDraft?>(null) }
    var showDurationPicker by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmsAvailable = ScheduledTimerManager.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    deleteTimerIndex?.let { idx ->
        val rawName = appState.timers.getOrNull(idx)?.activeTimerName.orEmpty()
        val timerName = rawName.ifBlank { stringResource(R.string.timer_delete_default_name, idx + 1) }
        AlertDialog(
            onDismissRequest = { deleteTimerIndex = null },
            title = { Text(stringResource(R.string.timer_delete_title, timerName)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTimer(idx)
                    deleteTimerIndex = null
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTimerIndex = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteSchedule?.let { schedule ->
        AlertDialog(
            onDismissRequest = { deleteSchedule = null },
            title = { Text("Delete scheduled timer?") },
            text = { Text(schedule.name.ifBlank { "This scheduled timer" }) },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        ScheduledTimerManager.deleteSchedule(context, schedule)
                        deleteSchedule = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteSchedule = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.delete_all_timers)) },
            text = { Text(stringResource(R.string.delete_all_timers_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAllTimers()
                    showDeleteAllConfirm = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDurationPicker && editorDraft != null) {
        DurationPickerSheet(
            initialMillis = editorDraft?.durationMillis ?: 0L,
            onDurationSet = { durationMillis ->
                editorDraft = editorDraft?.copy(durationMillis = durationMillis)
            },
            onDismiss = { showDurationPicker = false },
        )
    }

    editorDraft?.let { draft ->
        ScheduledTimerEditorSheet(
            draft = draft,
            presets = presets,
            exactAlarmsAvailable = exactAlarmsAvailable,
            onOpenExactAlarmSettings = {
                context.startActivity(ScheduledTimerManager.exactAlarmSettingsIntent(context))
            },
            onUpdateDraft = { editorDraft = it },
            onPickDate = { current ->
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        editorDraft = editorDraft?.copy(oneTimeDate = LocalDate.of(year, month + 1, dayOfMonth))
                    },
                    current.year,
                    current.monthValue - 1,
                    current.dayOfMonth,
                ).show()
            },
            onPickStartTime = { current ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        editorDraft = editorDraft?.copy(startTime = LocalTime.of(hourOfDay, minute))
                    },
                    current.hour,
                    current.minute,
                    false,
                ).show()
            },
            onPickEndTime = { current ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        editorDraft = editorDraft?.copy(endTime = LocalTime.of(hourOfDay, minute))
                    },
                    current.hour,
                    current.minute,
                    false,
                ).show()
            },
            onOpenDurationPicker = { showDurationPicker = true },
            onSave = { entity ->
                coroutineScope.launch {
                    ScheduledTimerManager.upsertSchedule(context, entity)
                    editorDraft = null
                }
            },
            onDismiss = { editorDraft = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == TimerSheetTab.Active,
                onClick = { selectedTab = TimerSheetTab.Active },
                text = { Text("Active Timers") },
            )
            Tab(
                selected = selectedTab == TimerSheetTab.Scheduled,
                onClick = { selectedTab = TimerSheetTab.Scheduled },
                text = { Text("Scheduled Timers") },
            )
        }

        when (selectedTab) {
            TimerSheetTab.Active -> {
                val filteredIndices = appState.timers.indices.filter { index ->
                    val timer = appState.timers[index]
                    val name = timer.activeTimerName.ifBlank { "Timer ${index + 1}" }
                    activeSearchQuery.isBlank() || name.contains(activeSearchQuery, ignoreCase = true)
                }

                OutlinedTextField(
                    value = activeSearchQuery,
                    onValueChange = { activeSearchQuery = it },
                    label = { Text(stringResource(R.string.search_timers)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (appState.timers.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { showDeleteAllConfirm = true }) {
                            Text(stringResource(R.string.delete_all_timers))
                        }
                        TextButton(onClick = onDeleteNonRunningTimers) {
                            Text(stringResource(R.string.delete_non_running_timers))
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items = filteredIndices,
                        key = { _, realIndex -> appState.timers[realIndex].id },
                    ) { _, realIndex ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd && appState.timers.size > 1) {
                                    if (confirmSwipeDelete) {
                                        deleteTimerIndex = realIndex
                                        false
                                    } else {
                                        onDeleteTimer(realIndex)
                                        false
                                    }
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = appState.timers.size > 1,
                            enableDismissFromEndToStart = false,
                            backgroundContent = {
                                DeleteBackground()
                            },
                        ) {
                            TimerRow(
                                timer = appState.timers[realIndex],
                                timerIndex = realIndex,
                                isOledMode = appState.isOledMode,
                                onClick = {
                                    onNavigateToTimer(realIndex)
                                    onDismiss()
                                },
                                onLongPress = { if (appState.timers.size > 1) deleteTimerIndex = realIndex },
                            )
                        }
                    }
                }
            }

            TimerSheetTab.Scheduled -> {
                val filteredSchedules = schedules.filter { schedule ->
                    val presetName = presetMap[schedule.presetId]?.name.orEmpty()
                    val query = scheduledSearchQuery.trim()
                    query.isBlank() ||
                        schedule.name.contains(query, ignoreCase = true) ||
                        presetName.contains(query, ignoreCase = true)
                }

                OutlinedTextField(
                    value = scheduledSearchQuery,
                    onValueChange = { scheduledSearchQuery = it },
                    label = { Text("Search scheduled timers") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (!exactAlarmsAvailable) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Exact alarm access is required for scheduled timers.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            OutlinedButton(onClick = {
                                context.startActivity(ScheduledTimerManager.exactAlarmSettingsIntent(context))
                            }) {
                                Text("Open exact alarm settings")
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { editorDraft = ScheduledTimerDraft() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("New Scheduled Timer")
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items = filteredSchedules,
                        key = { _, schedule -> schedule.id },
                    ) { _, schedule ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                    deleteSchedule = schedule
                                    false
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = false,
                            backgroundContent = { DeleteBackground() },
                        ) {
                            ScheduledTimerRow(
                                schedule = schedule,
                                preset = presetMap[schedule.presetId],
                                isRunning = appState.timers.any { it.scheduleId == schedule.id && it.status != TimerStatus.Idle },
                                exactAlarmsAvailable = exactAlarmsAvailable,
                                onClick = { editorDraft = schedule.toDraft() },
                                onLongPress = { deleteSchedule = schedule },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledTimerEditorSheet(
    draft: ScheduledTimerDraft,
    presets: List<PresetEntity>,
    exactAlarmsAvailable: Boolean,
    onOpenExactAlarmSettings: () -> Unit,
    onUpdateDraft: (ScheduledTimerDraft) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onPickStartTime: (LocalTime) -> Unit,
    onPickEndTime: (LocalTime) -> Unit,
    onOpenDurationPicker: () -> Unit,
    onSave: (ScheduledTimerEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPresetPicker by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val selectedPreset = presets.firstOrNull { it.id == draft.presetId }
    val durationPreview = draft.toEntityOrNull()?.let(ScheduledTimerManager::computeLaunchDurationMillis)
    val startTimeError = when {
        draft.type == ScheduledTimerType.OneTime && draft.isStartTimeInPast ->
            "Start time has already passed. Select a later start time."
        else -> null
    }

    LaunchedEffect(draft.type, draft.presetId) {
        validationMessage = null
    }

    if (showPresetPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPresetPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Choose preset",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (presets.isEmpty()) {
                    Text(
                        text = "No presets yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(presets, key = { _, preset -> preset.id }) { _, preset ->
                            Surface(
                                onClick = {
                                    val seededDuration = preset.durationMillis.coerceIn(1L, MAX_DURATION_MILLIS)
                                    val updatedDraft = when (draft.type) {
                                        ScheduledTimerType.Repeating -> {
                                            draft.copy(
                                                presetId = preset.id,
                                                durationMillis = seededDuration,
                                                timingMode = ScheduledTimerTimingMode.Duration,
                                                endTime = draft.startTime.plusSeconds(seededDuration / 1000L),
                                            )
                                        }
                                        ScheduledTimerType.OneTime -> draft.copy(presetId = preset.id)
                                    }
                                    onUpdateDraft(updatedDraft)
                                    showPresetPicker = false
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = preset.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = preset.durationMillis.formatClockTime(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { showPresetPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (draft.id == 0L) "New Scheduled Timer" else "Edit Scheduled Timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (draft.type == ScheduledTimerType.Repeating || selectedPreset != null) {
                OutlinedButton(onClick = { showPresetPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedPreset?.name ?: "Choose preset")
                }
            } else {
                OutlinedButton(onClick = { showPresetPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Optional preset")
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onUpdateDraft(draft.copy(name = it)) },
                label = { Text("Custom name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ToggleRow(
                title = "Schedule type",
                leftLabel = "One-time",
                rightLabel = "Repeating",
                leftSelected = draft.type == ScheduledTimerType.OneTime,
                onSelectLeft = {
                    onUpdateDraft(draft.copy(type = ScheduledTimerType.OneTime))
                },
                onSelectRight = {
                    onUpdateDraft(
                        draft.copy(
                            type = ScheduledTimerType.Repeating,
                            presetId = draft.presetId,
                        ),
                    )
                },
            )

            if (draft.type == ScheduledTimerType.OneTime) {
                OutlinedButton(onClick = { onPickDate(draft.oneTimeDate) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: ${draft.oneTimeDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))}")
                }
            } else {
                WeekdayPicker(
                    selectedDays = draft.weekdays,
                    onToggle = { day ->
                        val updated = if (draft.weekdays.contains(day)) draft.weekdays - day else draft.weekdays + day
                        onUpdateDraft(draft.copy(weekdays = updated))
                    },
                )
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    1.dp,
                    if (startTimeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPickStartTime(draft.startTime) },
            ) {
                Text(
                    text = "Start time: ${draft.startTime.format(timeFormatter())}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    color = if (startTimeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            startTimeError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            ToggleRow(
                title = "Length mode",
                leftLabel = "Duration",
                rightLabel = "End time",
                leftSelected = draft.timingMode == ScheduledTimerTimingMode.Duration,
                onSelectLeft = { onUpdateDraft(draft.copy(timingMode = ScheduledTimerTimingMode.Duration)) },
                onSelectRight = { onUpdateDraft(draft.copy(timingMode = ScheduledTimerTimingMode.EndTime)) },
            )

            if (draft.timingMode == ScheduledTimerTimingMode.Duration) {
                OutlinedButton(onClick = onOpenDurationPicker, modifier = Modifier.fillMaxWidth()) {
                    Text("Duration: ${draft.durationMillis.formatClockTime()}")
                }
            } else {
                OutlinedButton(onClick = { onPickEndTime(draft.endTime) }, modifier = Modifier.fillMaxWidth()) {
                    Text("End time: ${draft.endTime.format(timeFormatter())}")
                }
            }

            if (draft.type == ScheduledTimerType.Repeating && draft.presetId == null) {
                Text(
                    text = "Choose a preset for repeating schedules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!exactAlarmsAvailable) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Exact alarm access is required before this schedule can be saved.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        OutlinedButton(onClick = onOpenExactAlarmSettings) {
                            Text("Open exact alarm settings")
                        }
                    }
                }
            }

            durationPreview?.let {
                Text(
                    text = "This launch will run for ${it.formatClockTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            validationMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    val validationError = validateDraft(draft, exactAlarmsAvailable)
                    validationMessage = validationError
                    if (validationError == null) {
                        onSave(draft.toEntity())
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduledTimerRow(
    schedule: ScheduledTimerEntity,
    preset: PresetEntity?,
    isRunning: Boolean,
    exactAlarmsAvailable: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val nextOccurrence = ScheduledTimerManager
        .nextOccurrenceMillis(schedule)
        ?.takeIf { exactAlarmsAvailable && ScheduledTimerManager.shouldKeepAlarmArmed(schedule, presetExists = preset != null) }
    val status = when {
        isRunning -> ScheduledStatus.Running
        ScheduledTimerManager.scheduleRequiresPreset(schedule) &&
            (preset == null || schedule.scheduleOutcome() == ScheduledTimerOutcome.MissingPreset) -> ScheduledStatus.MissingPreset
        schedule.scheduleOutcome() == ScheduledTimerOutcome.MissedCapacity -> ScheduledStatus.Missed
        else -> ScheduledStatus.Scheduled
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = schedule.name.ifBlank { preset?.name ?: "Scheduled timer" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ScheduledStatusBadge(status)
            }

            Text(
                text = "Preset: ${preset?.name ?: "None"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append(
                        when (schedule.scheduleType()) {
                            ScheduledTimerType.OneTime -> {
                                val date = LocalDate.ofEpochDay(schedule.oneTimeDateEpochDay ?: LocalDate.now().toEpochDay())
                                date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
                            }
                            ScheduledTimerType.Repeating -> ScheduledTimerManager.weekdaySummary(schedule.weekdayMask)
                        },
                    )
                    append(" at ")
                    append(ScheduledTimerManager.minutesToLocalTime(schedule.startTimeMinutes).format(timeFormatter()))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when (schedule.scheduleTimingMode()) {
                    ScheduledTimerTimingMode.Duration -> "Duration: ${(schedule.durationMillis ?: 0L).formatClockTime()}"
                    ScheduledTimerTimingMode.EndTime -> {
                        val endMinutes = schedule.endTimeMinutes ?: 0
                        "Ends at ${ScheduledTimerManager.minutesToLocalTime(endMinutes).format(timeFormatter())}"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (nextOccurrence != null) {
                Text(
                    text = "Next: ${Instant.ofEpochMilli(nextOccurrence).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, MMM d h:mm a", Locale.getDefault()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun WeekdayPicker(
    selectedDays: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Repeat on",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                val selected = selectedDays.contains(day)
                Surface(
                    onClick = { onToggle(day) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = day.name.take(3).lowercase().replaceFirstChar(Char::titlecase),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(label = leftLabel, selected = leftSelected, onClick = onSelectLeft)
            SelectChip(label = rightLabel, selected = !leftSelected, onClick = onSelectRight)
        }
    }
}

@Composable
private fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large,
            )
            .padding(start = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Delete",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerRow(
    timer: TimerInstance,
    timerIndex: Int,
    isOledMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${timerIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = TextAlign.Center,
            )

            val bucket = progressBucket(timer)
            key(bucket) {
                Box(modifier = Modifier.size(48.dp)) {
                    VisualTimerCanvas(
                        timer = timer,
                        onDurationSelected = {},
                        modifier = Modifier.fillMaxSize(),
                        isOledMode = isOledMode,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val name = timer.activeTimerName.ifBlank {
                    stringResource(R.string.timer_delete_default_name, timerIndex + 1)
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(status = timer.status)
                    if (timer.isScheduledLaunch) {
                        ScheduledStatusBadge(ScheduledStatus.Scheduled)
                    }
                    Text(
                        text = timer.displayMillis.formatClockTime(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (timer.status == TimerStatus.Running || timer.status == TimerStatus.Paused) {
                    val finishEpoch = System.currentTimeMillis() + timer.displayMillis
                    Text(
                        text = stringResource(R.string.finishes_at, finishEpoch.toLocalTimeString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TimerStatus) {
    val label = when (status) {
        TimerStatus.Idle -> stringResource(R.string.status_ready)
        TimerStatus.Running -> stringResource(R.string.status_running)
        TimerStatus.Paused -> stringResource(R.string.status_paused)
        TimerStatus.Overtime -> stringResource(R.string.status_overtime)
        TimerStatus.Finished -> stringResource(R.string.status_finished)
    }
    val color = when (status) {
        TimerStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        TimerStatus.Running -> MaterialTheme.colorScheme.primary
        TimerStatus.Paused -> MaterialTheme.colorScheme.secondary
        TimerStatus.Overtime -> MaterialTheme.colorScheme.error
        TimerStatus.Finished -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ScheduledStatusBadge(status: ScheduledStatus) {
    val label = when (status) {
        ScheduledStatus.Scheduled -> "Scheduled"
        ScheduledStatus.Running -> "Running"
        ScheduledStatus.Missed -> "Missed"
        ScheduledStatus.MissingPreset -> "Missing preset"
    }
    val color = when (status) {
        ScheduledStatus.Scheduled -> MaterialTheme.colorScheme.tertiary
        ScheduledStatus.Running -> MaterialTheme.colorScheme.primary
        ScheduledStatus.Missed -> MaterialTheme.colorScheme.error
        ScheduledStatus.MissingPreset -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun progressBucket(timer: TimerInstance): Int {
    if (timer.originalDurationMillis <= 0L) return 0
    if (timer.status == TimerStatus.Overtime || timer.status == TimerStatus.Finished) return 16
    val elapsed = timer.originalDurationMillis - timer.remainingMillis
    return ((elapsed.toFloat() / timer.originalDurationMillis) * 16).toInt().coerceIn(0, 16)
}

private fun Long.toLocalTimeString(): String {
    val localTime = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    return timeFormatter().format(localTime)
}

private fun timeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun ScheduledTimerEntity.toDraft(): ScheduledTimerDraft {
    val startTime = ScheduledTimerManager.minutesToLocalTime(startTimeMinutes)
    val endTime = ScheduledTimerManager.minutesToLocalTime(endTimeMinutes ?: startTimeMinutes)
    return ScheduledTimerDraft(
        id = id,
        name = name,
        presetId = presetId,
        type = scheduleType(),
        oneTimeDate = LocalDate.ofEpochDay(oneTimeDateEpochDay ?: LocalDate.now().toEpochDay()),
        weekdays = weekdayMask.toWeekdaySet(),
        startTime = startTime,
        timingMode = scheduleTimingMode(),
        durationMillis = durationMillis ?: 0L,
        endTime = endTime,
    )
}

private fun ScheduledTimerDraft.toEntityOrNull(): ScheduledTimerEntity? {
    return ScheduledTimerEntity(
        id = id,
        name = name,
        presetId = presetId,
        type = type.name,
        oneTimeDateEpochDay = if (type == ScheduledTimerType.OneTime) oneTimeDate.toEpochDay() else null,
        weekdayMask = if (type == ScheduledTimerType.Repeating) weekdayMaskFor(weekdays) else 0,
        startTimeMinutes = startTime.hour * 60 + startTime.minute,
        timingMode = timingMode.name,
        durationMillis = if (timingMode == ScheduledTimerTimingMode.Duration) durationMillis else null,
        endTimeMinutes = if (timingMode == ScheduledTimerTimingMode.EndTime) endTime.hour * 60 + endTime.minute else null,
        lastOutcome = ScheduledTimerOutcome.None.name,
        lastOutcomeAtMillis = null,
    )
}

private fun validateDraft(
    draft: ScheduledTimerDraft,
    exactAlarmsAvailable: Boolean,
): String? {
    if (!exactAlarmsAvailable) {
        return "Exact alarm access is required."
    }
    if (draft.requiresPreset && draft.presetId == null) {
        return "Choose a preset."
    }
    if (draft.type == ScheduledTimerType.OneTime && draft.isStartTimeInPast) {
        return "Start time has already passed. Select a later start time."
    }
    if (draft.type == ScheduledTimerType.Repeating && draft.weekdays.isEmpty()) {
        return "Choose at least one weekday."
    }
    val durationMillis = draft.toEntityOrNull()?.let(ScheduledTimerManager::computeLaunchDurationMillis)
        ?: return "Choose a valid duration."
    if (durationMillis <= 0L || durationMillis > MAX_DURATION_MILLIS) {
        return "Duration must be greater than 0 and within the timer limit."
    }
    return null
}
