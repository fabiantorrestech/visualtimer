package com.fabiantorrestech.visualtimerplus.ui.screen

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.backup.AutoBackupManager
import com.fabiantorrestech.visualtimerplus.backup.BackupManager
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.CLEAN_MODE_AUTO_DISMISS_DEFAULT_SECONDS
import com.fabiantorrestech.visualtimerplus.timer.withTimerIndex
import com.fabiantorrestech.visualtimerplus.timer.findNextAvailableTimerSlot
import com.fabiantorrestech.visualtimerplus.timer.CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS
import com.fabiantorrestech.visualtimerplus.timer.CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS
import com.fabiantorrestech.visualtimerplus.timer.ClockPosition
import com.fabiantorrestech.visualtimerplus.timer.FinishedSoundRoute
import com.fabiantorrestech.visualtimerplus.timer.FinishedVibrationMode
import com.fabiantorrestech.visualtimerplus.timer.NotificationMode
import com.fabiantorrestech.visualtimerplus.timer.OverlayLabelPosition
import com.fabiantorrestech.visualtimerplus.timer.OverlaySize
import com.fabiantorrestech.visualtimerplus.timer.OverlayStyle
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
import com.fabiantorrestech.visualtimerplus.util.formatApproxTime
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import com.fabiantorrestech.visualtimerplus.util.formatWallClockEndTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
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
    overlayPermissionGranted: Boolean,
    onOpenOverlayPermissionSettings: () -> Unit,
    accessibilityServiceConnected: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
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

    fun handleAddTimer() {
        val targetIndex = appState.findNextAvailableTimerSlot() ?: return
        // Set this before dispatching so LaunchedEffect(activeTimerIndex) doesn't double-animate.
        // Also ensures navigation happens even when activeTimerIndex doesn't change (reuse case).
        lastPagerSetActiveIndex = targetIndex
        onAction(TimerAction.AddTimer)
        coroutineScope.launch { pagerState.animateScrollToPage(targetIndex) }
    }

    var cleanModeUiAwake by rememberSaveable { mutableStateOf(false) }
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
        // If the settled page is already running in clean mode, mark as already-initialized so
        // the initial-show flow doesn't fire just because we navigated to a running timer.
        val settledTimer = appState.timers.getOrNull(pagerState.settledPage)
        cleanModeWasActive = settledTimer?.settings?.cleanModeEnabled == true &&
            (settledTimer.status == TimerStatus.Running || settledTimer.status == TimerStatus.Overtime)
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

    // pagerState.isScrollInProgress is included as a key so the effect re-evaluates when
    // scrolling stops — this prevents a mid-swipe isCleanModeActive transition from waking
    // the UI before the page has settled.
    LaunchedEffect(isCleanModeActive, pagerState.isScrollInProgress) {
        if (!isCleanModeActive) {
            cleanModeUiAwake = false
            cleanModeWasActive = false
        } else if (!cleanModeWasActive && !pagerState.isScrollInProgress) {
            cleanModeWasActive = true
            cleanModeUiAwake = true
            cleanModeActivityTick += 1
        }
    }

    LaunchedEffect(isCleanModeActive, cleanModeActivityTick, currentPageSettings.cleanModeAutoDismissSeconds, currentPageSettings.cleanModeAutoDismissEnabled) {
        if (!isCleanModeActive || !cleanModeUiAwake || !currentPageSettings.cleanModeAutoDismissEnabled) return@LaunchedEffect
        delay(currentPageSettings.cleanModeAutoDismissSeconds * 1_000L)
        cleanModeUiAwake = false
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
                overlayPermissionGranted = overlayPermissionGranted,
                onOpenOverlayPermissionSettings = onOpenOverlayPermissionSettings,
                accessibilityServiceConnected = accessibilityServiceConnected,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onRequestNotificationPermission = onNotificationPermissionNeeded,
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
                            if (change.isConsumed && !(cleanModeState.value && !cleanModeUiAwakeState.value)) { consumed = true; break }
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
                        cleanModeUiAwake = cleanModeUiAwake,
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
                        onCleanModeAdjust = { delta ->
                            pagedOnAction(TimerAction.AdjustDuration(delta))
                            awakenMinimalUi()
                        },
                    )
                } else {
                    PortraitLayout(
                        appState = appState,
                        timer = pageTimer,
                        isCleanModeActive = pageIsCleanModeActive,
                        showTopClock = pageShowTopClock,
                        minimalUiAlpha = minimalUiAlpha,
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
                        tapToToggleMinimalMode = appState.tapToToggleMinimalMode,
                        onCleanModeAdjust = { delta ->
                            pagedOnAction(TimerAction.AdjustDuration(delta))
                            awakenMinimalUi()
                        },
                    )
                }
            } else {
                AddTimerPage(onAdd = { handleAddTimer() })
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
                        elevation = FloatingActionButtonDefaults.elevation(),
                    ) {
                        Text(text = "−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                if (appState.timers.size < 20) {
                    SmallFloatingActionButton(
                        onClick = { handleAddTimer() },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(),
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
                db = db,
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

private fun TimerStatus.isPreviewEditable(): Boolean =
    this == TimerStatus.Idle || this == TimerStatus.Paused

private fun liveCountdownText(timer: TimerInstance, now: Long): String =
    when (timer.status) {
        TimerStatus.Running -> {
            val targetEndMillis = timer.targetEndTimeMillis ?: (now + timer.remainingMillis)
            (targetEndMillis - now).coerceAtLeast(0L).formatApproxTime()
        }
        TimerStatus.Overtime -> {
            val overtimeStartedAtMillis = timer.overtimeStartedAtMillis ?: now
            (now - overtimeStartedAtMillis).coerceAtLeast(0L).formatClockTime()
        }
        else -> timer.displayMillis.formatClockTime()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortraitLayout(
    appState: AppState,
    timer: TimerInstance,
    isCleanModeActive: Boolean,
    showTopClock: Boolean,
    minimalUiAlpha: Float,
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
    tapToToggleMinimalMode: Boolean,
    onCleanModeAdjust: (Long) -> Unit,
) {
    val settings = timer.settings
    var isDragging by remember { mutableStateOf(false) }
    var previewDurationMillis by remember(timer.id) { mutableStateOf<Long?>(null) }
    val isPreviewEditable = timer.status.isPreviewEditable()
    val effectivePreviewDurationMillis = if (isPreviewEditable) previewDurationMillis else null
    val minimalControlsInteractable = !isCleanModeActive || minimalUiAlpha > 0.99f
    val effectiveDisplayMillis = effectivePreviewDurationMillis ?: timer.displayMillis
    val effectiveDurationForCommit = effectivePreviewDurationMillis ?: timer.selectedDurationMillis
    val resolvedIdleTimer = if (
        timer.status == TimerStatus.Idle &&
        effectiveDurationForCommit != timer.selectedDurationMillis
    ) {
        timer.copy(
            selectedDurationMillis = effectiveDurationForCommit,
            remainingMillis = effectiveDurationForCommit,
        )
    } else {
        timer
    }

    LaunchedEffect(timer.status) {
        if (!timer.status.isPreviewEditable()) {
            isDragging = false
            previewDurationMillis = null
        }
    }

    LaunchedEffect(isDragging, isPreviewEditable, effectivePreviewDurationMillis, timer.displayMillis) {
        if (
            isPreviewEditable &&
            !isDragging &&
            effectivePreviewDurationMillis != null &&
            effectivePreviewDurationMillis == timer.displayMillis
        ) {
            previewDurationMillis = null
        }
    }

    fun commitPreviewDuration(durationMillis: Long) {
        isDragging = false
        previewDurationMillis = durationMillis
        if (durationMillis != timer.selectedDurationMillis) {
            onAction(TimerAction.SetDurationExact(durationMillis))
        }
    }

    fun handleIdleCenterTap() {
        val resolvedDuration = previewDurationMillis ?: timer.selectedDurationMillis
        if (resolvedDuration != timer.selectedDurationMillis) {
            onAction(TimerAction.SetDurationExact(resolvedDuration))
        }
        if (resolvedDuration == 0L) onOpenDurationPicker() else onStartWithPromptCheck()
    }

    fun handleTimerControlAction(action: TimerAction) {
        if (action is TimerAction.Start) {
            if (effectiveDurationForCommit != timer.selectedDurationMillis) {
                onAction(TimerAction.SetDurationExact(effectiveDurationForCommit))
            }
            if (effectiveDurationForCommit > 0L) onStartWithPromptCheck()
            return
        }
        onAction(action)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.displayCutout)
                    .union(WindowInsets.navigationBars),
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Timer arc — anchored at center so top/bottom content never shifts its position
        HeroTimerCard(
            timer = timer,
            displayAlpha = if (isCleanModeActive) minimalUiAlpha else 1f,
            previewDurationMillis = effectivePreviewDurationMillis,
            onPreviewDurationChanged = { previewDurationMillis = it },
            onDragCommit = ::commitPreviewDuration,
            onCenterTap = {
                when (timer.status) {
                    TimerStatus.Idle    -> handleIdleCenterTap()
                    TimerStatus.Running -> onAction(TimerAction.Pause())
                    TimerStatus.Paused  -> onAction(TimerAction.Resume())
                    else                -> {}
                }
            },
            onCenterLongPress = onOpenDurationPicker,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            isOledMode = appState.isOledMode,
            onDragActiveChanged = { dragging ->
                isDragging = dragging
            },
        )

        // Top overlay: clock, end-time, status row, name chip
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val clockAlpha = if (isCleanModeActive && settings.hideClockInCleanMode) minimalUiAlpha else 1f
            if (showTopClock) {
                Box(modifier = Modifier.alpha(clockAlpha)) {
                    Crossfade(targetState = isDragging, label = "portraitDragReadout") { dragging ->
                        if (dragging) {
                            Text(
                                text = effectiveDisplayMillis.formatClockTime(),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = settings.clockTextSizeSp.sp,
                                    lineHeight = (settings.clockTextSizeSp * 1.2f).sp,
                                ),
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = when (settings.clockPosition) {
                                    ClockPosition.Left -> TextAlign.Start
                                    ClockPosition.Center -> TextAlign.Center
                                    ClockPosition.Right -> TextAlign.End
                                },
                            )
                        } else {
                            CurrentTimeText(
                                showSeconds = settings.showClockSecondsEnabled,
                                clockPosition = settings.clockPosition,
                                clockTextSizeSp = settings.clockTextSizeSp,
                            )
                        }
                    }
                }
            } else {
                AnimatedVisibility(visible = isDragging) {
                    Text(
                        text = effectiveDisplayMillis.formatClockTime(),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = settings.clockTextSizeSp.sp,
                            lineHeight = (settings.clockTextSizeSp * 1.2f).sp,
                        ),
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = when (settings.clockPosition) {
                            ClockPosition.Left -> TextAlign.Start
                            ClockPosition.Center -> TextAlign.Center
                            ClockPosition.Right -> TextAlign.End
                        },
                    )
                }
            }

            val showEndTime = timer.status != TimerStatus.Finished && effectiveDisplayMillis > 0L
            AnimatedVisibility(
                visible = showEndTime,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                EndTimeText(
                    timer = timer,
                    clockPosition = settings.clockPosition,
                    textSizeSp = settings.endTimeSizeSp,
                    showSeconds = true,
                    durationOverrideMillis = if (isDragging && isPreviewEditable) effectiveDurationForCommit else null,
                    modifier = Modifier.alpha(
                        if (isCleanModeActive && settings.hideClockInCleanMode) minimalUiAlpha else 1f,
                    ),
                )
            }

            val showAnyTopRow = showTopClock || showEndTime || isDragging
            if (showAnyTopRow) Spacer(modifier = Modifier.height(4.dp))
            else Spacer(modifier = Modifier.height(8.dp))

            StatusAndLogRow(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                enabled = minimalControlsInteractable,
                onOpenLog = onOpenLog,
            )

            Spacer(modifier = Modifier.height(6.dp))

            TimerNameChip(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                enabled = minimalControlsInteractable,
                onNameChipClick = onNameChipClick,
                onClearPreset = onClearPreset,
            )
        } // end top overlay Column

        // Bottom overlay: title, presets (idle only), controls, quick adjust
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimerTitleDisplay(timer = timer, isCleanModeActive = isCleanModeActive, minimalUiAlpha = minimalUiAlpha)

            CleanModeCanvasAdjustBar(
                visible = isCleanModeActive,
                controlsAlpha = minimalUiAlpha,
                enabled = minimalControlsInteractable,
                positiveOnly = timer.status == TimerStatus.Overtime,
                onAdjust = onCleanModeAdjust,
            )
            if (isCleanModeActive) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!isCleanModeActive) {
                AnimatedVisibility(visible = timer.status == TimerStatus.Idle) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionCard(title = stringResource(R.string.presets)) {
                            PresetRow(
                                modifier = Modifier.fillMaxWidth(),
                                onPresetSelected = { onAction(TimerAction.SetDuration(it)) },
                                enabled = true,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                AnimatedVisibility(visible = timer.status != TimerStatus.Idle) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionCard(title = stringResource(R.string.adjust_timer)) {
                            QuickAdjustRow(
                                modifier = Modifier.fillMaxWidth(),
                                onAdjust = onAdjust,
                                enabled = true,
                                positiveOnly = timer.status == TimerStatus.Overtime,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            TimerControls(
                timer = resolvedIdleTimer,
                onAction = ::handleTimerControlAction,
                enabled = minimalControlsInteractable,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
            )
        } // end bottom overlay Column

        HiddenBottomControlsRestoreLayer(
            visible = isCleanModeActive && !cleanModeUiAwake && tapToToggleMinimalMode,
            onRestore = awakenMinimalUi,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )

        // Gear icon — top-right corner, consistent in both running and non-running modes
        Surface(
            onClick = onOpenSettings,
            enabled = minimalControlsInteractable,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .alpha(if (isCleanModeActive) minimalUiAlpha else 1f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = "⚙", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } // end outer Box
}


@Composable
private fun LandscapeLayout(
    appState: AppState,
    timer: TimerInstance,
    isCleanModeActive: Boolean,
    showTopClock: Boolean,
    minimalUiAlpha: Float,
    cleanModeUiAwake: Boolean,
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
    onCleanModeAdjust: (Long) -> Unit,
) {
    val settings = timer.settings
    var isDragging by remember { mutableStateOf(false) }
    var previewDurationMillis by remember(timer.id) { mutableStateOf<Long?>(null) }
    val isPreviewEditable = timer.status.isPreviewEditable()
    val effectivePreviewDurationMillis = if (isPreviewEditable) previewDurationMillis else null
    val minimalControlsInteractable = !isCleanModeActive || minimalUiAlpha > 0.99f
    val effectiveDisplayMillis = effectivePreviewDurationMillis ?: timer.displayMillis
    val effectiveDurationForCommit = effectivePreviewDurationMillis ?: timer.selectedDurationMillis
    val resolvedIdleTimer = if (
        timer.status == TimerStatus.Idle &&
        effectiveDurationForCommit != timer.selectedDurationMillis
    ) {
        timer.copy(
            selectedDurationMillis = effectiveDurationForCommit,
            remainingMillis = effectiveDurationForCommit,
        )
    } else {
        timer
    }

    LaunchedEffect(timer.status) {
        if (!timer.status.isPreviewEditable()) {
            isDragging = false
            previewDurationMillis = null
        }
    }

    LaunchedEffect(isDragging, isPreviewEditable, effectivePreviewDurationMillis, timer.displayMillis) {
        if (
            isPreviewEditable &&
            !isDragging &&
            effectivePreviewDurationMillis != null &&
            effectivePreviewDurationMillis == timer.displayMillis
        ) {
            previewDurationMillis = null
        }
    }

    fun commitPreviewDuration(durationMillis: Long) {
        isDragging = false
        previewDurationMillis = durationMillis
        if (durationMillis != timer.selectedDurationMillis) {
            onAction(TimerAction.SetDurationExact(durationMillis))
        }
    }

    fun handleIdleCenterTap() {
        val resolvedDuration = previewDurationMillis ?: timer.selectedDurationMillis
        if (resolvedDuration != timer.selectedDurationMillis) {
            onAction(TimerAction.SetDurationExact(resolvedDuration))
        }
        if (resolvedDuration == 0L) onOpenDurationPicker() else onStartWithPromptCheck()
    }

    fun handleTimerControlAction(action: TimerAction) {
        if (action is TimerAction.Start) {
            if (effectiveDurationForCommit != timer.selectedDurationMillis) {
                onAction(TimerAction.SetDurationExact(effectiveDurationForCommit))
            }
            if (effectiveDurationForCommit > 0L) onStartWithPromptCheck()
            return
        }
        onAction(action)
    }
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
                previewDurationMillis = effectivePreviewDurationMillis,
                onPreviewDurationChanged = { previewDurationMillis = it },
                onDragCommit = ::commitPreviewDuration,
                onCenterTap = {
                    when (timer.status) {
                        TimerStatus.Idle    -> handleIdleCenterTap()
                        TimerStatus.Running -> onAction(TimerAction.Pause())
                        TimerStatus.Paused  -> onAction(TimerAction.Resume())
                        else                -> {}
                    }
                },
                onCenterLongPress = onOpenDurationPicker,
                modifier = Modifier.fillMaxSize(),
                isOledMode = appState.isOledMode,
                onDragActiveChanged = { dragging ->
                    isDragging = dragging
                },
            )
        }

        // Right pane: scrollable controls with the label cemented at the bottom.
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState(), enabled = !isCleanModeActive || cleanModeUiAwake)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTopClock) {
                Crossfade(targetState = isDragging, label = "landscapeDragReadout") { dragging ->
                    if (dragging) {
                        Text(
                            text = effectiveDisplayMillis.formatClockTime(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = settings.clockTextSizeSp.sp,
                                lineHeight = (settings.clockTextSizeSp * 1.2f).sp,
                            ),
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = when (settings.clockPosition) {
                                ClockPosition.Left -> TextAlign.Start
                                ClockPosition.Center -> TextAlign.Center
                                ClockPosition.Right -> TextAlign.End
                            },
                        )
                    } else {
                        CurrentTimeText(
                            showSeconds = settings.showClockSecondsEnabled,
                            clockPosition = settings.clockPosition,
                            clockTextSizeSp = settings.clockTextSizeSp,
                        )
                    }
                }
            } else {
                AnimatedVisibility(visible = isDragging) {
                    Text(
                        text = effectiveDisplayMillis.formatClockTime(),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = settings.clockTextSizeSp.sp,
                            lineHeight = (settings.clockTextSizeSp * 1.2f).sp,
                        ),
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = when (settings.clockPosition) {
                            ClockPosition.Left -> TextAlign.Start
                            ClockPosition.Center -> TextAlign.Center
                            ClockPosition.Right -> TextAlign.End
                        },
                    )
                }
            }

            val showEndTimeLandscape = timer.status != TimerStatus.Finished && effectiveDisplayMillis > 0L
            AnimatedVisibility(
                visible = showEndTimeLandscape,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                EndTimeText(
                    timer = timer,
                    clockPosition = settings.clockPosition,
                    textSizeSp = settings.endTimeSizeSp,
                    showSeconds = true,
                    durationOverrideMillis = if (isDragging && isPreviewEditable) effectiveDurationForCommit else null,
                    modifier = Modifier.alpha(
                        if (isCleanModeActive && settings.hideClockInCleanMode) minimalUiAlpha else 1f,
                    ),
                )
            }

            val showAnyTopRowLandscape = showTopClock || showEndTimeLandscape
            if (showAnyTopRowLandscape) Spacer(modifier = Modifier.height(8.dp))

            StatusAndLogRow(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                enabled = minimalControlsInteractable,
                onOpenLog = onOpenLog,
            )

            Spacer(modifier = Modifier.height(6.dp))

            TimerNameChip(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                enabled = minimalControlsInteractable,
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
                SectionCard(modifier = Modifier.alpha(minimalUiAlpha)) {
                    QuickAdjustRow(
                        modifier = Modifier.fillMaxWidth(),
                        onAdjust = onCleanModeAdjust,
                        enabled = minimalControlsInteractable,
                        positiveOnly = timer.status == TimerStatus.Overtime,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionCard(modifier = Modifier.alpha(if (isCleanModeActive) minimalUiAlpha else 1f)) {
                TimerControls(
                    timer = resolvedIdleTimer,
                    onAction = ::handleTimerControlAction,
                    enabled = minimalControlsInteractable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = onOpenSettings,
                enabled = minimalControlsInteractable,
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
        // Label cemented at the bottom-right of the right pane, always visible.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            TimerTitleDisplay(
                timer = timer,
                isCleanModeActive = isCleanModeActive,
                minimalUiAlpha = minimalUiAlpha,
                forceAlpha = if (isCleanModeActive) 1f - minimalUiAlpha else 1f,
            )
        }
        } // end right pane Box
    }
}

@Composable
private fun StatusAndLogRow(
    timer: TimerInstance,
    isCleanModeActive: Boolean,
    minimalUiAlpha: Float,
    enabled: Boolean,
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
            enabled = enabled,
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
    enabled: Boolean,
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
            enabled = enabled,
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
                enabled = enabled,
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
    previewDurationMillis: Long?,
    onPreviewDurationChanged: (Long) -> Unit,
    onDragCommit: (Long) -> Unit,
    onCenterTap: () -> Unit,
    onCenterLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    isOledMode: Boolean = false,
    onDragActiveChanged: (Boolean) -> Unit = {},
) {
    val effectiveDisplayMillis = previewDurationMillis ?: timer.displayMillis
    // Drive countdown text at wall-clock second boundaries so it ticks in sync with CurrentTimeText.
    var syncedCountdownText by remember(
        timer.status,
        timer.targetEndTimeMillis,
        timer.overtimeStartedAtMillis,
    ) {
        mutableStateOf(liveCountdownText(timer, System.currentTimeMillis()))
    }
    LaunchedEffect(timer.targetEndTimeMillis, timer.overtimeStartedAtMillis, timer.status) {
        if (timer.status != TimerStatus.Running && timer.status != TimerStatus.Overtime) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            syncedCountdownText = liveCountdownText(timer, now)
            delay(1_000L - (now % 1_000L))
        }
    }
    val countdownText = if (timer.status == TimerStatus.Running || timer.status == TimerStatus.Overtime) {
        syncedCountdownText
    } else {
        effectiveDisplayMillis.formatClockTime()
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        VisualTimerCanvas(
            modifier = Modifier.fillMaxSize(),
            timer = timer,
            onPreviewDurationChanged = onPreviewDurationChanged,
            onDragCommit = onDragCommit,
            isOledMode = isOledMode,
            onDragActiveChanged = onDragActiveChanged,
            displayMillisOverride = previewDurationMillis,
        )
        Box(
            modifier = Modifier
                .wrapContentSize()
                .combinedClickable(
                    onClick = onCenterTap,
                    onLongClick = onCenterLongPress,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                val centerFontSizeSp = if (countdownText.count { it == ':' } >= 2)
                    timer.settings.centerTimeSizeSp * 0.9f
                else
                    timer.settings.centerTimeSizeSp
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = centerFontSizeSp.sp,
                        lineHeight = (centerFontSizeSp * 1.2f).sp,
                        shadow = Shadow(
                            color = Color.White.copy(alpha = 0.85f),
                            offset = Offset.Zero,
                            blurRadius = 6f,
                        ),
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
    overlayPermissionGranted: Boolean,
    onOpenOverlayPermissionSettings: () -> Unit,
    accessibilityServiceConnected: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val settings = timer.settings
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).appDao() }
    val backupScope = rememberCoroutineScope()
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAutomationDialog by remember { mutableStateOf(false) }

    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var exactAlarmGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        )
    }
    var fullScreenGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                exactAlarmGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
                fullScreenGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                    context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var autoBackupLocationUri by remember {
        mutableStateOf(AutoBackupManager.getSavedLocationUri(context))
    }

    val autoBackupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        AutoBackupManager.saveLocationUri(context, uri)
        autoBackupLocationUri = uri.toString()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            try {
                val (folders, presets) = withContext(Dispatchers.IO) {
                    dao.getAllFolders() to dao.getAllPresets()
                }
                val json = BackupManager.buildBackup(context, folders, presets).toString(2)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                backupStatusMessage = context.getString(R.string.backup_export_success)
            } catch (e: Exception) {
                backupStatusMessage = "Export failed: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                        ?: throw IllegalStateException("Could not read file.")
                }
                BackupManager.parseBackup(json)
                pendingImportJson = json
            } catch (e: Exception) {
                backupStatusMessage = "Import failed: ${e.message}"
            }
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch(Dispatchers.IO) {
            val displayName = context.contentResolver.query(uri, null, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
            val destDir = File(context.filesDir, "fonts").also { it.mkdirs() }
            val destFile = File(destDir, "custom_font")
            context.contentResolver.openInputStream(uri)?.use { it.copyTo(destFile.outputStream()) }
            onAction(TimerAction.SetCustomFont(destFile.absolutePath, displayName))
        }
    }

    pendingImportJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportJson = null
                    backupScope.launch {
                        try {
                            val root = BackupManager.parseBackup(json)
                            withContext(Dispatchers.IO) {
                                BackupManager.applyBackup(context, root, dao)
                            }
                            backupStatusMessage = context.getString(R.string.backup_import_success)
                        } catch (e: Exception) {
                            backupStatusMessage = "Import failed: ${e.message}"
                        }
                    }
                }) { Text(stringResource(R.string.backup_import)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAutomationDialog) {
        AutomationInfoDialog(onDismiss = { showAutomationDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.settings_tab_settings)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.settings_tab_permissions)) },
            )
        }

        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp),
            ) {
            // ── Automation ────────────────────────────────────────────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.automation_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showAutomationDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info_outline),
                            contentDescription = stringResource(R.string.automation_dialog_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.automation_auto_open_after_quick_start),
                    checked = appState.autoOpenAppAfterQuickStart,
                    onCheckedChange = { onAction(TimerAction.SetAutoOpenAppAfterQuickStart(it)) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
            Column {
                Text(
                    text = stringResource(R.string.custom_font),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = appState.customFontDisplayName ?: stringResource(R.string.custom_font_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fontPickerLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.custom_font_select))
                    }
                    if (appState.customFontPath != null) {
                        OutlinedButton(onClick = { onAction(TimerAction.SetCustomFont(null, null)) }) {
                            Text(stringResource(R.string.custom_font_reset))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.custom_font_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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

        Spacer(modifier = Modifier.height(12.dp))

        SectionCard {
            PreferenceToggle(
                label = stringResource(R.string.overlay_enabled),
                checked = appState.overlayEnabled,
                onCheckedChange = { onAction(TimerAction.SetOverlayEnabled(it)) },
            )
            if (appState.overlayEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                PreferenceToggle(
                    label = stringResource(R.string.overlay_show_on_lockscreen),
                    description = stringResource(R.string.overlay_show_on_lockscreen_desc),
                    checked = appState.overlayShowOnLockscreen,
                    onCheckedChange = { onAction(TimerAction.SetOverlayShowOnLockscreen(it)) },
                )
                Spacer(modifier = Modifier.height(4.dp))
                PreferenceToggle(
                    label = stringResource(R.string.overlay_show_timer_name),
                    checked = appState.overlayShowTimerName,
                    onCheckedChange = { onAction(TimerAction.SetOverlayShowTimerName(it)) },
                )
                if (appState.overlayShowTimerName) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OverlayLabelPositionSelector(
                        selectedPosition = appState.overlayTimerNamePosition,
                        onPositionSelected = { onAction(TimerAction.SetOverlayTimerNamePosition(it)) },
                    )
                }
                if (appState.overlayShowOnLockscreen && !accessibilityServiceConnected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.overlay_accessibility_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AssistChip(
                        onClick = onOpenAccessibilitySettings,
                        label = { Text(stringResource(R.string.overlay_open_accessibility_settings)) },
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OverlaySizeSelector(
                selectedSize = appState.overlaySize,
                onSizeSelected = { onAction(TimerAction.SetOverlaySize(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            OverlayStyleSelector(
                selectedStyle = appState.overlayStyle,
                onStyleSelected = { onAction(TimerAction.SetOverlayStyle(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (overlayPermissionGranted) {
                    stringResource(R.string.overlay_permission_granted)
                } else {
                    stringResource(R.string.overlay_permission_required)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!overlayPermissionGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                AssistChip(
                    onClick = onOpenOverlayPermissionSettings,
                    label = { Text(stringResource(R.string.overlay_open_settings)) },
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        var currentSettingsExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { currentSettingsExpanded = !currentSettingsExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.current_timer_settings),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (currentSettingsExpanded) "▲" else "▼",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = currentSettingsExpanded) {
        Column {
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
            SizeSlider(
                label = stringResource(R.string.end_time_size),
                value = settings.endTimeSizeSp,
                onValueChange = { onAction(TimerAction.SetEndTimeSizeSp(it)) },
                valueRange = 14f..60f,
                defaultValue = 32f,
            )
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
                label = stringResource(R.string.full_clock_mode),
                checked = settings.fullClockMode,
                onCheckedChange = { onAction(TimerAction.SetFullClockMode(it)) },
            )
            if (!settings.fullClockMode) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.clockwise_mode),
                    checked = settings.clockwiseModeEnabled,
                    onCheckedChange = { onAction(TimerAction.SetClockwiseModeEnabled(it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.show_direction_indicator),
                    checked = settings.showDirectionIndicator,
                    onCheckedChange = { onAction(TimerAction.SetShowDirectionIndicator(it)) },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.clean_mode),
                checked = settings.cleanModeEnabled,
                onCheckedChange = { onAction(TimerAction.SetCleanModeEnabled(it)) },
            )
            if (settings.cleanModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceToggle(
                    label = stringResource(R.string.clean_mode_auto_dismiss_enabled),
                    checked = settings.cleanModeAutoDismissEnabled,
                    onCheckedChange = { onAction(TimerAction.SetCleanModeAutoDismissEnabled(it)) },
                )
                if (settings.cleanModeAutoDismissEnabled) {
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
        } // Column
        } // AnimatedVisibility(currentSettingsExpanded)

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

        Spacer(modifier = Modifier.height(12.dp))

        // ── Backup & Restore ──────────────────────────────────────────────────
        SectionCard(title = stringResource(R.string.backup_restore_title)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("visualtimer_backup_$today.json") },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.backup_import)) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_backup_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.auto_backup_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = appState.autoBackupEnabled,
                    onCheckedChange = { onAction(TimerAction.SetAutoBackupEnabled(it)) },
                    enabled = autoBackupLocationUri != null,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { autoBackupFolderLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (autoBackupLocationUri == null)
                        stringResource(R.string.auto_backup_set_location)
                    else
                        stringResource(R.string.auto_backup_change_location)
                )
            }

            autoBackupLocationUri?.let { uriString ->
                Spacer(modifier = Modifier.height(4.dp))
                val displayPath = Uri.parse(uriString).lastPathSegment ?: uriString
                Text(
                    text = stringResource(R.string.auto_backup_location_prefix) + displayPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            backupStatusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

            Spacer(modifier = Modifier.height(24.dp))
            } // closes inner Settings Column
        } else {
            PermissionsTabContent(
                notifGranted = notifGranted,
                overlayGranted = overlayPermissionGranted,
                exactAlarmGranted = exactAlarmGranted,
                fullScreenGranted = fullScreenGranted,
                accessibilityServiceConnected = accessibilityServiceConnected,
                onRequestNotification = onRequestNotificationPermission,
                onOpenOverlaySettings = onOpenOverlayPermissionSettings,
                onOpenExactAlarmSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                },
                onOpenFullScreenSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                },
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            )
        }
    } // closes outer Column
} // closes SettingsSheetContent

@Composable
private fun PermissionsTabContent(
    notifGranted: Boolean,
    overlayGranted: Boolean,
    exactAlarmGranted: Boolean,
    fullScreenGranted: Boolean,
    accessibilityServiceConnected: Boolean,
    onRequestNotification: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = stringResource(R.string.perm_notifications_title),
                description = stringResource(R.string.perm_notifications_desc),
                granted = notifGranted,
                grantLabel = stringResource(R.string.perm_action_grant),
                onGrant = onRequestNotification,
            )
        }
        PermissionRow(
            title = stringResource(R.string.perm_overlay_title),
            description = stringResource(R.string.perm_overlay_desc),
            granted = overlayGranted,
            grantLabel = stringResource(R.string.perm_action_open_settings),
            onGrant = onOpenOverlaySettings,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow(
                title = stringResource(R.string.perm_exact_alarm_title),
                description = stringResource(R.string.perm_exact_alarm_desc),
                granted = exactAlarmGranted,
                grantLabel = stringResource(R.string.perm_action_open_settings),
                onGrant = onOpenExactAlarmSettings,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PermissionRow(
                title = stringResource(R.string.perm_full_screen_title),
                description = stringResource(R.string.perm_full_screen_desc),
                granted = fullScreenGranted,
                grantLabel = stringResource(R.string.perm_action_open_settings),
                onGrant = onOpenFullScreenSettings,
            )
        }
        PermissionRow(
            title = stringResource(R.string.perm_accessibility_title),
            description = stringResource(R.string.perm_accessibility_desc),
            granted = accessibilityServiceConnected,
            grantLabel = stringResource(R.string.perm_action_open_settings),
            onGrant = onOpenAccessibilitySettings,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    grantLabel: String,
    onGrant: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (granted) {
                Text(
                    text = stringResource(R.string.perm_granted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                AssistChip(
                    onClick = onGrant,
                    label = { Text(grantLabel, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AutomationInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automation_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AutomationDialogSection(title = stringResource(R.string.automation_section_quick_timer)) {
                    AutomationMonoLine("Action: com.fabiantorrestech.visualtimerplus.ACTION_QUICK_TIMER")
                    AutomationMonoLine("Target: QuickTimerActivity")
                    AutomationMonoLine("Type: Activity intent")
                }
                Spacer(modifier = Modifier.height(16.dp))
                AutomationDialogSection(title = stringResource(R.string.automation_section_control)) {
                    AutomationMonoLine("Target service: com.fabiantorrestech.visualtimerplus.timer.TimerService")
                    Spacer(modifier = Modifier.height(4.dp))
                    AutomationMonoLine("…action.START — start the active timer")
                    AutomationMonoLine("…action.PAUSE — pause")
                    AutomationMonoLine("…action.RESUME — resume")
                    AutomationMonoLine("…action.RESET — reset to idle")
                    AutomationMonoLine("…action.RESTART — restart from original duration")
                    Spacer(modifier = Modifier.height(4.dp))
                    AutomationMonoLine("Extra (optional): timer_index (int, 0-based)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                AutomationDialogSection(title = stringResource(R.string.automation_section_macrodroid)) {
                    AutomationMonoLine("1. Add Action → Device Actions → Send Intent")
                    AutomationMonoLine("2. Target: Activity")
                    AutomationMonoLine("3. Action:    …ACTION_QUICK_TIMER")
                    AutomationMonoLine("4. Package:   com.fabiantorrestech.visualtimerplus")
                    AutomationMonoLine("5. Component: …ui.screen.QuickTimerActivity")
                    AutomationMonoLine("6. Data URI:  (leave EMPTY — not the class name)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                AutomationDialogSection(title = stringResource(R.string.automation_section_tasker)) {
                    AutomationMonoLine("1. New Task → + → System → Send Intent")
                    AutomationMonoLine("2. Action:  …ACTION_QUICK_TIMER")
                    AutomationMonoLine("3. Package: com.fabiantorrestech.visualtimerplus")
                    AutomationMonoLine("4. Class:   …ui.screen.QuickTimerActivity")
                    AutomationMonoLine("5. Target:  Activity")
                }
                Spacer(modifier = Modifier.height(16.dp))
                AutomationDialogSection(title = stringResource(R.string.automation_section_keymapper)) {
                    AutomationMonoLine("1. New Key Map → Add Action → Intent")
                    AutomationMonoLine("2. Target:    Activity")
                    AutomationMonoLine("3. Action:    …ACTION_QUICK_TIMER")
                    AutomationMonoLine("4. Package:   com.fabiantorrestech.visualtimerplus")
                    AutomationMonoLine("5. Component: …ui.screen.QuickTimerActivity")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automation_dialog_close))
            }
        },
    )
}

@Composable
private fun AutomationDialogSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    content()
}

@Composable
private fun AutomationMonoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DefaultTimerSettingsSection(
    defaultSettings: com.fabiantorrestech.visualtimerplus.timer.TimerSettings,
    defaultDurationMillis: Long,
    onSettingsChanged: (com.fabiantorrestech.visualtimerplus.timer.TimerSettings) -> Unit,
    onSetDefaultDuration: () -> Unit,
) {
    // Local draft: changes accumulate here and are committed only on "Save Defaults"
    var draft by remember { mutableStateOf(defaultSettings) }

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
            checked = draft.soundEnabled,
            onCheckedChange = { draft = draft.copy(soundEnabled = it) },
        )
        if (draft.soundEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            FinishedSoundRouteSelector(
                selectedRoute = draft.finishedSoundRoute,
                onRouteSelected = { draft = draft.copy(finishedSoundRoute = it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            FinishedSoundVolumeSlider(
                volumePercent = draft.finishedSoundVolumePercent,
                onVolumeChanged = { draft = draft.copy(finishedSoundVolumePercent = it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.ignore_silent_mode),
                checked = draft.ignoreSilentMode,
                onCheckedChange = { draft = draft.copy(ignoreSilentMode = it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.override_muted_system_volume),
                checked = draft.overrideMutedSystemVolume,
                onCheckedChange = { draft = draft.copy(overrideMutedSystemVolume = it) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FinishedVibrationSelector(
            selectedMode = draft.finishedVibrationMode,
            onModeSelected = { draft = draft.copy(finishedVibrationMode = it) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.keep_screen_awake),
            checked = draft.keepScreenAwake,
            onCheckedChange = { draft = draft.copy(keepScreenAwake = it) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SizeSlider(
            label = stringResource(R.string.center_time_size),
            value = draft.centerTimeSizeSp,
            onValueChange = { draft = draft.copy(centerTimeSizeSp = it) },
            valueRange = 20f..80f,
            defaultValue = 36f,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.show_current_time),
            checked = draft.showCurrentTimeEnabled,
            onCheckedChange = { draft = draft.copy(showCurrentTimeEnabled = it, showClockSecondsEnabled = if (it) draft.showClockSecondsEnabled else false) },
        )
        if (draft.showCurrentTimeEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.show_seconds),
                checked = draft.showClockSecondsEnabled,
                onCheckedChange = { draft = draft.copy(showClockSecondsEnabled = it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            ClockPositionSelector(
                selectedPosition = draft.clockPosition,
                onPositionSelected = { draft = draft.copy(clockPosition = it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SizeSlider(
                label = stringResource(R.string.clock_size),
                value = draft.clockTextSizeSp,
                onValueChange = { draft = draft.copy(clockTextSizeSp = it) },
                valueRange = 14f..60f,
                defaultValue = 32f,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SizeSlider(
            label = stringResource(R.string.end_time_size),
            value = draft.endTimeSizeSp,
            onValueChange = { draft = draft.copy(endTimeSizeSp = it) },
            valueRange = 14f..60f,
            defaultValue = 32f,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.full_clock_mode),
            checked = draft.fullClockMode,
            onCheckedChange = { draft = draft.copy(fullClockMode = it) },
        )
        if (!draft.fullClockMode) {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.clockwise_mode),
                checked = draft.clockwiseModeEnabled,
                onCheckedChange = { draft = draft.copy(clockwiseModeEnabled = it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.show_direction_indicator),
                checked = draft.showDirectionIndicator,
                onCheckedChange = { draft = draft.copy(showDirectionIndicator = it) },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.clean_mode),
            checked = draft.cleanModeEnabled,
            onCheckedChange = { draft = draft.copy(cleanModeEnabled = it) },
        )
        if (draft.cleanModeEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceToggle(
                label = stringResource(R.string.clean_mode_auto_dismiss_enabled),
                checked = draft.cleanModeAutoDismissEnabled,
                onCheckedChange = { draft = draft.copy(cleanModeAutoDismissEnabled = it) },
            )
            if (draft.cleanModeAutoDismissEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                SizeSlider(
                    label = stringResource(R.string.clean_mode_auto_dismiss_time),
                    value = draft.cleanModeAutoDismissSeconds.toFloat(),
                    onValueChange = { draft = draft.copy(cleanModeAutoDismissSeconds = it.roundToInt()) },
                    valueRange = CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS.toFloat()..CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS.toFloat(),
                    defaultValue = CLEAN_MODE_AUTO_DISMISS_DEFAULT_SECONDS.toFloat(),
                    steps = CLEAN_MODE_AUTO_DISMISS_MAX_SECONDS - CLEAN_MODE_AUTO_DISMISS_MIN_SECONDS - 1,
                    valueText = "${draft.cleanModeAutoDismissSeconds}s",
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.timer_title_enabled),
            checked = draft.timerTitleEnabled,
            onCheckedChange = { draft = draft.copy(timerTitleEnabled = it) },
        )
        if (draft.timerTitleEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            TitlePositionSelector(
                selectedPosition = draft.timerTitlePosition,
                onPositionSelected = { draft = draft.copy(timerTitlePosition = it) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SizeSlider(
                label = stringResource(R.string.timer_title_size),
                value = draft.timerTitleTextSizeSp,
                onValueChange = { draft = draft.copy(timerTitleTextSizeSp = it) },
                valueRange = 10f..48f,
                defaultValue = 16f,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        PreferenceToggle(
            label = stringResource(R.string.prompt_before_start),
            checked = draft.promptBeforeStart,
            onCheckedChange = { draft = draft.copy(promptBeforeStart = it) },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            onClick = { onSettingsChanged(draft) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                text = stringResource(R.string.save_defaults),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CleanModeCanvasAdjustBar(
    visible: Boolean,
    controlsAlpha: Float,
    enabled: Boolean,
    positiveOnly: Boolean,
    onAdjust: (Long) -> Unit,
) {
    if (!visible) return
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(controlsAlpha),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!positiveOnly) {
            CanvasAdjustButton(label = "−5 min", enabled = enabled, modifier = Modifier.weight(1f), onClick = { onAdjust(-5 * 60_000L) })
            CanvasAdjustButton(label = "−1 min", enabled = enabled, modifier = Modifier.weight(1f), onClick = { onAdjust(-1 * 60_000L) })
        }
        CanvasAdjustButton(label = "+1 min", enabled = enabled, modifier = Modifier.weight(1f), onClick = { onAdjust(1 * 60_000L) })
        CanvasAdjustButton(label = "+5 min", enabled = enabled, modifier = Modifier.weight(1f), onClick = { onAdjust(5 * 60_000L) })
    }
}

@Composable
private fun HiddenBottomControlsRestoreLayer(
    visible: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .height(148.dp)
            .pointerInput(onRestore) {
                detectTapGestures(onTap = { onRestore() })
            },
    )
}

@Composable
private fun CanvasAdjustButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PreferenceToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
private fun OverlaySizeSelector(selectedSize: OverlaySize, onSizeSelected: (OverlaySize) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.overlay_size_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.overlay_size_small),
                selected = selectedSize == OverlaySize.Small,
                onClick = { onSizeSelected(OverlaySize.Small) },
            )
            SelectorChip(
                label = stringResource(R.string.overlay_size_medium),
                selected = selectedSize == OverlaySize.Medium,
                onClick = { onSizeSelected(OverlaySize.Medium) },
            )
            SelectorChip(
                label = stringResource(R.string.overlay_size_large),
                selected = selectedSize == OverlaySize.Large,
                onClick = { onSizeSelected(OverlaySize.Large) },
            )
        }
    }
}

@Composable
private fun OverlayStyleSelector(selectedStyle: OverlayStyle, onStyleSelected: (OverlayStyle) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.overlay_style_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.overlay_style_ring),
                selected = selectedStyle == OverlayStyle.Ring,
                onClick = { onStyleSelected(OverlayStyle.Ring) },
            )
            SelectorChip(
                label = stringResource(R.string.overlay_style_timer_face),
                selected = selectedStyle == OverlayStyle.TimerFace,
                onClick = { onStyleSelected(OverlayStyle.TimerFace) },
            )
        }
    }
}

@Composable
private fun OverlayLabelPositionSelector(
    selectedPosition: OverlayLabelPosition,
    onPositionSelected: (OverlayLabelPosition) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.overlay_timer_name_position),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip(
                label = stringResource(R.string.overlay_timer_name_position_top),
                selected = selectedPosition == OverlayLabelPosition.Top,
                onClick = { onPositionSelected(OverlayLabelPosition.Top) },
            )
            SelectorChip(
                label = stringResource(R.string.overlay_timer_name_position_bottom),
                selected = selectedPosition == OverlayLabelPosition.Bottom,
                onClick = { onPositionSelected(OverlayLabelPosition.Bottom) },
            )
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
            if (showSeconds) {
                // Align to the next wall-clock second boundary so this clock ticks in
                // phase with the visual timer (which crosses each second within 250ms).
                val ms = System.currentTimeMillis()
                delay(1_000L - (ms % 1_000L))
            } else {
                delay(15_000L)
            }
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
    showSeconds: Boolean = false,
    durationOverrideMillis: Long? = null,
    modifier: Modifier = Modifier,
) {
    // Ticks once per second so end time stays live (both as clock advances and as duration changes during drag)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            delay(1_000L - (ms % 1_000L))
            now = System.currentTimeMillis()
        }
    }

    val endMillis = when (timer.status) {
        TimerStatus.Running -> timer.targetEndTimeMillis ?: (now + timer.remainingMillis)
        TimerStatus.Paused  -> now + (durationOverrideMillis ?: timer.pausedRemainingMillis ?: timer.remainingMillis)
        TimerStatus.Idle    -> now + (durationOverrideMillis ?: timer.selectedDurationMillis)
        else                -> null
    } ?: return

    // Re-derive the formatted string only when the visible minute (or second) boundary changes
    val endText = remember(endMillis / (if (showSeconds) 1_000L else 60_000L), showSeconds) {
        formatWallClockEndTime(endMillis, showSeconds)
    }

    Text(
        text = endText,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontSize = textSizeSp.sp,
            lineHeight = (textSizeSp * 1.2f).sp,
        ),
        color = Color(0xFFF59E0B),
        modifier = modifier.fillMaxWidth(),
        textAlign = when (clockPosition) {
            ClockPosition.Left   -> TextAlign.Start
            ClockPosition.Center -> TextAlign.Center
            ClockPosition.Right  -> TextAlign.End
        },
    )
}

@Composable
private fun TimerTitleDisplay(timer: TimerInstance, isCleanModeActive: Boolean, minimalUiAlpha: Float, forceAlpha: Float? = null) {
    val settings = timer.settings
    if (!settings.timerTitleEnabled || timer.activeTimerName.isBlank()) return
    val alpha = forceAlpha ?: (if (isCleanModeActive && settings.timerTitleHideInCleanMode) minimalUiAlpha else 1f)
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
