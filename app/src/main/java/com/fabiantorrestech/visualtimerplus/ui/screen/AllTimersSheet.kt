package com.fabiantorrestech.visualtimerplus.ui.screen

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.component.VisualTimerCanvas
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTimersSheet(
    appState: AppState,
    onNavigateToTimer: (Int) -> Unit,
    onDeleteTimer: (Int) -> Unit,
    onDeleteAllTimers: () -> Unit,
    onDeleteNonRunningTimers: () -> Unit,
    onDismiss: () -> Unit,
    confirmSwipeDelete: Boolean = true,
) {
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteMenu by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Per-timer delete confirmation
    deleteIndex?.let { idx ->
        val rawName = appState.timers.getOrNull(idx)?.activeTimerName.orEmpty()
        val timerName = rawName.ifBlank {
            stringResource(R.string.timer_delete_default_name, idx + 1)
        }
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text(stringResource(R.string.timer_delete_title, timerName)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTimer(idx)
                    deleteIndex = null
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteIndex = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Step 1: choose delete mode
    if (showDeleteMenu) {
        AlertDialog(
            onDismissRequest = { showDeleteMenu = false },
            title = { Text(stringResource(R.string.delete_timers)) },
            text = null,
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showDeleteMenu = false
                            showDeleteAllConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.delete_all_timers),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = {
                            onDeleteNonRunningTimers()
                            showDeleteMenu = false
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete_non_running_timers))
                    }
                    TextButton(
                        onClick = { showDeleteMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
            dismissButton = null,
        )
    }

    // Step 2: extra confirmation before deleting all
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.delete_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAllTimers()
                    showDeleteAllConfirm = false
                    onDismiss()
                }) {
                    Text(
                        text = stringResource(R.string.delete_all_confirm_yes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // fillMaxHeight so the sheet always pulls to the top of the screen
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.all_timers_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showDeleteMenu = true }) {
                Text(
                    text = stringResource(R.string.delete_timers),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.all_timers_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        val filteredIndices = appState.timers.indices.filter { i ->
            val timer = appState.timers[i]
            if (searchQuery.isBlank()) true
            else timer.activeTimerName.contains(searchQuery, ignoreCase = true)
        }

        if (filteredIndices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.all_timers_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = filteredIndices,
                    key = { _, realIndex -> appState.timers[realIndex].id },
                ) { _, realIndex ->
                    val timer = appState.timers[realIndex]
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd && appState.timers.size > 1) {
                                if (confirmSwipeDelete) {
                                    deleteIndex = realIndex
                                    false
                                } else {
                                    onDeleteTimer(realIndex)
                                    false
                                }
                            } else false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = appState.timers.size > 1,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
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
                        },
                    ) {
                        TimerRow(
                            timer = timer,
                            timerIndex = realIndex,
                            isOledMode = appState.isOledMode,
                            onClick = {
                                onNavigateToTimer(realIndex)
                                onDismiss()
                            },
                            onLongPress = { if (appState.timers.size > 1) deleteIndex = realIndex },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
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
    return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(localTime)
}
