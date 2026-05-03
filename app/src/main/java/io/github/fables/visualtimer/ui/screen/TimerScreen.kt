package com.fabiantorrestech.visualtimerplus.ui.screen

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.ClockPosition
import com.fabiantorrestech.visualtimerplus.timer.ClockTextSize
import com.fabiantorrestech.visualtimerplus.timer.FinishedVibrationMode
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerState
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.component.PresetRow
import com.fabiantorrestech.visualtimerplus.ui.component.QuickAdjustRow
import com.fabiantorrestech.visualtimerplus.ui.component.TimerControls
import com.fabiantorrestech.visualtimerplus.ui.component.VisualTimerCanvas
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    stateFlow: StateFlow<TimerState>,
    onAction: (TimerAction) -> Unit,
    onToggleOledMode: (Boolean) -> Unit,
    onNotificationPermissionNeeded: () -> Unit,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCleanModeActive = state.cleanModeEnabled && state.status == TimerStatus.Running
    val isStatusBarHiddenNow = state.hideStatusBarEnabled &&
        (!state.hideStatusBarOnlyWhenRunning || state.status == TimerStatus.Running)
    val showTopClock = isStatusBarHiddenNow && state.showCurrentTimeEnabled
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var cleanModeUiAwake by rememberSaveable { mutableStateOf(false) }
    var cleanModeControlsExpanded by rememberSaveable { mutableStateOf(false) }
    var cleanModeActivityTick by rememberSaveable { mutableStateOf(0) }
    val minimalUiAlpha by animateFloatAsState(
        targetValue = if (isCleanModeActive && !cleanModeUiAwake) 0f else 1f,
        label = "minimalUiAlpha",
    )

    fun awakenMinimalUi() {
        if (!isCleanModeActive) return
        cleanModeUiAwake = true
        cleanModeActivityTick += 1
    }

    LaunchedEffect(state.status) {
        if (state.status == TimerStatus.Running) {
            onNotificationPermissionNeeded()
        }
    }

    LaunchedEffect(isCleanModeActive) {
        if (!isCleanModeActive) {
            cleanModeUiAwake = false
            cleanModeControlsExpanded = false
        } else {
            cleanModeUiAwake = true
            cleanModeControlsExpanded = false
            cleanModeActivityTick += 1
        }
    }

    LaunchedEffect(isCleanModeActive, cleanModeActivityTick) {
        if (!isCleanModeActive || !cleanModeUiAwake) return@LaunchedEffect
        delay(5_000L)
        cleanModeUiAwake = false
        cleanModeControlsExpanded = false
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            SettingsSheetContent(
                state = state,
                onAction = onAction,
                onToggleOledMode = onToggleOledMode,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (state.isOledMode) {
                    Modifier.background(MaterialTheme.colorScheme.background)
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    )
                },
            )
            .pointerInput(isCleanModeActive) {
                if (!isCleanModeActive) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    awakenMinimalUi()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTopClock) {
                Box(
                    modifier = Modifier.alpha(
                        if (isCleanModeActive && state.hideClockInCleanMode) minimalUiAlpha else 1f,
                    ),
                ) {
                    CurrentTimeText(
                        showSeconds = state.showClockSecondsEnabled,
                        clockPosition = state.clockPosition,
                        clockTextSize = state.clockTextSize,
                        isLandscape = isLandscape,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoBadge(
                    text = statusLabel(state.status),
                    modifier = Modifier.weight(1f),
                )
                InfoBadge(
                    text = state.selectedDurationMillis.formatClockTime(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            HeroTimerCard(
                state = state,
                displayAlpha = if (isCleanModeActive) minimalUiAlpha else 1f,
                onDurationSelected = { durationMillis ->
                    onAction(TimerAction.SetDuration(durationMillis))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!isCleanModeActive) {
                SectionCard(title = stringResource(R.string.presets)) {
                    PresetRow(
                        modifier = Modifier.fillMaxWidth(),
                        onPresetSelected = { durationMillis ->
                            onAction(TimerAction.SetDuration(durationMillis))
                        },
                        enabled = state.status != TimerStatus.Running,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SectionCard(title = stringResource(R.string.adjust_timer)) {
                    QuickAdjustRow(
                        modifier = Modifier.fillMaxWidth(),
                        onAdjust = { deltaMinutes ->
                            onAction(TimerAction.AdjustDuration(deltaMinutes * 60_000L))
                        },
                        enabled = true,
                    )
                }
            } else {
                CleanModeQuickAdjust(
                    controlsExpanded = cleanModeControlsExpanded,
                    controlsAlpha = minimalUiAlpha,
                    onExpand = {
                        awakenMinimalUi()
                        cleanModeControlsExpanded = true
                    },
                    onAdjust = { deltaMinutes ->
                        onAction(TimerAction.AdjustDuration(deltaMinutes * 60_000L))
                        awakenMinimalUi()
                        cleanModeControlsExpanded = false
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard(
                modifier = Modifier.alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
            ) {
                TimerControls(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = {
                    awakenMinimalUi()
                    showSettingsSheet = true
                },
                label = { Text(text = stringResource(R.string.settings)) },
                modifier = Modifier
                    .align(Alignment.End)
                    .alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
                shape = RoundedCornerShape(22.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun HeroTimerCard(
    state: TimerState,
    displayAlpha: Float,
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        VisualTimerCanvas(
            modifier = Modifier.fillMaxSize(),
            state = state,
            onDurationSelected = onDurationSelected,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.displayMillis.formatClockTime(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusLabel(state.status),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(displayAlpha),
            )
        }
    }
}

@Composable
private fun statusLabel(status: TimerStatus): String = when (status) {
    TimerStatus.Idle -> stringResource(R.string.status_ready)
    TimerStatus.Running -> stringResource(R.string.status_running)
    TimerStatus.Paused -> stringResource(R.string.status_paused)
    TimerStatus.Finished -> stringResource(R.string.status_finished)
}

@Composable
private fun SettingsSheetContent(
    state: TimerState,
    onAction: (TimerAction) -> Unit,
    onToggleOledMode: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SectionCard {
            ThemeModeSelector(
                selectedMode = state.themeMode,
                onModeSelected = { onAction(TimerAction.SetThemeMode(it)) },
            )
            if (state.themeMode != ThemeMode.Light) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.oled_black),
                    checked = state.isOledMode,
                    onCheckedChange = onToggleOledMode,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.sound),
                checked = state.soundEnabled,
                onCheckedChange = { onAction(TimerAction.SetSoundEnabled(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            FinishedVibrationSelector(
                selectedMode = state.finishedVibrationMode,
                onModeSelected = { onAction(TimerAction.SetFinishedVibrationMode(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.keep_screen_awake),
                checked = state.keepScreenAwakeEnabled,
                onCheckedChange = { onAction(TimerAction.SetKeepScreenAwakeEnabled(it)) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SectionCard {
            PreferenceToggle(
                label = stringResource(R.string.hide_status_bar),
                checked = state.hideStatusBarEnabled,
                onCheckedChange = { onAction(TimerAction.SetHideStatusBarEnabled(it)) },
            )
            if (state.hideStatusBarEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.hide_status_bar_only_while_running),
                    checked = state.hideStatusBarOnlyWhenRunning,
                    onCheckedChange = { onAction(TimerAction.SetHideStatusBarOnlyWhenRunning(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.show_current_time),
                    checked = state.showCurrentTimeEnabled,
                    onCheckedChange = { onAction(TimerAction.SetShowCurrentTimeEnabled(it)) },
                )
                if (state.showCurrentTimeEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PreferenceToggle(
                        label = stringResource(R.string.show_seconds),
                        checked = state.showClockSecondsEnabled,
                        onCheckedChange = { onAction(TimerAction.SetShowClockSecondsEnabled(it)) },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ClockPositionSelector(
                        selectedPosition = state.clockPosition,
                        onPositionSelected = { onAction(TimerAction.SetClockPosition(it)) },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ClockTextSizeSelector(
                        selectedSize = state.clockTextSize,
                        onSizeSelected = { onAction(TimerAction.SetClockTextSize(it)) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SectionCard {
            PreferenceToggle(
                label = stringResource(R.string.clockwise_mode),
                checked = state.clockwiseModeEnabled,
                onCheckedChange = { onAction(TimerAction.SetClockwiseModeEnabled(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.clean_mode),
                checked = state.cleanModeEnabled,
                onCheckedChange = { onAction(TimerAction.SetCleanModeEnabled(it)) },
            )
            if (state.cleanModeEnabled && state.showCurrentTimeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.hide_clock_in_minimal_mode),
                    checked = state.hideClockInCleanMode,
                    onCheckedChange = { onAction(TimerAction.SetHideClockInCleanMode(it)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CleanModeQuickAdjust(
    controlsExpanded: Boolean,
    controlsAlpha: Float,
    onExpand: () -> Unit,
    onAdjust: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (controlsExpanded && controlsAlpha > 0f) {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                QuickAdjustRow(
                    modifier = Modifier.fillMaxWidth(),
                    onAdjust = onAdjust,
                    enabled = true,
                )
            }
        } else {
            Surface(
                onClick = onExpand,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.alpha(controlsAlpha),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun FinishedVibrationSelector(
    selectedMode: FinishedVibrationMode,
    onModeSelected: (FinishedVibrationMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.finished_vibration),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FinishedVibrationChip(
                label = stringResource(R.string.finished_vibration_off),
                selected = selectedMode == FinishedVibrationMode.Off,
                onClick = { onModeSelected(FinishedVibrationMode.Off) },
            )
            FinishedVibrationChip(
                label = stringResource(R.string.finished_vibration_1m),
                selected = selectedMode == FinishedVibrationMode.OneMinute,
                onClick = { onModeSelected(FinishedVibrationMode.OneMinute) },
            )
            FinishedVibrationChip(
                label = stringResource(R.string.finished_vibration_2m),
                selected = selectedMode == FinishedVibrationMode.TwoMinutes,
                onClick = { onModeSelected(FinishedVibrationMode.TwoMinutes) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FinishedVibrationChip(
                label = stringResource(R.string.finished_vibration_5m),
                selected = selectedMode == FinishedVibrationMode.FiveMinutes,
                onClick = { onModeSelected(FinishedVibrationMode.FiveMinutes) },
            )
            FinishedVibrationChip(
                label = stringResource(R.string.finished_vibration_10m),
                selected = selectedMode == FinishedVibrationMode.TenMinutes,
                onClick = { onModeSelected(FinishedVibrationMode.TenMinutes) },
            )
            FinishedVibrationChip(
                label = stringResource(R.string.finished_vibration_forever),
                selected = selectedMode == FinishedVibrationMode.Forever,
                onClick = { onModeSelected(FinishedVibrationMode.Forever) },
            )
        }
    }
}

@Composable
private fun RowScope.FinishedVibrationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            },
            labelColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        },
    )
}

@Composable
private fun ClockPositionSelector(
    selectedPosition: ClockPosition,
    onPositionSelected: (ClockPosition) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.clock_position),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClockPositionChip(
                label = stringResource(R.string.clock_left),
                selected = selectedPosition == ClockPosition.Left,
                onClick = { onPositionSelected(ClockPosition.Left) },
            )
            ClockPositionChip(
                label = stringResource(R.string.clock_center),
                selected = selectedPosition == ClockPosition.Center,
                onClick = { onPositionSelected(ClockPosition.Center) },
            )
            ClockPositionChip(
                label = stringResource(R.string.clock_right),
                selected = selectedPosition == ClockPosition.Right,
                onClick = { onPositionSelected(ClockPosition.Right) },
            )
        }
    }
}

@Composable
private fun RowScope.ClockPositionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            },
            labelColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        },
    )
}

@Composable
private fun ClockTextSizeSelector(
    selectedSize: ClockTextSize,
    onSizeSelected: (ClockTextSize) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.clock_size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClockTextSizeChip(
                label = stringResource(R.string.clock_size_small),
                selected = selectedSize == ClockTextSize.Small,
                onClick = { onSizeSelected(ClockTextSize.Small) },
            )
            ClockTextSizeChip(
                label = stringResource(R.string.clock_size_medium),
                selected = selectedSize == ClockTextSize.Medium,
                onClick = { onSizeSelected(ClockTextSize.Medium) },
            )
            ClockTextSizeChip(
                label = stringResource(R.string.clock_size_large),
                selected = selectedSize == ClockTextSize.Large,
                onClick = { onSizeSelected(ClockTextSize.Large) },
            )
        }
    }
}

@Composable
private fun RowScope.ClockTextSizeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            },
            labelColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        },
    )
}

@Composable
private fun ThemeModeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.theme_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.theme_light) to ThemeMode.Light,
                stringResource(R.string.theme_dark) to ThemeMode.Dark,
                stringResource(R.string.theme_system) to ThemeMode.System,
            ).forEach { (label, mode) ->
                val selected = selectedMode == mode
                AssistChip(
                    onClick = { onModeSelected(mode) },
                    label = {
                        Text(
                            text = label,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
                        },
                        labelColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                    border = if (selected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun InfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CurrentTimeText(
    showSeconds: Boolean,
    clockPosition: ClockPosition,
    clockTextSize: ClockTextSize,
    isLandscape: Boolean,
) {
    val formatter = if (showSeconds) {
        rememberClockFormatter("h:mm:ss a")
    } else {
        rememberClockFormatter("h:mm a")
    }
    var currentTimeText by remember(showSeconds) {
        mutableStateOf(LocalTime.now().format(formatter))
    }

    LaunchedEffect(showSeconds) {
        while (true) {
            currentTimeText = LocalTime.now().format(formatter)
            delay(if (showSeconds) 1_000L else 15_000L)
        }
    }

    Text(
        text = currentTimeText,
        style = clockTextStyle(
            clockTextSize = clockTextSize,
            isLandscape = isLandscape,
        ),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = if (isLandscape) 8.dp else 34.dp),
        textAlign = when (clockPosition) {
            ClockPosition.Left -> TextAlign.Start
            ClockPosition.Center -> TextAlign.Center
            ClockPosition.Right -> TextAlign.End
        },
    )
}

private fun rememberClockFormatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

@Composable
private fun clockTextStyle(
    clockTextSize: ClockTextSize,
    isLandscape: Boolean,
) = when (clockTextSize) {
    ClockTextSize.Small -> {
        if (isLandscape) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium
    }

    ClockTextSize.Medium -> {
        if (isLandscape) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge
    }

    ClockTextSize.Large -> {
        if (isLandscape) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall
    }
}
