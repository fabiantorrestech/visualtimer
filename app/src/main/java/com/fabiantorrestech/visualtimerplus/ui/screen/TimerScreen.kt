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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.timer.ClockPosition
import com.fabiantorrestech.visualtimerplus.timer.ClockTextSize
import com.fabiantorrestech.visualtimerplus.timer.FinishedSoundRoute
import com.fabiantorrestech.visualtimerplus.timer.FinishedVibrationMode
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerState
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.component.DurationPickerContent
import com.fabiantorrestech.visualtimerplus.ui.component.DurationPickerSheet
import com.fabiantorrestech.visualtimerplus.ui.component.PresetRow
import com.fabiantorrestech.visualtimerplus.ui.component.PresetsSheet
import com.fabiantorrestech.visualtimerplus.ui.component.QuickAdjustRow
import com.fabiantorrestech.visualtimerplus.ui.component.TimerControls
import com.fabiantorrestech.visualtimerplus.ui.component.VisualTimerCanvas
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    stateFlow: StateFlow<TimerState>,
    onAction: (TimerAction) -> Unit,
    onToggleOledMode: (Boolean) -> Unit,
    onNotificationPermissionNeeded: () -> Unit,
    onOpenLog: () -> Unit,
    db: AppDatabase,
    openPresetsOnLaunch: Boolean = false,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCleanModeActive = state.cleanModeEnabled && state.status == TimerStatus.Running
    val isStatusBarHiddenNow = state.hideStatusBarEnabled &&
        (!state.hideStatusBarOnlyWhenRunning || state.status == TimerStatus.Running)
    val showTopClock = isStatusBarHiddenNow && state.showCurrentTimeEnabled

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showPresetsSheet by rememberSaveable { mutableStateOf(openPresetsOnLaunch) }
    var showDurationPicker by rememberSaveable { mutableStateOf(false) }
    var showDefaultDurationPicker by rememberSaveable { mutableStateOf(false) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showStartNamePrompt by rememberSaveable { mutableStateOf(false) }

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
        if (state.status == TimerStatus.Running) onNotificationPermissionNeeded()
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

    // Duration picker (tap canvas center when idle)
    if (showDurationPicker) {
        DurationPickerSheet(
            initialMillis = state.selectedDurationMillis,
            onDurationSet = { millis ->
                onAction(TimerAction.SetDurationExact(millis))
                showDurationPicker = false
            },
            onDismiss = { showDurationPicker = false },
        )
    }

    // Default duration picker (from settings)
    if (showDefaultDurationPicker) {
        DurationPickerSheet(
            initialMillis = state.defaultDurationMillis,
            onDurationSet = { millis ->
                onAction(TimerAction.SetDefaultDuration(millis))
                showDefaultDurationPicker = false
            },
            onDismiss = { showDefaultDurationPicker = false },
        )
    }

    // Timer name dialog
    if (showNameDialog) {
        TimerNameDialog(
            currentName = state.activeTimerName,
            onConfirm = { name ->
                onAction(TimerAction.SetActiveTimerName(name))
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }

    // Prompt-before-start dialog
    if (showStartNamePrompt) {
        TimerNameDialog(
            currentName = state.activeTimerName,
            title = stringResource(R.string.name_prompt_title),
            confirmLabel = stringResource(R.string.start),
            onConfirm = { name ->
                onAction(TimerAction.SetActiveTimerName(name))
                showStartNamePrompt = false
                onAction(TimerAction.Start)
            },
            onDismiss = {
                showStartNamePrompt = false
                onAction(TimerAction.Start)
            },
            dismissLabel = stringResource(R.string.name_prompt_skip),
        )
    }

    // Presets sheet
    if (showPresetsSheet) {
        PresetsSheet(
            db = db,
            currentDurationMillis = state.selectedDurationMillis,
            onAction = onAction,
            onDismiss = { showPresetsSheet = false },
        )
    }

    // Settings sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            SettingsSheetContent(
                state = state,
                onAction = onAction,
                onToggleOledMode = onToggleOledMode,
                onSetDefaultDuration = { showDefaultDurationPicker = true },
            )
        }
    }

    val bgModifier = if (state.isOledMode) {
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
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
            .pointerInput(isCleanModeActive) {
                if (!isCleanModeActive) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    awakenMinimalUi()
                }
            },
    ) {
        if (isLandscape) {
            LandscapeLayout(
                state = state,
                isCleanModeActive = isCleanModeActive,
                showTopClock = showTopClock,
                minimalUiAlpha = minimalUiAlpha,
                cleanModeControlsExpanded = cleanModeControlsExpanded,
                onAction = onAction,
                onOpenSettings = { showSettingsSheet = true; awakenMinimalUi() },
                onOpenLog = onOpenLog,
                onOpenPresets = { showPresetsSheet = true },
                onOpenDurationPicker = { if (state.status == TimerStatus.Idle) showDurationPicker = true },
                onNameChipClick = {
                    if (state.status != TimerStatus.Running) showNameDialog = true
                },
                onClearPreset = {
                    onAction(TimerAction.SetActivePresetId(null))
                    onAction(TimerAction.SetActiveTimerName(""))
                },
                onStartWithPromptCheck = {
                    if (state.promptBeforeStart) showStartNamePrompt = true
                    else onAction(TimerAction.Start)
                },
                onAdjust = { onAction(TimerAction.AdjustDuration(it)) },
                awakenMinimalUi = ::awakenMinimalUi,
                onCleanModeExpand = {
                    awakenMinimalUi()
                    cleanModeControlsExpanded = true
                },
                onCleanModeAdjust = { delta ->
                    onAction(TimerAction.AdjustDuration(delta))
                    awakenMinimalUi()
                    cleanModeControlsExpanded = false
                },
            )
        } else {
            PortraitLayout(
                state = state,
                isCleanModeActive = isCleanModeActive,
                showTopClock = showTopClock,
                minimalUiAlpha = minimalUiAlpha,
                cleanModeControlsExpanded = cleanModeControlsExpanded,
                onAction = onAction,
                onOpenSettings = { showSettingsSheet = true; awakenMinimalUi() },
                onOpenLog = onOpenLog,
                onOpenPresets = { showPresetsSheet = true },
                onOpenDurationPicker = { if (state.status == TimerStatus.Idle) showDurationPicker = true },
                onNameChipClick = {
                    if (state.status != TimerStatus.Running) showNameDialog = true
                },
                onClearPreset = {
                    onAction(TimerAction.SetActivePresetId(null))
                    onAction(TimerAction.SetActiveTimerName(""))
                },
                onStartWithPromptCheck = {
                    if (state.promptBeforeStart) showStartNamePrompt = true
                    else onAction(TimerAction.Start)
                },
                onAdjust = { onAction(TimerAction.AdjustDuration(it)) },
                awakenMinimalUi = ::awakenMinimalUi,
                onCleanModeExpand = {
                    awakenMinimalUi()
                    cleanModeControlsExpanded = true
                },
                onCleanModeAdjust = { delta ->
                    onAction(TimerAction.AdjustDuration(delta))
                    awakenMinimalUi()
                    cleanModeControlsExpanded = false
                },
            )
        }

        // FAB — opens presets sheet (hidden in clean mode)
        if (!isCleanModeActive) {
            FloatingActionButton(
                onClick = { showPresetsSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.loweredElevation(),
            ) {
                Text(
                    text = "☰",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    state: TimerState,
    isCleanModeActive: Boolean,
    showTopClock: Boolean,
    minimalUiAlpha: Float,
    cleanModeControlsExpanded: Boolean,
    onAction: (TimerAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenDurationPicker: () -> Unit,
    onNameChipClick: () -> Unit,
    onClearPreset: () -> Unit,
    onStartWithPromptCheck: () -> Unit,
    onAdjust: (Long) -> Unit,
    awakenMinimalUi: () -> Unit,
    onCleanModeExpand: () -> Unit,
    onCleanModeAdjust: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
                    isLandscape = false,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        StatusAndLogRow(
            state = state,
            isCleanModeActive = isCleanModeActive,
            minimalUiAlpha = minimalUiAlpha,
            onOpenLog = onOpenLog,
        )

        Spacer(modifier = Modifier.height(6.dp))

        TimerNameChip(
            state = state,
            isCleanModeActive = isCleanModeActive,
            minimalUiAlpha = minimalUiAlpha,
            onNameChipClick = onNameChipClick,
            onClearPreset = onClearPreset,
        )

        Spacer(modifier = Modifier.height(8.dp))

        HeroTimerCard(
            state = state,
            displayAlpha = if (isCleanModeActive) minimalUiAlpha else 1f,
            onDurationSelected = { onAction(TimerAction.SetDuration(it)) },
            onCenterTap = onOpenDurationPicker,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!isCleanModeActive) {
            SectionCard(title = stringResource(R.string.presets)) {
                PresetRow(
                    modifier = Modifier.fillMaxWidth(),
                    onPresetSelected = { onAction(TimerAction.SetDuration(it)) },
                    enabled = state.status != TimerStatus.Running,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SectionCard(title = stringResource(R.string.adjust_timer)) {
                QuickAdjustRow(
                    modifier = Modifier.fillMaxWidth(),
                    onAdjust = onAdjust,
                    enabled = true,
                )
            }
        } else {
            CleanModeQuickAdjust(
                controlsExpanded = cleanModeControlsExpanded,
                controlsAlpha = minimalUiAlpha,
                onExpand = onCleanModeExpand,
                onAdjust = onCleanModeAdjust,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        SectionCard(
            modifier = Modifier.alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
        ) {
            TimerControls(
                state = state,
                onAction = { action ->
                    if (action == TimerAction.Start) onStartWithPromptCheck()
                    else onAction(action)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        AssistChip(
            onClick = onOpenSettings,
            label = { Text(text = stringResource(R.string.settings)) },
            modifier = Modifier
                .align(Alignment.Start)
                .alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
            shape = RoundedCornerShape(22.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
    }
}

@Composable
private fun LandscapeLayout(
    state: TimerState,
    isCleanModeActive: Boolean,
    showTopClock: Boolean,
    minimalUiAlpha: Float,
    cleanModeControlsExpanded: Boolean,
    onAction: (TimerAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenDurationPicker: () -> Unit,
    onNameChipClick: () -> Unit,
    onClearPreset: () -> Unit,
    onStartWithPromptCheck: () -> Unit,
    onAdjust: (Long) -> Unit,
    awakenMinimalUi: () -> Unit,
    onCleanModeExpand: () -> Unit,
    onCleanModeAdjust: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Left: timer canvas
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            HeroTimerCard(
                state = state,
                displayAlpha = if (isCleanModeActive) minimalUiAlpha else 1f,
                onDurationSelected = { onAction(TimerAction.SetDuration(it)) },
                onCenterTap = onOpenDurationPicker,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Right: controls column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTopClock) {
                CurrentTimeText(
                    showSeconds = state.showClockSecondsEnabled,
                    clockPosition = state.clockPosition,
                    clockTextSize = state.clockTextSize,
                    isLandscape = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            StatusAndLogRow(
                state = state,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                onOpenLog = onOpenLog,
            )

            Spacer(modifier = Modifier.height(6.dp))

            TimerNameChip(
                state = state,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                onNameChipClick = onNameChipClick,
                onClearPreset = onClearPreset,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!isCleanModeActive) {
                SectionCard(title = stringResource(R.string.presets)) {
                    PresetRow(
                        modifier = Modifier.fillMaxWidth(),
                        onPresetSelected = { onAction(TimerAction.SetDuration(it)) },
                        enabled = state.status != TimerStatus.Running,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SectionCard(title = stringResource(R.string.adjust_timer)) {
                    QuickAdjustRow(
                        modifier = Modifier.fillMaxWidth(),
                        onAdjust = onAdjust,
                        enabled = true,
                    )
                }
            } else {
                CleanModeQuickAdjust(
                    controlsExpanded = cleanModeControlsExpanded,
                    controlsAlpha = minimalUiAlpha,
                    onExpand = onCleanModeExpand,
                    onAdjust = onCleanModeAdjust,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionCard(
                modifier = Modifier.alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
            ) {
                TimerControls(
                    state = state,
                    onAction = { action ->
                        if (action == TimerAction.Start) onStartWithPromptCheck()
                        else onAction(action)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = onOpenSettings,
                label = { Text(text = stringResource(R.string.settings)) },
                modifier = Modifier
                    .align(Alignment.Start)
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
private fun StatusAndLogRow(
    state: TimerState,
    isCleanModeActive: Boolean,
    minimalUiAlpha: Float,
    onOpenLog: () -> Unit,
) {
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

        val isActive = state.status != TimerStatus.Idle && state.originalDurationMillis > 0L
        val adjustedTotal = state.adjustedTotalMillis?.takeIf { isActive }
        InfoBadge(
            text = when {
                adjustedTotal != null -> stringResource(R.string.duration_adjusted, adjustedTotal.formatClockTime())
                isActive -> stringResource(R.string.duration_original, state.originalDurationMillis.formatClockTime())
                else -> state.selectedDurationMillis.formatClockTime()
            },
            secondaryText = if (adjustedTotal != null) {
                stringResource(R.string.duration_original, state.originalDurationMillis.formatClockTime())
            } else null,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )

        // History icon — rightmost; hidden in clean mode (invisible but not gone)
        Surface(
            onClick = onOpenLog,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier
                .size(36.dp)
                .alpha(if (isCleanModeActive) 0f else 1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "◷",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimerNameChip(
    state: TimerState,
    isCleanModeActive: Boolean,
    minimalUiAlpha: Float,
    onNameChipClick: () -> Unit,
    onClearPreset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onNameChipClick,
            enabled = state.status != TimerStatus.Running,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val hasName = state.activeTimerName.isNotBlank()
                Text(
                    text = if (hasName) state.activeTimerName else stringResource(R.string.name_placeholder),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hasName) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = if (hasName) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (state.isTimerNameAdjusted) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.log_adjusted_badge),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (state.status != TimerStatus.Running) {
                    Text(
                        text = "✎",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
        // Clear preset association button — only visible when a preset is active
        if (state.activePresetId != null) {
            Surface(
                onClick = onClearPreset,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerNameDialog(
    currentName: String,
    title: String = "",
    confirmLabel: String = "",
    dismissLabel: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val resolvedTitle = title.ifBlank { stringResource(R.string.name_dialog_title) }
    val resolvedConfirm = confirmLabel.ifBlank { stringResource(R.string.save) }
    val resolvedDismiss = dismissLabel.ifBlank { stringResource(R.string.cancel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(resolvedTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name_dialog_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) {
                Text(resolvedConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(resolvedDismiss)
            }
        },
    )
}

@Composable
private fun HeroTimerCard(
    state: TimerState,
    displayAlpha: Float,
    onDurationSelected: (Long) -> Unit,
    onCenterTap: () -> Unit,
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
        Surface(
            onClick = onCenterTap,
            color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier.size(120.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
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
    onSetDefaultDuration: () -> Unit,
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
            if (state.soundEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                FinishedSoundRouteSelector(
                    selectedRoute = state.finishedSoundRoute,
                    onRouteSelected = { onAction(TimerAction.SetFinishedSoundRoute(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                FinishedSoundVolumeSlider(
                    volumePercent = state.finishedSoundVolumePercent,
                    onVolumeChanged = { onAction(TimerAction.SetFinishedSoundVolumePercent(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.ignore_silent_mode),
                    checked = state.ignoreSilentMode,
                    onCheckedChange = { onAction(TimerAction.SetIgnoreSilentMode(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.override_muted_system_volume),
                    checked = state.overrideMutedSystemVolume,
                    onCheckedChange = { onAction(TimerAction.SetOverrideMutedSystemVolume(it)) },
                )
            }
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
                label = stringResource(R.string.full_clock_mode),
                checked = state.fullClockMode,
                onCheckedChange = { onAction(TimerAction.SetFullClockMode(it)) },
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

        Spacer(modifier = Modifier.height(12.dp))

        SectionCard {
            // Default duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.default_duration),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    onClick = onSetDefaultDuration,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = if (state.defaultDurationMillis > 0L)
                            state.defaultDurationMillis.formatClockTime()
                        else stringResource(R.string.default_duration_not_set),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.prompt_before_start),
                checked = state.promptBeforeStart,
                onCheckedChange = { onAction(TimerAction.SetPromptBeforeStart(it)) },
            )
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FinishedSoundRouteSelector(
    selectedRoute: FinishedSoundRoute,
    onRouteSelected: (FinishedSoundRoute) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.finished_sound_route),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.finished_sound_route_default),
                selected = selectedRoute == FinishedSoundRoute.Default,
                onClick = { onRouteSelected(FinishedSoundRoute.Default) },
            )
            SelectorChip(
                label = stringResource(R.string.finished_sound_route_alarm),
                selected = selectedRoute == FinishedSoundRoute.Alarm,
                onClick = { onRouteSelected(FinishedSoundRoute.Alarm) },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.finished_sound_route_notification),
                selected = selectedRoute == FinishedSoundRoute.Notification,
                onClick = { onRouteSelected(FinishedSoundRoute.Notification) },
            )
            SelectorChip(
                label = stringResource(R.string.finished_sound_route_media),
                selected = selectedRoute == FinishedSoundRoute.Media,
                onClick = { onRouteSelected(FinishedSoundRoute.Media) },
            )
        }
    }
}

@Composable
private fun FinishedSoundVolumeSlider(
    volumePercent: Int,
    onVolumeChanged: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.finished_sound_volume, volumePercent.coerceIn(0, 100)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = volumePercent.coerceIn(0, 100).toFloat(),
            onValueChange = { onVolumeChanged(it.roundToInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
        )
    }
}

@Composable
private fun FinishedVibrationSelector(
    selectedMode: FinishedVibrationMode,
    onModeSelected: (FinishedVibrationMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.finished_vibration),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.finished_vibration_off),
                selected = selectedMode == FinishedVibrationMode.Off,
                onClick = { onModeSelected(FinishedVibrationMode.Off) },
            )
            SelectorChip(
                label = stringResource(R.string.finished_vibration_1m),
                selected = selectedMode == FinishedVibrationMode.OneMinute,
                onClick = { onModeSelected(FinishedVibrationMode.OneMinute) },
            )
            SelectorChip(
                label = stringResource(R.string.finished_vibration_2m),
                selected = selectedMode == FinishedVibrationMode.TwoMinutes,
                onClick = { onModeSelected(FinishedVibrationMode.TwoMinutes) },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.finished_vibration_5m),
                selected = selectedMode == FinishedVibrationMode.FiveMinutes,
                onClick = { onModeSelected(FinishedVibrationMode.FiveMinutes) },
            )
            SelectorChip(
                label = stringResource(R.string.finished_vibration_10m),
                selected = selectedMode == FinishedVibrationMode.TenMinutes,
                onClick = { onModeSelected(FinishedVibrationMode.TenMinutes) },
            )
            SelectorChip(
                label = stringResource(R.string.finished_vibration_forever),
                selected = selectedMode == FinishedVibrationMode.Forever,
                onClick = { onModeSelected(FinishedVibrationMode.Forever) },
            )
        }
    }
}

@Composable
private fun ClockPositionSelector(
    selectedPosition: ClockPosition,
    onPositionSelected: (ClockPosition) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.clock_position),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.clock_left),
                selected = selectedPosition == ClockPosition.Left,
                onClick = { onPositionSelected(ClockPosition.Left) },
            )
            SelectorChip(
                label = stringResource(R.string.clock_center),
                selected = selectedPosition == ClockPosition.Center,
                onClick = { onPositionSelected(ClockPosition.Center) },
            )
            SelectorChip(
                label = stringResource(R.string.clock_right),
                selected = selectedPosition == ClockPosition.Right,
                onClick = { onPositionSelected(ClockPosition.Right) },
            )
        }
    }
}

@Composable
private fun ClockTextSizeSelector(
    selectedSize: ClockTextSize,
    onSizeSelected: (ClockTextSize) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.clock_size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.clock_size_small),
                selected = selectedSize == ClockTextSize.Small,
                onClick = { onSizeSelected(ClockTextSize.Small) },
            )
            SelectorChip(
                label = stringResource(R.string.clock_size_medium),
                selected = selectedSize == ClockTextSize.Medium,
                onClick = { onSizeSelected(ClockTextSize.Medium) },
            )
            SelectorChip(
                label = stringResource(R.string.clock_size_large),
                selected = selectedSize == ClockTextSize.Large,
                onClick = { onSizeSelected(ClockTextSize.Large) },
            )
        }
    }
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
                SelectorChip(
                    label = label,
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.SelectorChip(
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
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        },
    )
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
    secondaryText: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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
        style = clockTextStyle(clockTextSize = clockTextSize, isLandscape = isLandscape),
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
private fun clockTextStyle(clockTextSize: ClockTextSize, isLandscape: Boolean) =
    when (clockTextSize) {
        ClockTextSize.Small -> if (isLandscape) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium
        ClockTextSize.Medium -> if (isLandscape) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge
        ClockTextSize.Large -> if (isLandscape) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall
    }
