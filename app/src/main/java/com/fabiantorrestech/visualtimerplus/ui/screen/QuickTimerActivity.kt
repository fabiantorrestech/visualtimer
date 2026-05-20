package com.fabiantorrestech.visualtimerplus.ui.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.PresetEntity
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import com.fabiantorrestech.visualtimerplus.timer.MAX_TIMERS
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.ui.component.DurationPickerSheet
import com.fabiantorrestech.visualtimerplus.ui.component.QuickTimerDial
import com.fabiantorrestech.visualtimerplus.ui.theme.VisualTimerPlusTheme
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuickTimerActivity : ComponentActivity() {

    private lateinit var controller: TimerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        TimerRepository.initialize(applicationContext)
        controller = TimerController(applicationContext)

        setContent {
            val appState by TimerRepository.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (appState.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                else -> systemDark
            }
            VisualTimerPlusTheme(
                isDark = isDark,
                oledBlackEnabled = appState.isOledMode,
                customFontPath = appState.customFontPath,
            ) {
                QuickTimerPopup(
                    onDismiss = { finish() },
                    onLaunchTimer = { durationMillis, presetId, name ->
                        launchTimer(durationMillis, presetId, name)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TimerRepository.setAppForeground(true)
        TimerOverlayManager.setAppForeground(true)
    }

    override fun onStop() {
        super.onStop()
        TimerRepository.setAppForeground(false)
        TimerOverlayManager.setAppForeground(false)
    }

    private fun launchTimer(durationMillis: Long, presetId: Long?, name: String) {
        val state = TimerRepository.state.value
        if (state.timers.size >= MAX_TIMERS) return

        controller.dispatch(TimerAction.AddTimer)
        val newIndex = TimerRepository.state.value.timers.size - 1

        if (name.isNotBlank()) controller.dispatch(TimerAction.SetActiveTimerName(name, newIndex))
        if (presetId != null) controller.dispatch(TimerAction.SetActivePresetId(presetId, newIndex))
        controller.dispatch(TimerAction.SetDurationExact(durationMillis, newIndex))
        controller.dispatch(TimerAction.Start(newIndex))

        TimerNotificationManager.showQuickStartNotification(this, newIndex, name, durationMillis)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTimerPopup(
    onDismiss: () -> Unit,
    onLaunchTimer: (durationMillis: Long, presetId: Long?, name: String) -> Unit,
) {
    val context = LocalContext.current
    val appState by TimerRepository.state.collectAsStateWithLifecycle()
    var timerName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var customDuration by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var presets by remember { mutableStateOf<List<PresetEntity>>(emptyList()) }

    val db = remember { AppDatabase.getInstance(context.applicationContext) }
    val maxTimersError = stringResource(R.string.quick_timer_max_reached, MAX_TIMERS)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            presets = db.appDao().getAllPresets()
        }
    }

    fun attemptStart(durationMillis: Long, presetId: Long?, name: String) {
        if (TimerRepository.state.value.timers.size >= MAX_TIMERS) {
            errorMessage = maxTimersError
            return
        }
        onLaunchTimer(durationMillis, presetId, name)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onDismiss() },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* consume to prevent scrim dismiss */ },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.quick_timer_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                NameField(
                    name = timerName,
                    onNameChange = { timerName = it },
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(12.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; errorMessage = null },
                        text = { Text(stringResource(R.string.quick_timer_tab_presets)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; errorMessage = null },
                        text = { Text(stringResource(R.string.quick_timer_tab_custom)) },
                    )
                }

                Box(modifier = Modifier.heightIn(min = 220.dp, max = 300.dp)) {
                    when (selectedTab) {
                        0 -> PresetsTabContent(
                            presets = presets,
                            onPresetTap = { preset ->
                                val name = timerName.ifBlank { preset.name }
                                attemptStart(preset.durationMillis, preset.id, name)
                            },
                        )
                        1 -> QuickDialTabContent(
                            customDuration = customDuration,
                            onDurationChanged = { customDuration = it },
                            onStart = { attemptStart(customDuration, null, timerName) },
                            clockwiseModeEnabled = appState.defaultTimerSettings.clockwiseModeEnabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_timer_name_hint)) },
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsTabContent(
    presets: List<PresetEntity>,
    onPresetTap: (PresetEntity) -> Unit,
) {
    if (presets.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.quick_timer_no_presets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(presets, key = { it.id }) { preset ->
            ListItem(
                headlineContent = {
                    Text(
                        text = preset.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Text(
                        text = preset.durationMillis.formatClockTime(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { onPresetTap(preset) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickDialTabContent(
    customDuration: Long,
    onDurationChanged: (Long) -> Unit,
    onStart: () -> Unit,
    clockwiseModeEnabled: Boolean,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                Text(
                    text = if (customDuration == 0L) "--:--" else customDuration.formatClockTime(),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (customDuration == 0L)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                TextButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Text(
                        text = stringResource(R.string.quick_timer_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuickTimerDial(
                    durationMillis = customDuration,
                    onDurationChanged = onDurationChanged,
                    showLabel = false,
                    modifier = Modifier.size(160.dp),
                )
                DirectionArrow(
                    clockwise = clockwiseModeEnabled,
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(0.55f),
                )
            }
        }

        Button(
            onClick = onStart,
            enabled = customDuration > 0L,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.quick_timer_start))
        }
    }

    if (showPicker) {
        DurationPickerSheet(
            initialMillis = customDuration,
            onDurationSet = { millis ->
                onDurationChanged(millis)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun DirectionArrow(clockwise: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val strokeWidth = size.minDimension * 0.18f
        val sweepSign = if (clockwise) 1f else -1f
        val arcSweep = sweepSign * 90f
        val endAngleRad = Math.toRadians((-90.0 + arcSweep)).toFloat()
        val tipX = center.x + radius * cos(endAngleRad)
        val tipY = center.y + radius * sin(endAngleRad)
        val tangentRad = endAngleRad + (if (clockwise) (PI / 2).toFloat() else (-PI / 2).toFloat())
        val headSize = size.minDimension * 0.38f

        val arrowPath = Path()
        arrowPath.moveTo(tipX + headSize * cos(tangentRad), tipY + headSize * sin(tangentRad))
        arrowPath.lineTo(tipX + headSize * 0.55f * cos(tangentRad + 2.2f), tipY + headSize * 0.55f * sin(tangentRad + 2.2f))
        arrowPath.lineTo(tipX + headSize * 0.55f * cos(tangentRad - 2.2f), tipY + headSize * 0.55f * sin(tangentRad - 2.2f))
        arrowPath.close()

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = arcSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawPath(arrowPath, color = color)
    }
}
