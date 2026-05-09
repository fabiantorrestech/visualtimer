package com.fabiantorrestech.visualtimerplus.ui.screen

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.CLEAN_MODE_AUTO_DISMISS_DEFAULT_SECONDS
import com.fabiantorrestech.visualtimerplus.timer.withTimerIndex
import com.fabiantorrestech.visualtimerplus.timer.CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS
import com.fabiantorrestech.visualtimerplus.timer.CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS
import com.fabiantorrestech.visualtimerplus.timer.ClockPosition
import com.fabiantorrestech.visualtimerplus.timer.FinishedSoundRoute
import com.fabiantorrestech.visualtimerplus.timer.FinishedVibrationMode
import com.fabiantorrestech.visualtimerplus.timer.NotificationMode
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.component.DurationPickerSheet
import com.fabiantorrestech.visualtimerplus.ui.component.PresetRow
import com.fabiantorrestech.visualtimerplus.ui.component.PresetsSheet
import com.fabiantorrestech.visualtimerplus.ui.component.QuickAdjustRow
import com.fabiantorrestech.visualtimerplus.ui.component.TimerControls
import com.fabiantorrestech.visualtimerplus.ui.component.VisualTimerCanvas
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    stateFlow: StateFlow<AppState>,
    onAction: (TimerAction) -> Unit,
    onToggleOledMode: (Boolean) -> Unit,
    onNotificationPermissionNeeded: () -> Unit,
    onOpenLog: () -> Unit,
    db: AppDatabase,
    openPresetsOnLaunch: Boolean = false,
) {
    val appState by stateFlow.collectAsStateWithLifecycle()
    val timer = appState.activeTimer
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val pagerState = rememberPagerState(
        pageCount = { appState.timers.size + if (appState.timers.size < 20) 1 else 0 },
    )
    val coroutineScope = rememberCoroutineScope()
    // Use the currently visible page for outer-level clean mode / status bar logic
    val currentPageTimer = appState.timers.getOrElse(pagerState.currentPage) { appState.activeTimer }
    val currentPageSettings = currentPageTimer.settings
    val isCleanModeActive = currentPageSettings.cleanModeEnabled &&
        (currentPageTimer.status == TimerStatus.Running || currentPageTimer.status == TimerStatus.Overtime)

    var showAllTimersSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showPresetsSheet by rememberSaveable { mutableStateOf(openPresetsOnLaunch) }
    var showDurationPicker by rememberSaveable { mutableStateOf(false) }
    var showDefaultDurationPicker by rememberSaveable { mutableStateOf(false) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showStartNamePrompt by rememberSaveable { mutableStateOf(false) }
    var showDeleteTimerDialog by rememberSaveable { mutableStateOf(false) }

    // Tracks which activeTimerIndex was last set BY the pager, to prevent the reverse
    // sync from animating back when the user swipes faster than state propagates.
    var lastPagerSetActiveIndex by remember { mutableStateOf(appState.activeTimerIndex) }

    var cleanModeUiAwake by rememberSaveable { mutableStateOf(false) }
    var cleanModeControlsExpanded by rememberSaveable { mutableStateOf(false) }
    var cleanModeActivityTick by rememberSaveable { mutableStateOf(0) }
    var cleanModeWasActive by rememberSaveable { mutableStateOf(false) }

    val minimalUiAlpha by animateFloatAsState(
        targetValue = if (isCleanModeActive && !cleanModeUiAwake) 0f else 1f,
        label = "minimalUiAlpha",
    )

    fun awakenMinimalUi() {
        if (!isCleanModeActive) return
        cleanModeUiAwake = true
        cleanModeActivityTick += 1
    }

    // Pager → state: when swipe settles, sync active timer and reset clean mode.
    // Record which index the pager set so the reverse effect can ignore it.
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage < appState.timers.size) {
            lastPagerSetActiveIndex = pagerState.settledPage
            onAction(TimerAction.SetActiveTimer(pagerState.settledPage))
        }
        cleanModeUiAwake = false
        cleanModeControlsExpanded = false
        cleanModeWasActive = false
    }

    // State → pager: when active timer changes externally (e.g. AddTimer / RemoveTimer), scroll pager.
    // Skip when the change was triggered by the pager itself to break the feedback loop that
    // caused jitter when swiping faster than state propagation.
    LaunchedEffect(appState.activeTimerIndex) {
        val target = appState.activeTimerIndex
        if (target != lastPagerSetActiveIndex && target < appState.timers.size) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(timer.status) {
        if (timer.status == TimerStatus.Running || timer.status == TimerStatus.Overtime) onNotificationPermissionNeeded()
    }

    LaunchedEffect(isCleanModeActive) {
        if (!isCleanModeActive) {
            cleanModeUiAwake = false
            cleanModeControlsExpanded = false
            cleanModeWasActive = false
        } else if (!cleanModeWasActive) {
            cleanModeWasActive = true
            cleanModeUiAwake = true
            cleanModeControlsExpanded = false
            cleanModeActivityTick += 1
        }
    }

    LaunchedEffect(isCleanModeActive, cleanModeActivityTick, currentPageSettings.cleanModeAutoDismissSeconds) {
        if (!isCleanModeActive || !cleanModeUiAwake) return@LaunchedEffect
        delay(currentPageSettings.cleanModeAutoDismissSeconds * 1_000L)
        cleanModeUiAwake = false
        cleanModeControlsExpanded = false
    }

    if (showDurationPicker) {
        DurationPickerSheet(
            initialMillis = timer.selectedDurationMillis,
            onDurationSet = { millis ->
                onAction(TimerAction.SetDurationExact(millis))
                showDurationPicker = false
            },
            onDismiss = { showDurationPicker = false },
        )
    }

    if (showDefaultDurationPicker) {
        DurationPickerSheet(
            initialMillis = appState.defaultDurationMillis,
            onDurationSet = { millis ->
                onAction(TimerAction.SetAppDefaultDuration(millis))
                showDefaultDurationPicker = false
            },
            onDismiss = { showDefaultDurationPicker = false },
        )
    }

    if (showNameDialog) {
        TimerNameDialog(
            currentName = timer.activeTimerName,
            onConfirm = { name ->
                onAction(TimerAction.SetActiveTimerName(name))
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showStartNamePrompt) {
        TimerNameDialog(
            currentName = timer.activeTimerName,
            title = stringResource(R.string.name_prompt_title),
            confirmLabel = stringResource(R.string.start),
            onConfirm = { name ->
                onAction(TimerAction.SetActiveTimerName(name))
                showStartNamePrompt = false
                onAction(TimerAction.Start())
            },
            onDismiss = {
                showStartNamePrompt = false
                onAction(TimerAction.Start())
            },
            dismissLabel = stringResource(R.string.name_prompt_skip),
        )
    }

    if (showPresetsSheet) {
        PresetsSheet(
            db = db,
            currentDurationMillis = timer.selectedDurationMillis,
            onAction = onAction,
            onDismiss = { showPresetsSheet = false },
        )
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            SettingsSheetContent(
                appState = appState,
                timer = timer,
                onAction = onAction,
                onSetDefaultDuration = { showDefaultDurationPicker = true },
            )
        }
    }

    if (showDeleteTimerDialog && appState.timers.size > 1) {
        val timerName = timer.activeTimerName.ifBlank {
            stringResource(R.string.timer_delete_default_name, appState.activeTimerIndex + 1)
        }
        AlertDialog(
            onDismissRequest = { showDeleteTimerDialog = false },
            title = { Text(stringResource(R.string.timer_delete_title, timerName)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(TimerAction.RemoveTimer(appState.activeTimerIndex))
                    showDeleteTimerDialog = false
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTimerDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val bgModifier = if (appState.isOledMode) {
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

    // rememberUpdatedState gives a State object the pointerInput lambda can read
    // without needing to restart when isCleanModeActive or timer status changes.
    val cleanModeState = rememberUpdatedState(isCleanModeActive)
    val pageTimerStatusState = rememberUpdatedState(currentPageTimer.status)
    val tapToToggleState = rememberUpdatedState(appState.tapToToggleMinimalMode)
    val cleanModeUiAwakeState = rememberUpdatedState(cleanModeUiAwake)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
            .pointerInput(appState.timers.size) {
                // detectTapGestures can't be used here because HorizontalPager consumes
                // down events. requireUnconsumed=false lets us see the down regardless.
                // PointerEventPass.Final lets us see whether a child (e.g. PageIndicatorBar)
                // consumed the event — if so we skip our handlers.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    var slopExceeded = false
                    var consumed = false
                    val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) { consumed = true; break }
                            if (!change.pressed) break
                            if ((change.position - startPos).getDistance() > viewConfiguration.touchSlop) {
                                slopExceeded = true; break
                            }
                        } while (true)
                    } == null
                    val isTap = !longPressed && !slopExceeded && !consumed
                    when {
                        longPressed -> showAllTimersSheet = true
                        isTap &&
                            tapToToggleState.value &&
                            cleanModeState.value &&
                            (pageTimerStatusState.value == TimerStatus.Running || pageTimerStatusState.value == TimerStatus.Overtime) -> {
                            cleanModeUiAwake = !cleanModeUiAwakeState.value
                            if (cleanModeUiAwake) {
                                cleanModeActivityTick += 1
                            } else {
                                cleanModeControlsExpanded = false
                            }
                        }
                    }
                }
            },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            if (pageIndex < appState.timers.size) {
                val pageTimer = appState.timers[pageIndex]
                val pagedOnAction: (TimerAction) -> Unit = { onAction(it.withTimerIndex(pageIndex)) }
                val pageIsStatusBarHiddenNow = appState.hideStatusBarEnabled &&
                    (!appState.hideStatusBarOnlyWhenRunning ||
                        pageTimer.status == TimerStatus.Running ||
                        pageTimer.status == TimerStatus.Overtime)
                val pageShowTopClock = pageTimer.settings.showCurrentTimeEnabled
                val pageIsCleanModeActive = pageTimer.settings.cleanModeEnabled &&
                    (pageTimer.status == TimerStatus.Running || pageTimer.status == TimerStatus.Overtime)

                if (isLandscape) {
                    LandscapeLayout(
                        appState = appState,
                        timer = pageTimer,
                        isCleanModeActive = pageIsCleanModeActive,
                        showTopClock = pageShowTopClock,
                        minimalUiAlpha = minimalUiAlpha,
                        cleanModeControlsExpanded = cleanModeControlsExpanded,
                        onAction = pagedOnAction,
                        onOpenSettings = { showSettingsSheet = true; awakenMinimalUi() },
                        onOpenLog = onOpenLog,
                        onOpenPresets = { showPresetsSheet = true },
                        onOpenDurationPicker = { if (pageTimer.status == TimerStatus.Idle) showDurationPicker = true },
                        onNameChipClick = { showNameDialog = true },
                        onClearPreset = {
                            pagedOnAction(TimerAction.SetActivePresetId(null))
                            pagedOnAction(TimerAction.SetActiveTimerName(""))
                        },
                        onStartWithPromptCheck = {
                            if (pageTimer.settings.promptBeforeStart) showStartNamePrompt = true
                            else pagedOnAction(TimerAction.Start())
                        },
                        onAdjust = { pagedOnAction(TimerAction.AdjustDuration(it)) },
                        awakenMinimalUi = ::awakenMinimalUi,
                        onCleanModeExpand = {
                            awakenMinimalUi()
                            cleanModeControlsExpanded = true
                        },
                        onCleanModeAdjust = { delta ->
                            pagedOnAction(TimerAction.AdjustDuration(delta))
                            awakenMinimalUi()
                            cleanModeControlsExpanded = false
                        },
                    )
                } else {
                    PortraitLayout(
                        appState = appState,
                        timer = pageTimer,
                        isCleanModeActive = pageIsCleanModeActive,
                        showTopClock = pageShowTopClock,
                        minimalUiAlpha = minimalUiAlpha,
                        cleanModeControlsExpanded = cleanModeControlsExpanded,
                        onAction = pagedOnAction,
                        onOpenSettings = { showSettingsSheet = true; awakenMinimalUi() },
                        onOpenLog = onOpenLog,
                        onOpenPresets = { showPresetsSheet = true },
                        onOpenDurationPicker = { if (pageTimer.status == TimerStatus.Idle) showDurationPicker = true },
                        onNameChipClick = { showNameDialog = true },
                        onClearPreset = {
                            pagedOnAction(TimerAction.SetActivePresetId(null))
                            pagedOnAction(TimerAction.SetActiveTimerName(""))
                        },
                        onStartWithPromptCheck = {
                            if (pageTimer.settings.promptBeforeStart) showStartNamePrompt = true
                            else pagedOnAction(TimerAction.Start())
                        },
                        onAdjust = { pagedOnAction(TimerAction.AdjustDuration(it)) },
                        awakenMinimalUi = ::awakenMinimalUi,
                        cleanModeUiAwake = cleanModeUiAwake,
                        onCleanModeExpand = {
                            awakenMinimalUi()
                            cleanModeControlsExpanded = true
                        },
                        onCleanModeAdjust = { delta ->
                            pagedOnAction(TimerAction.AdjustDuration(delta))
                            awakenMinimalUi()
                            cleanModeControlsExpanded = false
                        },
                    )
                }
            } else {
                AddTimerPage(onAdd = { onAction(TimerAction.AddTimer) })
            }
        }

        PageIndicatorBar(
            timers = appState.timers,
            pagerState = pagerState,
            isCleanModeActive = isCleanModeActive,
            hideInCleanMode = appState.hidePageDotsInCleanMode,
            onLongPress = { showAllTimersSheet = true },
            onScrollToPage = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )

        if (!isCleanModeActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (appState.timers.size > 1) {
                    SmallFloatingActionButton(
                        onClick = { showDeleteTimerDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        elevation = FloatingActionButtonDefaults.loweredElevation(),
                    ) {
                        Text(text = "−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                if (appState.timers.size < 20) {
                    SmallFloatingActionButton(
                        onClick = { onAction(TimerAction.AddTimer) },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.loweredElevation(),
                    ) {
                        Text(text = "+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                FloatingActionButton(
                    onClick = { showPresetsSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.loweredElevation(),
                ) {
                    Text(text = "☰", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    if (showAllTimersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllTimersSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            AllTimersSheet(
                appState = appState,
                onNavigateToTimer = { index ->
                    showAllTimersSheet = false
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                },
                onDeleteTimer = { index -> onAction(TimerAction.RemoveTimer(index)) },
                onDeleteAllTimers = { onAction(TimerAction.RemoveAllTimers) },
                onDeleteNonRunningTimers = { onAction(TimerAction.RemoveNonRunningTimers) },
                onDismiss = { showAllTimersSheet = false },
                confirmSwipeDelete = appState.confirmSwipeDelete,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageIndicatorBar(
    timers: List<TimerInstance>,
    pagerState: PagerState,
    isCleanModeActive: Boolean,
    hideInCleanMode: Boolean,
    onLongPress: () -> Unit,
    onScrollToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (timers.size <= 1) return
    if (isCleanModeActive && hideInCleanMode) return

    val useDots = timers.size <= 4
    val currentPage = pagerState.currentPage.coerceIn(0, timers.lastIndex)

    Row(
        modifier = modifier
            .combinedClickable(onLongClick = onLongPress, onClick = {})
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "←",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (currentPage > 0) 0.8f else 0.25f),
            modifier = Modifier.clickable(enabled = currentPage > 0) { onScrollToPage(currentPage - 1) },
        )

        if (useDots) {
            timers.indices.forEach { i ->
                val isActive = i == currentPage
                Box(
                    modifier = Modifier
                        .size(if (isActive) 10.dp else 7.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isActive) 0.9f else 0.35f),
                            shape = CircleShape,
                        ),
                )
            }
        } else {
            Text(
                text = "${currentPage + 1}/${timers.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }

        Text(
            text = "→",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (currentPage < timers.lastIndex) 0.8f else 0.25f),
            modifier = Modifier.clickable(enabled = currentPage < timers.lastIndex) { onScrollToPage(currentPage + 1) },
        )
    }
}

@Composable
private fun AddTimerPage(onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = stringResource(R.string.add_timer),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = onAdd,
                label = { Text(stringResource(R.string.add_timer)) },
                shape = RoundedCornerShape(20.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitLayout(
    appState: AppState,
    timer: TimerInstance,
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
    cleanModeUiAwake: Boolean,
    onCleanModeExpand: () -> Unit,
    onCleanModeAdjust: (Long) -> Unit,
) {
    val settings = timer.settings
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    LaunchedEffect(isCleanModeActive, cleanModeUiAwake) {
        if (isCleanModeActive) sheetState.partialExpand()
    }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeight = if (isCleanModeActive && !cleanModeUiAwake) 0.dp else 164.dp + navBarHeight
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        containerColor = Color.Transparent,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                SectionCard(modifier = Modifier.alpha(if (isCleanModeActive) minimalUiAlpha else 1f)) {
                    val interactable = !isCleanModeActive || minimalUiAlpha > 0.99f
                    TimerControls(
                        timer = timer,
                        onAction = { action ->
                            if (!interactable) return@TimerControls
                            if (action is TimerAction.Start) onStartWithPromptCheck()
                            else onAction(action)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { if (!isCleanModeActive || minimalUiAlpha > 0.99f) onOpenSettings() },
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
                Spacer(modifier = Modifier.height(16.dp))
                if (!isCleanModeActive) {
                    SectionCard(title = stringResource(R.string.presets)) {
                        PresetRow(
                            modifier = Modifier.fillMaxWidth(),
                            onPresetSelected = { onAction(TimerAction.SetDuration(it)) },
                            enabled = timer.status != TimerStatus.Running,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionCard(title = stringResource(R.string.adjust_timer)) {
                        QuickAdjustRow(
                            modifier = Modifier.fillMaxWidth(),
                            onAdjust = onAdjust,
                            enabled = true,
                            positiveOnly = timer.status == TimerStatus.Overtime,
                        )
                    }
                } else {
                    val interactable = minimalUiAlpha > 0.99f
                    CleanModeQuickAdjust(
                        controlsExpanded = cleanModeControlsExpanded,
                        controlsAlpha = minimalUiAlpha,
                        positiveOnly = timer.status == TimerStatus.Overtime,
                        onExpand = { if (interactable) onCleanModeExpand() },
                        onAdjust = { delta -> if (interactable) onCleanModeAdjust(delta) },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTopClock) {
                Box(
                    modifier = Modifier.alpha(
                        if (isCleanModeActive && settings.hideClockInCleanMode) minimalUiAlpha else 1f,
                    ),
                ) {
                    CurrentTimeText(
                        showSeconds = settings.showClockSecondsEnabled,
                        clockPosition = settings.clockPosition,
                        clockTextSizeSp = settings.clockTextSizeSp,
                    )
                }
            }

            val showEndTime = settings.showEndTimeEnabled &&
                timer.status != TimerStatus.Finished && timer.displayMillis > 0L
            if (showEndTime) {
                EndTimeText(
                    timer = timer,
                    clockPosition = settings.clockPosition,
                    textSizeSp = settings.endTimeSizeSp,
                    modifier = Modifier.alpha(
                        if (isCleanModeActive && settings.hideClockInCleanMode) minimalUiAlpha else 1f,
                    ),
                )
            }

            val showTitle = settings.timerTitleEnabled && timer.activeTimerName.isNotBlank()
            val showAnyTopRow = showTopClock || showEndTime
            if (showAnyTopRow && showTitle) Spacer(modifier = Modifier.height(6.dp))
            else if (showAnyTopRow || showTitle) Spacer(modifier = Modifier.height(4.dp))
            else Spacer(modifier = Modifier.height(16.dp))

            TimerTitleDisplay(timer = timer, isCleanModeActive = isCleanModeActive, minimalUiAlpha = minimalUiAlpha)

            if (showTitle) Spacer(modifier = Modifier.height(8.dp))
            else if (!showTopClock) Spacer(modifier = Modifier.height(0.dp))

            StatusAndLogRow(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                onOpenLog = onOpenLog,
            )

            Spacer(modifier = Modifier.height(6.dp))

            TimerNameChip(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                onNameChipClick = onNameChipClick,
                onClearPreset = onClearPreset,
            )

            Spacer(modifier = Modifier.height(8.dp))

            HeroTimerCard(
                timer = timer,
                displayAlpha = if (isCleanModeActive) minimalUiAlpha else 1f,
                onDurationSelected = { onAction(TimerAction.SetDuration(it)) },
                onCenterTap = onOpenDurationPicker,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                isOledMode = appState.isOledMode,
            )
        }
    }
}

@Composable
private fun LandscapeLayout(
    appState: AppState,
    timer: TimerInstance,
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
    val settings = timer.settings
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            HeroTimerCard(
                timer = timer,
                displayAlpha = if (isCleanModeActive) minimalUiAlpha else 1f,
                onDurationSelected = { onAction(TimerAction.SetDuration(it)) },
                onCenterTap = onOpenDurationPicker,
                modifier = Modifier.fillMaxSize(),
                isOledMode = appState.isOledMode,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTopClock) {
                CurrentTimeText(
                    showSeconds = settings.showClockSecondsEnabled,
                    clockPosition = settings.clockPosition,
                    clockTextSizeSp = settings.clockTextSizeSp,
                )
            }

            val showTitleLandscape = settings.timerTitleEnabled && timer.activeTimerName.isNotBlank()
            if (showTopClock && showTitleLandscape) Spacer(modifier = Modifier.height(6.dp))
            else if (showTopClock || showTitleLandscape) Spacer(modifier = Modifier.height(4.dp))

            TimerTitleDisplay(timer = timer, isCleanModeActive = isCleanModeActive, minimalUiAlpha = minimalUiAlpha)

            Spacer(modifier = Modifier.height(if (showTopClock || showTitleLandscape) 8.dp else 0.dp))

            StatusAndLogRow(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                onOpenLog = onOpenLog,
            )

            Spacer(modifier = Modifier.height(6.dp))

            TimerNameChip(
                timer = timer,
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
                        enabled = timer.status != TimerStatus.Running,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SectionCard(title = stringResource(R.string.adjust_timer)) {
                    QuickAdjustRow(
                        modifier = Modifier.fillMaxWidth(),
                        onAdjust = onAdjust,
                        enabled = true,
                        positiveOnly = timer.status == TimerStatus.Overtime,
                    )
                }
            } else {
                val interactable = minimalUiAlpha > 0.99f
                CleanModeQuickAdjust(
                    controlsExpanded = cleanModeControlsExpanded,
                    controlsAlpha = minimalUiAlpha,
                    positiveOnly = timer.status == TimerStatus.Overtime,
                    onExpand = { if (interactable) onCleanModeExpand() },
                    onAdjust = { delta -> if (interactable) onCleanModeAdjust(delta) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionCard(modifier = Modifier.alpha(if (isCleanModeActive) minimalUiAlpha else 1f)) {
                val interactable = !isCleanModeActive || minimalUiAlpha > 0.99f
                TimerControls(
                    timer = timer,
                    onAction = { action ->
                        if (!interactable) return@TimerControls
                        if (action is TimerAction.Start) onStartWithPromptCheck()
                        else onAction(action)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = { if (!isCleanModeActive || minimalUiAlpha > 0.99f) onOpenSettings() },
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
    timer: TimerInstance,
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
        InfoBadge(text = statusLabel(timer.status), modifier = Modifier.weight(1f))

        val isActive = timer.status != TimerStatus.Idle && timer.originalDurationMillis > 0L
        val adjustedTotal = timer.adjustedTotalMillis?.takeIf { isActive }
        InfoBadge(
            text = when {
                adjustedTotal != null -> stringResource(R.string.duration_adjusted, adjustedTotal.formatClockTime())
                isActive -> stringResource(R.string.duration_original, timer.originalDurationMillis.formatClockTime())
                else -> timer.selectedDurationMillis.formatClockTime()
            },
            secondaryText = if (adjustedTotal != null) {
                stringResource(R.string.duration_original, timer.originalDurationMillis.formatClockTime())
            } else null,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )

        Surface(
            onClick = onOpenLog,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(36.dp).alpha(if (isCleanModeActive) 0f else 1f),
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
    timer: TimerInstance,
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
                val hasName = timer.activeTimerName.isNotBlank()
                Text(
                    text = if (hasName) timer.activeTimerName else stringResource(R.string.name_placeholder),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hasName) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = if (hasName) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (timer.isTimerNameAdjusted) {
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
                Text(
                    text = "✎",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        if (timer.activePresetId != null) {
            Surface(
                onClick = onClearPreset,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "✕", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            TextButton(onClick = { onConfirm(name.trim()) }) { Text(resolvedConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(resolvedDismiss) }
        },
    )
}

@Composable
private fun HeroTimerCard(
    timer: TimerInstance,
    displayAlpha: Float,
    onDurationSelected: (Long) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
    isOledMode: Boolean = false,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        VisualTimerCanvas(
            modifier = Modifier.fillMaxSize(),
            timer = timer,
            onDurationSelected = onDurationSelected,
            isOledMode = isOledMode,
        )
        Surface(
            onClick = onCenterTap,
            color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier.wrapContentSize(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = timer.displayMillis.formatClockTime(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = timer.settings.centerTimeSizeSp.sp,
                        lineHeight = (timer.settings.centerTimeSizeSp * 1.2f).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusLabel(timer.status),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(displayAlpha),
                    maxLines = 1,
                    softWrap = false,
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
    TimerStatus.Overtime -> stringResource(R.string.status_overtime)
    TimerStatus.Finished -> stringResource(R.string.status_finished)
}

@Composable
private fun SettingsSheetContent(
    appState: AppState,
    timer: TimerInstance,
    onAction: (TimerAction) -> Unit,
    onSetDefaultDuration: () -> Unit,
) {
    val settings = timer.settings
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

        // ── App-global settings ────────────────────────────────────────────────
        SectionCard {
            ThemeModeSelector(
                selectedMode = appState.themeMode,
                onModeSelected = { onAction(TimerAction.SetThemeMode(it)) },
            )
            if (appState.themeMode != ThemeMode.Light) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.oled_black),
                    checked = appState.isOledMode,
                    onCheckedChange = { onAction(TimerAction.SetOledMode(it)) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.confirm_swipe_delete),
                checked = appState.confirmSwipeDelete,
                onCheckedChange = { onAction(TimerAction.SetConfirmSwipeDelete(it)) },
            )
            PreferenceToggle(
                label = stringResource(R.string.tap_to_toggle_minimal_mode),
                checked = appState.tapToToggleMinimalMode,
                onCheckedChange = { onAction(TimerAction.SetTapToToggleMinimalMode(it)) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.current_timer_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Per-timer sound & vibration settings ──────────────────────────────
        SectionCard {
            PreferenceToggle(
                label = stringResource(R.string.sound),
                checked = settings.soundEnabled,
                onCheckedChange = { onAction(TimerAction.SetSoundEnabled(it)) },
            )
            if (settings.soundEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                FinishedSoundRouteSelector(
                    selectedRoute = settings.finishedSoundRoute,
                    onRouteSelected = { onAction(TimerAction.SetFinishedSoundRoute(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                FinishedSoundVolumeSlider(
                    volumePercent = settings.finishedSoundVolumePercent,
                    onVolumeChanged = { onAction(TimerAction.SetFinishedSoundVolumePercent(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.ignore_silent_mode),
                    checked = settings.ignoreSilentMode,
                    onCheckedChange = { onAction(TimerAction.SetIgnoreSilentMode(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.override_muted_system_volume),
                    checked = settings.overrideMutedSystemVolume,
                    onCheckedChange = { onAction(TimerAction.SetOverrideMutedSystemVolume(it)) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            FinishedVibrationSelector(
                selectedMode = settings.finishedVibrationMode,
                onModeSelected = { onAction(TimerAction.SetFinishedVibrationMode(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.keep_screen_awake),
                checked = settings.keepScreenAwake,
                onCheckedChange = { onAction(TimerAction.SetKeepScreenAwakeEnabled(it)) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Status bar settings (app-global) ──────────────────────────────────
        SectionCard {
            PreferenceToggle(
                label = stringResource(R.string.hide_status_bar),
                checked = appState.hideStatusBarEnabled,
                onCheckedChange = { onAction(TimerAction.SetHideStatusBarEnabled(it)) },
            )
            if (appState.hideStatusBarEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.hide_status_bar_only_while_running),
                    checked = appState.hideStatusBarOnlyWhenRunning,
                    onCheckedChange = { onAction(TimerAction.SetHideStatusBarOnlyWhenRunning(it)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Per-timer visual settings ──────────────────────────────────────────
        SectionCard {
            PreferenceToggle(
                label = stringResource(R.string.show_current_time),
                checked = settings.showCurrentTimeEnabled,
                onCheckedChange = { onAction(TimerAction.SetShowCurrentTimeEnabled(it)) },
            )
            if (settings.showCurrentTimeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.show_seconds),
                    checked = settings.showClockSecondsEnabled,
                    onCheckedChange = { onAction(TimerAction.SetShowClockSecondsEnabled(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                ClockPositionSelector(
                    selectedPosition = settings.clockPosition,
                    onPositionSelected = { onAction(TimerAction.SetClockPosition(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SizeSlider(
                    label = stringResource(R.string.clock_size),
                    value = settings.clockTextSizeSp,
                    onValueChange = { onAction(TimerAction.SetClockTextSizeSp(it)) },
                    valueRange = 14f..60f,
                    defaultValue = 32f,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.show_end_time),
                checked = settings.showEndTimeEnabled,
                onCheckedChange = { onAction(TimerAction.SetShowEndTimeEnabled(it)) },
            )
            if (settings.showEndTimeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                SizeSlider(
                    label = stringResource(R.string.end_time_size),
                    value = settings.endTimeSizeSp,
                    onValueChange = { onAction(TimerAction.SetEndTimeSizeSp(it)) },
                    valueRange = 14f..60f,
                    defaultValue = 32f,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SizeSlider(
                label = stringResource(R.string.center_time_size),
                value = settings.centerTimeSizeSp,
                onValueChange = { onAction(TimerAction.SetCenterTimeSizeSp(it)) },
                valueRange = 20f..80f,
                defaultValue = 36f,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.clockwise_mode),
                checked = settings.clockwiseModeEnabled,
                onCheckedChange = { onAction(TimerAction.SetClockwiseModeEnabled(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.full_clock_mode),
                checked = settings.fullClockMode,
                onCheckedChange = { onAction(TimerAction.SetFullClockMode(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.clean_mode),
                checked = settings.cleanModeEnabled,
                onCheckedChange = { onAction(TimerAction.SetCleanModeEnabled(it)) },
            )
            if (settings.cleanModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                SizeSlider(
                    label = stringResource(R.string.clean_mode_auto_dismiss_time),
                    value = settings.cleanModeAutoDismissSeconds.toFloat(),
                    onValueChange = { onAction(TimerAction.SetCleanModeAutoDismissSeconds(it.roundToInt())) },
                    valueRange = CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS.toFloat()..CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS.toFloat(),
                    defaultValue = CLEAN_MODE_AUTO_DISMISS_DEFAULT_SECONDS.toFloat(),
                    steps = CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS - CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS - 1,
                    valueText = "${settings.cleanModeAutoDismissSeconds}s",
                )
            }
            if (settings.cleanModeEnabled && settings.showCurrentTimeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.hide_clock_in_minimal_mode),
                    checked = settings.hideClockInCleanMode,
                    onCheckedChange = { onAction(TimerAction.SetHideClockInCleanMode(it)) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.timer_title_enabled),
                checked = settings.timerTitleEnabled,
                onCheckedChange = { onAction(TimerAction.SetTimerTitleEnabled(it)) },
            )
            if (settings.timerTitleEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                TitlePositionSelector(
                    selectedPosition = settings.timerTitlePosition,
                    onPositionSelected = { onAction(TimerAction.SetTimerTitlePosition(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SizeSlider(
                    label = stringResource(R.string.timer_title_size),
                    value = settings.timerTitleTextSizeSp,
                    onValueChange = { onAction(TimerAction.SetTimerTitleTextSizeSp(it)) },
                    valueRange = 10f..48f,
                    defaultValue = 16f,
                )
                if (settings.cleanModeEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PreferenceToggle(
                        label = stringResource(R.string.timer_title_hide_in_clean_mode),
                        checked = settings.timerTitleHideInCleanMode,
                        onCheckedChange = { onAction(TimerAction.SetTimerTitleHideInCleanMode(it)) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Per-timer start settings ──────────────────────────────────────────
        SectionCard {
            PreferenceToggle(
                label = stringResource(R.string.prompt_before_start),
                checked = settings.promptBeforeStart,
                onCheckedChange = { onAction(TimerAction.SetPromptBeforeStart(it)) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Default settings for new timers (collapsible) ─────────────────────
        var defaultSettingsExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { defaultSettingsExpanded = !defaultSettingsExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.default_timer_settings_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (defaultSettingsExpanded) "▲" else "▼",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = defaultSettingsExpanded) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                DefaultTimerSettingsSection(
                    defaultSettings = appState.defaultTimerSettings,
                    defaultDurationMillis = appState.defaultDurationMillis,
                    onSettingsChanged = { onAction(TimerAction.SetDefaultTimerSettings(it)) },
                    onSetDefaultDuration = onSetDefaultDuration,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Notification style (app-global) ───────────────────────────────────
        SectionCard {
            NotificationModeSelector(
                selectedMode = appState.notificationMode,
                onModeSelected = { onAction(TimerAction.SetNotificationMode(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            NotificationUpdateIntervalSelector(
                intervalSeconds = appState.notificationUpdateIntervalSeconds,
                onIntervalSelected = { onAction(TimerAction.SetNotificationUpdateInterval(it)) },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DefaultTimerSettingsSection(
    defaultSettings: com.fabiantorrestech.visualtimerplus.timer.TimerSettings,
    defaultDurationMillis: Long,
    onSettingsChanged: (com.fabiantorrestech.visualtimerplus.timer.TimerSettings) -> Unit,
    onSetDefaultDuration: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.default_timer_settings_title)) {
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
                    text = if (defaultDurationMillis > 0L)
                        defaultDurationMillis.formatClockTime()
                    else stringResource(R.string.default_duration_not_set),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.sound),
            checked = defaultSettings.soundEnabled,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(soundEnabled = it)) },
        )
        if (defaultSettings.soundEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            FinishedSoundRouteSelector(
                selectedRoute = defaultSettings.finishedSoundRoute,
                onRouteSelected = { onSettingsChanged(defaultSettings.copy(finishedSoundRoute = it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            FinishedSoundVolumeSlider(
                volumePercent = defaultSettings.finishedSoundVolumePercent,
                onVolumeChanged = { onSettingsChanged(defaultSettings.copy(finishedSoundVolumePercent = it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.ignore_silent_mode),
                checked = defaultSettings.ignoreSilentMode,
                onCheckedChange = { onSettingsChanged(defaultSettings.copy(ignoreSilentMode = it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.override_muted_system_volume),
                checked = defaultSettings.overrideMutedSystemVolume,
                onCheckedChange = { onSettingsChanged(defaultSettings.copy(overrideMutedSystemVolume = it)) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FinishedVibrationSelector(
            selectedMode = defaultSettings.finishedVibrationMode,
            onModeSelected = { onSettingsChanged(defaultSettings.copy(finishedVibrationMode = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.keep_screen_awake),
            checked = defaultSettings.keepScreenAwake,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(keepScreenAwake = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SizeSlider(
            label = stringResource(R.string.center_time_size),
            value = defaultSettings.centerTimeSizeSp,
            onValueChange = { onSettingsChanged(defaultSettings.copy(centerTimeSizeSp = it)) },
            valueRange = 20f..80f,
            defaultValue = 36f,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.show_current_time),
            checked = defaultSettings.showCurrentTimeEnabled,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(showCurrentTimeEnabled = it, showClockSecondsEnabled = if (it) defaultSettings.showClockSecondsEnabled else false)) },
        )
        if (defaultSettings.showCurrentTimeEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.show_seconds),
                checked = defaultSettings.showClockSecondsEnabled,
                onCheckedChange = { onSettingsChanged(defaultSettings.copy(showClockSecondsEnabled = it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            ClockPositionSelector(
                selectedPosition = defaultSettings.clockPosition,
                onPositionSelected = { onSettingsChanged(defaultSettings.copy(clockPosition = it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SizeSlider(
                label = stringResource(R.string.clock_size),
                value = defaultSettings.clockTextSizeSp,
                onValueChange = { onSettingsChanged(defaultSettings.copy(clockTextSizeSp = it)) },
                valueRange = 14f..60f,
                defaultValue = 32f,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.show_end_time),
            checked = defaultSettings.showEndTimeEnabled,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(showEndTimeEnabled = it)) },
        )
        if (defaultSettings.showEndTimeEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            SizeSlider(
                label = stringResource(R.string.end_time_size),
                value = defaultSettings.endTimeSizeSp,
                onValueChange = { onSettingsChanged(defaultSettings.copy(endTimeSizeSp = it)) },
                valueRange = 14f..60f,
                defaultValue = 32f,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.clockwise_mode),
            checked = defaultSettings.clockwiseModeEnabled,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(clockwiseModeEnabled = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.full_clock_mode),
            checked = defaultSettings.fullClockMode,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(fullClockMode = it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.clean_mode),
            checked = defaultSettings.cleanModeEnabled,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(cleanModeEnabled = it)) },
        )
        if (defaultSettings.cleanModeEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            SizeSlider(
                label = stringResource(R.string.clean_mode_auto_dismiss_time),
                value = defaultSettings.cleanModeAutoDismissSeconds.toFloat(),
                onValueChange = { onSettingsChanged(defaultSettings.copy(cleanModeAutoDismissSeconds = it.roundToInt())) },
                valueRange = CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS.toFloat()..CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS.toFloat(),
                defaultValue = CLEAN_MODE_AUTO_DISMISS_DEFAULT_SECONDS.toFloat(),
                steps = CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS - CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS - 1,
                valueText = "${defaultSettings.cleanModeAutoDismissSeconds}s",
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.timer_title_enabled),
            checked = defaultSettings.timerTitleEnabled,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(timerTitleEnabled = it)) },
        )
        if (defaultSettings.timerTitleEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            TitlePositionSelector(
                selectedPosition = defaultSettings.timerTitlePosition,
                onPositionSelected = { onSettingsChanged(defaultSettings.copy(timerTitlePosition = it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SizeSlider(
                label = stringResource(R.string.timer_title_size),
                value = defaultSettings.timerTitleTextSizeSp,
                onValueChange = { onSettingsChanged(defaultSettings.copy(timerTitleTextSizeSp = it)) },
                valueRange = 10f..48f,
                defaultValue = 16f,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.prompt_before_start),
            checked = defaultSettings.promptBeforeStart,
            onCheckedChange = { onSettingsChanged(defaultSettings.copy(promptBeforeStart = it)) },
        )
    }
}

@Composable
private fun CleanModeQuickAdjust(
    controlsExpanded: Boolean,
    controlsAlpha: Float,
    positiveOnly: Boolean,
    onExpand: () -> Unit,
    onAdjust: (Long) -> Unit,
) {
    Box(
        modifier = if (controlsExpanded && controlsAlpha > 0f) Modifier.fillMaxWidth()
                   else Modifier.fillMaxWidth().height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (controlsExpanded && controlsAlpha > 0f) {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                QuickAdjustRow(
                    modifier = Modifier.fillMaxWidth(),
                    onAdjust = onAdjust,
                    enabled = true,
                    positiveOnly = positiveOnly,
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
                    modifier = Modifier.size(52.dp).padding(2.dp),
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
private fun PreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FinishedSoundRouteSelector(selectedRoute: FinishedSoundRoute, onRouteSelected: (FinishedSoundRoute) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.finished_sound_route), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(label = stringResource(R.string.finished_sound_route_default), selected = selectedRoute == FinishedSoundRoute.Default, onClick = { onRouteSelected(FinishedSoundRoute.Default) })
            SelectorChip(label = stringResource(R.string.finished_sound_route_alarm), selected = selectedRoute == FinishedSoundRoute.Alarm, onClick = { onRouteSelected(FinishedSoundRoute.Alarm) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(label = stringResource(R.string.finished_sound_route_notification), selected = selectedRoute == FinishedSoundRoute.Notification, onClick = { onRouteSelected(FinishedSoundRoute.Notification) })
            SelectorChip(label = stringResource(R.string.finished_sound_route_media), selected = selectedRoute == FinishedSoundRoute.Media, onClick = { onRouteSelected(FinishedSoundRoute.Media) })
        }
    }
}

@Composable
private fun FinishedSoundVolumeSlider(volumePercent: Int, onVolumeChanged: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.finished_sound_volume, volumePercent.coerceIn(0, 100)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = volumePercent.coerceIn(0, 100).toFloat(),
            onValueChange = { onVolumeChanged(it.roundToInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
        )
    }
}

@Composable
private fun FinishedVibrationSelector(selectedMode: FinishedVibrationMode, onModeSelected: (FinishedVibrationMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.finished_vibration), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(label = stringResource(R.string.finished_vibration_off), selected = selectedMode == FinishedVibrationMode.Off, onClick = { onModeSelected(FinishedVibrationMode.Off) })
            SelectorChip(label = stringResource(R.string.finished_vibration_1m), selected = selectedMode == FinishedVibrationMode.OneMinute, onClick = { onModeSelected(FinishedVibrationMode.OneMinute) })
            SelectorChip(label = stringResource(R.string.finished_vibration_2m), selected = selectedMode == FinishedVibrationMode.TwoMinutes, onClick = { onModeSelected(FinishedVibrationMode.TwoMinutes) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(label = stringResource(R.string.finished_vibration_5m), selected = selectedMode == FinishedVibrationMode.FiveMinutes, onClick = { onModeSelected(FinishedVibrationMode.FiveMinutes) })
            SelectorChip(label = stringResource(R.string.finished_vibration_10m), selected = selectedMode == FinishedVibrationMode.TenMinutes, onClick = { onModeSelected(FinishedVibrationMode.TenMinutes) })
            SelectorChip(label = stringResource(R.string.finished_vibration_forever), selected = selectedMode == FinishedVibrationMode.Forever, onClick = { onModeSelected(FinishedVibrationMode.Forever) })
        }
    }
}

@Composable
private fun NotificationModeSelector(selectedMode: NotificationMode, onModeSelected: (NotificationMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.notification_mode_label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(label = stringResource(R.string.notification_mode_consolidated), selected = selectedMode == NotificationMode.Consolidated, onClick = { onModeSelected(NotificationMode.Consolidated) })
            SelectorChip(label = stringResource(R.string.notification_mode_individual), selected = selectedMode == NotificationMode.Individual, onClick = { onModeSelected(NotificationMode.Individual) })
        }
    }
}

@Composable
private fun NotificationUpdateIntervalSelector(
    intervalSeconds: Int,
    onIntervalSelected: (Int) -> Unit,
) {
    val steps = TimerController.NOTIFICATION_UPDATE_INTERVAL_STEPS
    val currentIndex = steps.indexOf(intervalSeconds).coerceAtLeast(0)
    val intervalLabel = if (intervalSeconds >= 60)
        stringResource(R.string.notification_update_interval_minute)
    else
        stringResource(R.string.notification_update_interval_seconds, intervalSeconds)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.notification_update_interval_label, intervalLabel),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { onIntervalSelected(steps[it.roundToInt()]) },
            valueRange = 0f..(steps.lastIndex.toFloat()),
            steps = steps.size - 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            steps.forEach { s ->
                Text(
                    text = if (s >= 60) "1m" else "${s}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ClockPositionSelector(selectedPosition: ClockPosition, onPositionSelected: (ClockPosition) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.clock_position), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(label = stringResource(R.string.clock_left), selected = selectedPosition == ClockPosition.Left, onClick = { onPositionSelected(ClockPosition.Left) })
            SelectorChip(label = stringResource(R.string.clock_center), selected = selectedPosition == ClockPosition.Center, onClick = { onPositionSelected(ClockPosition.Center) })
            SelectorChip(label = stringResource(R.string.clock_right), selected = selectedPosition == ClockPosition.Right, onClick = { onPositionSelected(ClockPosition.Right) })
        }
    }
}

@Composable
private fun TitlePositionSelector(selectedPosition: ClockPosition, onPositionSelected: (ClockPosition) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.timer_title_position), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(label = stringResource(R.string.clock_left), selected = selectedPosition == ClockPosition.Left, onClick = { onPositionSelected(ClockPosition.Left) })
            SelectorChip(label = stringResource(R.string.clock_center), selected = selectedPosition == ClockPosition.Center, onClick = { onPositionSelected(ClockPosition.Center) })
            SelectorChip(label = stringResource(R.string.clock_right), selected = selectedPosition == ClockPosition.Right, onClick = { onPositionSelected(ClockPosition.Right) })
        }
    }
}

@Composable
private fun SizeSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    steps: Int = 0,
    valueText: String = "${value.roundToInt()}sp",
) {
    val defaultFraction = ((defaultValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, modifier = Modifier.fillMaxWidth())
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                val thumbRadius = 10.dp.toPx()
                val markerX = thumbRadius + defaultFraction * (size.width - 2 * thumbRadius)
                val centerY = size.height / 2f
                drawLine(
                    color = primaryColor.copy(alpha = 0.55f),
                    start = Offset(markerX, centerY - 14.dp.toPx()),
                    end = Offset(markerX, centerY + 14.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(selectedMode: ThemeMode, onModeSelected: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.theme_mode), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.theme_light) to ThemeMode.Light,
                stringResource(R.string.theme_dark) to ThemeMode.Dark,
                stringResource(R.string.theme_system) to ThemeMode.System,
            ).forEach { (label, mode) ->
                SelectorChip(label = label, selected = selectedMode == mode, onClick = { onModeSelected(mode) })
            }
        }
    }
}

@Composable
private fun RowScope.SelectorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text = label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
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
private fun SectionCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun InfoBadge(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start, secondaryText: String? = null) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
            if (secondaryText != null) {
                Text(text = secondaryText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = textAlign, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CurrentTimeText(showSeconds: Boolean, clockPosition: ClockPosition, clockTextSizeSp: Float) {
    val formatter = if (showSeconds) rememberClockFormatter("h:mm:ss a") else rememberClockFormatter("h:mm a")
    var currentTimeText by remember(showSeconds) { mutableStateOf(LocalTime.now().format(formatter)) }

    LaunchedEffect(showSeconds) {
        while (true) {
            currentTimeText = LocalTime.now().format(formatter)
            delay(if (showSeconds) 1_000L else 15_000L)
        }
    }

    Text(
        text = currentTimeText,
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = clockTextSizeSp.sp, lineHeight = (clockTextSizeSp * 1.2f).sp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
        modifier = Modifier.fillMaxWidth(),
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
private fun EndTimeText(
    timer: TimerInstance,
    clockPosition: ClockPosition,
    textSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberClockFormatter("h:mm a")
    val endMillis = when (timer.status) {
        TimerStatus.Running  -> timer.targetEndTimeMillis
        TimerStatus.Paused   -> System.currentTimeMillis() + (timer.pausedRemainingMillis ?: timer.remainingMillis)
        TimerStatus.Idle     -> System.currentTimeMillis() + timer.selectedDurationMillis
        else                 -> null
    } ?: return
    var endText by remember(endMillis / 60_000) {
        val time = java.time.Instant.ofEpochMilli(endMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
        mutableStateOf("E: ${time.format(formatter)}")
    }
    LaunchedEffect(endMillis) {
        while (true) {
            val time = java.time.Instant.ofEpochMilli(endMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
            endText = "E: ${time.format(formatter)}"
            delay(30_000L)
        }
    }
    Text(
        text = endText,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontSize = textSizeSp.sp,
            lineHeight = (textSizeSp * 1.2f).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
        modifier = modifier.fillMaxWidth(),
        textAlign = when (clockPosition) {
            ClockPosition.Left   -> TextAlign.Start
            ClockPosition.Center -> TextAlign.Center
            ClockPosition.Right  -> TextAlign.End
        },
    )
}

@Composable
private fun TimerTitleDisplay(timer: TimerInstance, isCleanModeActive: Boolean, minimalUiAlpha: Float) {
    val settings = timer.settings
    if (!settings.timerTitleEnabled || timer.activeTimerName.isBlank()) return
    val alpha = if (isCleanModeActive && settings.timerTitleHideInCleanMode) minimalUiAlpha else 1f
    Text(
        text = timer.activeTimerName,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = settings.timerTitleTextSizeSp.sp,
            lineHeight = (settings.timerTitleTextSizeSp * 1.25f).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        textAlign = when (settings.timerTitlePosition) {
            ClockPosition.Left -> TextAlign.Start
            ClockPosition.Center -> TextAlign.Center
            ClockPosition.Right -> TextAlign.End
        },
    )
}
