package com.fabiantorrestech.visualtimerplus.ui.screen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabiantorrestech.visualtimerplus.MainActivity
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.timer.AppState
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import com.fabiantorrestech.visualtimerplus.ui.component.VisualTimerCanvas
import com.fabiantorrestech.visualtimerplus.ui.theme.TimerRed
import com.fabiantorrestech.visualtimerplus.ui.theme.VisualTimerPlusTheme
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import com.fabiantorrestech.visualtimerplus.util.formatWallClockEndTime
import kotlin.math.min

class TimerFinishedActivity : ComponentActivity() {

    private lateinit var controller: TimerController
    private val activityCreatedAtMillis = System.currentTimeMillis()
    private var launchedMainActivity = false
    private var launchedTargetTimerIndex by mutableStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }

        controller = TimerController(applicationContext)
        applyLaunchIntent(intent)

        setContent {
            val appState by TimerRepository.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (appState.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                else -> systemDark
            }
            VisualTimerPlusTheme(isDark = isDark, oledBlackEnabled = appState.isOledMode, customFontPath = appState.customFontPath) {
                TimerFinishedScreen(
                    appState = appState,
                    activityCreatedAtMillis = activityCreatedAtMillis,
                    launchTargetTimerIndex = launchedTargetTimerIndex,
                    onDismiss = { id -> controller.dispatch(TimerAction.DismissFinished(id)) },
                    onAddTime = ::addTimeAndOpenApp,
                    onRestart = { id -> controller.dispatch(TimerAction.Restart(id)) },
                    onOpenApp = { id -> openApp(id) },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        setFinishedPageForeground(true)
    }

    override fun onStop() {
        if (!launchedMainActivity) {
            setFinishedPageForeground(false)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (!launchedMainActivity) {
            setFinishedPageForeground(false)
        }
        super.onDestroy()
    }

    private fun setFinishedPageForeground(foreground: Boolean) {
        TimerRepository.setAppForeground(foreground)
        TimerOverlayManager.setAppForeground(foreground)
    }

    private fun addTimeAndOpenApp(timerIndex: Int) {
        if (TimerRepository.getTimer(timerIndex).status != TimerStatus.Overtime) return
        controller.addTimeDuringOvertime(timerIndex, 60_000L)
        openApp(timerIndex)
    }

    private fun openApp(timerIndex: Int) {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    launchMainActivity(timerIndex)
                    finish()
                }
            })
        } else {
            launchMainActivity(timerIndex)
            finish()
        }
    }

    private fun launchMainActivity(timerIndex: Int) {
        launchedMainActivity = true
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(TimerNotificationManager.EXTRA_TARGET_TIMER_INDEX, timerIndex)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun applyLaunchIntent(intent: Intent?) {
        setIntent(intent)
        launchedTargetTimerIndex = intent
            ?.getIntExtra(TimerNotificationManager.EXTRA_TARGET_TIMER_INDEX, -1)
            ?: -1
    }

    companion object {
        fun createLaunchIntent(context: Context, timerIndex: Int): Intent = Intent(
            context,
            TimerFinishedActivity::class.java,
        ).apply {
            putExtra(TimerNotificationManager.EXTRA_TARGET_TIMER_INDEX, timerIndex)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
    }
}

@Composable
private fun TimerFinishedScreen(
    appState: AppState,
    activityCreatedAtMillis: Long,
    launchTargetTimerIndex: Int,
    onDismiss: (Int) -> Unit,
    onAddTime: (Int) -> Unit,
    onRestart: (Int) -> Unit,
    onOpenApp: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val overtimeTimers = appState.timers.filter { it.status == TimerStatus.Overtime }
    val runningTimers = appState.timers.filter { it.status == TimerStatus.Running }

    LaunchedEffect(overtimeTimers.isEmpty()) {
        if (overtimeTimers.isEmpty()) onClose()
    }

    val allEligible = overtimeTimers + runningTimers
    val defaultFocused = allEligible.find { it.id == launchTargetTimerIndex }
        ?: overtimeTimers.maxByOrNull { it.currentOvertimeSegmentMillis }
        ?: runningTimers.firstOrNull()
        ?: return

    var focusedTimerId by rememberSaveable { mutableStateOf<Int?>(launchTargetTimerIndex.takeIf { it >= 0 }) }
    LaunchedEffect(launchTargetTimerIndex, allEligible) {
        val targetTimer = allEligible.find { it.id == launchTargetTimerIndex } ?: return@LaunchedEffect
        focusedTimerId = targetTimer.id
    }
    val focusedTimer = allEligible.find { it.id == focusedTimerId } ?: defaultFocused

    val alsoFinishedList = overtimeTimers
        .filter { it.id != focusedTimer.id }
        .sortedByDescending { it.currentOvertimeSegmentMillis }

    val now = System.currentTimeMillis()
    val comingUpList = runningTimers
        .filter { it.id != focusedTimer.id }
        .sortedBy { it.targetEndTimeMillis?.minus(now) ?: it.remainingMillis }

    var currentTime by remember { mutableStateOf(formatWallClockEndTime(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        // Sync to the next minute boundary, then tick every 60s
        delay(60_000L - System.currentTimeMillis() % 60_000L)
        while (true) {
            currentTime = formatWallClockEndTime(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars)),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Clock pinned top-left, below system status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            // Scrollable, vertically centered main content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {

                if (focusedTimer.activeTimerName.isNotBlank()) {
                    Text(
                        text = focusedTimer.activeTimerName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                // Ring — 88% screen width, layered: canvas track → overtime overlay → gesture blocker → center text
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // Canvas: blinks in overtime; displayMillisOverride=0L gives clean dark track circle
                    VisualTimerCanvas(
                        timer = focusedTimer,
                        onPreviewDurationChanged = {},
                        onDragCommit = {},
                        modifier = Modifier.fillMaxSize(),
                        isOledMode = appState.isOledMode,
                        displayMillisOverride = if (focusedTimer.status == TimerStatus.Overtime) 0L else null,
                    )

                    // Overtime ring overlay: pulsing glow + growing arc (one rotation per 60s)
                    if (focusedTimer.status == TimerStatus.Overtime) {
                        OvertimeRingOverlay(
                            overtimeMillis = focusedTimer.currentOvertimeSegmentMillis,
                            modifier = Modifier.matchParentSize(),
                        )
                    }

                    // Consume all touch events so the canvas drag gesture can't fire
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                }
                            },
                    )

                    if (focusedTimer.status == TimerStatus.Overtime) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.status_overtime).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 3.sp,
                            )
                            Text(
                                text = "+${focusedTimer.currentOvertimeSegmentMillis.formatClockTime()}",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Action buttons: small pill · large circle Stop · small pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = { onAddTime(focusedTimer.id) },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        Text(
                            stringResource(R.string.finished_add_time),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Button(
                        onClick = { onDismiss(focusedTimer.id) },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            stringResource(R.string.finished_stop),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    FilledTonalButton(
                        onClick = { onRestart(focusedTimer.id) },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        Text(
                            "↻ ${stringResource(R.string.restart)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                AssistChip(
                    onClick = { onOpenApp(focusedTimer.id) },
                    modifier = Modifier.height(40.dp),
                    label = {
                        Text(
                            stringResource(R.string.finished_open_app),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )

                if (alsoFinishedList.isNotEmpty() || comingUpList.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(4.dp))

                    if (alsoFinishedList.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.finished_also_finished),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        alsoFinishedList.forEach { timer ->
                            TimerListRow(
                                timer = timer,
                                activityCreatedAtMillis = activityCreatedAtMillis,
                                onClick = { focusedTimerId = timer.id },
                            )
                        }
                    }

                    if (comingUpList.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.finished_coming_up),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        comingUpList.forEach { timer ->
                            TimerListRow(
                                timer = timer,
                                activityCreatedAtMillis = activityCreatedAtMillis,
                                onClick = { focusedTimerId = timer.id },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Draws a pulsing glow ring + growing arc (one full rotation = 60 seconds of overtime)
// layered on top of VisualTimerCanvas in overtime mode.
@Composable
private fun OvertimeRingOverlay(
    overtimeMillis: Long,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "overtime-ring")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    // Clockwise from 12 o'clock; one full rotation = 60 seconds
    val sweepAngle = (overtimeMillis % 60_000L).toFloat() / 60_000f * 360f

    Canvas(modifier = modifier) {
        val diameter = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter / 2f
        val strokeWidth = diameter * 0.09f
        val arcRadius = radius - strokeWidth / 2f
        val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
        val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

        // Pulsing glow: 4 concentric rings at increasing widths, fading outward
        val glowSteps = 4
        for (i in 1..glowSteps) {
            val factor = i.toFloat() / glowSteps
            drawCircle(
                color = TimerRed.copy(alpha = glowPulse * (1f - factor * 0.65f)),
                radius = arcRadius,
                center = center,
                style = Stroke(width = strokeWidth * (1f + factor)),
            )
        }

        // Growing arc (clockwise, full color)
        if (sweepAngle > 0f) {
            drawArc(
                color = TimerRed,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun TimerListRow(
    timer: TimerInstance,
    activityCreatedAtMillis: Long,
    onClick: () -> Unit,
) {
    val isFlashing = timer.status == TimerStatus.Overtime &&
        (timer.overtimeStartedAtMillis ?: 0L) > activityCreatedAtMillis

    val infiniteTransition = rememberInfiniteTransition(label = "flash-${timer.id}")
    val flashPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flashAlpha",
    )
    val bgColor = if (isFlashing) TimerRed.copy(alpha = flashPulse) else Color.Transparent

    val dotColor = if (timer.status == TimerStatus.Overtime) TimerRed else MaterialTheme.colorScheme.primary
    val displayText = if (timer.status == TimerStatus.Overtime) {
        "+${timer.currentOvertimeSegmentMillis.formatClockTime()}"
    } else {
        timer.displayMillis.formatClockTime()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, shape = CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = timer.activeTimerName.ifBlank { "Timer ${timer.id + 1}" },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = dotColor,
        )
    }
}
