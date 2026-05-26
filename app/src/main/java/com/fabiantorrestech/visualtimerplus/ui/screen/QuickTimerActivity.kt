package com.fabiantorrestech.visualtimerplus.ui.screen

import android.content.res.Configuration
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import com.fabiantorrestech.visualtimerplus.MainActivity
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.db.AppDatabase
import com.fabiantorrestech.visualtimerplus.db.PresetEntity
import com.fabiantorrestech.visualtimerplus.notification.TimerNotificationManager
import com.fabiantorrestech.visualtimerplus.overlay.TimerOverlayManager
import com.fabiantorrestech.visualtimerplus.timer.MAX_TIMERS
import com.fabiantorrestech.visualtimerplus.timer.QuickTimerLandscapePlacement
import com.fabiantorrestech.visualtimerplus.timer.ThemeMode
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerController
import com.fabiantorrestech.visualtimerplus.timer.TimerRepository
import com.fabiantorrestech.visualtimerplus.timer.findNextAvailableTimerSlot
import com.fabiantorrestech.visualtimerplus.ui.component.DurationPickerSheet
import com.fabiantorrestech.visualtimerplus.ui.component.QuickTimerDial
import com.fabiantorrestech.visualtimerplus.ui.theme.VisualTimerPlusTheme
import com.fabiantorrestech.visualtimerplus.util.formatEndTimeFromNow
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuickTimerActivity : ComponentActivity() {

    private lateinit var controller: TimerController
    private var navigatingToMain = false

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
                    onOpenApp = { openMainActivity() },
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
        if (!navigatingToMain) {
            TimerRepository.setAppForeground(false)
            TimerOverlayManager.setAppForeground(false)
        }
    }

    private fun launchTimer(durationMillis: Long, presetId: Long?, name: String) {
        val state = TimerRepository.state.value
        val targetIndex = state.findNextAvailableTimerSlot() ?: return

        controller.dispatch(TimerAction.AddTimer)
        val updatedState = TimerRepository.state.value
        val newIndex = updatedState.activeTimerIndex
        if (newIndex != targetIndex || newIndex !in updatedState.timers.indices) return

        if (name.isNotBlank()) controller.dispatch(TimerAction.SetActiveTimerName(name, newIndex))
        if (presetId != null) controller.dispatch(TimerAction.SetActivePresetId(presetId, newIndex))
        controller.dispatch(TimerAction.SetDurationExact(durationMillis, newIndex))
        controller.dispatch(TimerAction.Start(newIndex))

        if (TimerRepository.state.value.autoOpenAppAfterQuickStart) {
            openMainActivity(targetTimerIndex = newIndex)
        } else {
            TimerNotificationManager.showQuickStartNotification(this, newIndex, name, durationMillis)
            finish()
        }
    }

    private fun openMainActivity(targetTimerIndex: Int? = null) {
        navigatingToMain = true
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (targetTimerIndex != null) {
                putExtra(TimerNotificationManager.EXTRA_TARGET_TIMER_INDEX, targetTimerIndex)
            }
        }
        startActivity(launchIntent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTimerPopup(
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
    onLaunchTimer: (durationMillis: Long, presetId: Long?, name: String) -> Unit,
) {
    val context = LocalContext.current
    val appState by TimerRepository.state.collectAsStateWithLifecycle()
    var timerName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(1) }
    var customDuration by remember { mutableLongStateOf(0L) }
    var pickerPreviewDuration by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var presets by remember { mutableStateOf<List<PresetEntity>>(emptyList()) }

    val db = remember { AppDatabase.getInstance(context.applicationContext) }
    val maxTimersError = stringResource(R.string.quick_timer_max_reached, MAX_TIMERS)
    val popupContainerColor = if (appState.isOledMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            presets = db.appDao().getAllPresets()
        }
    }

    fun attemptStart(durationMillis: Long, presetId: Long?, name: String) {
        if (durationMillis <= 0L) return
        if (TimerRepository.state.value.findNextAvailableTimerSlot() == null) {
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
        val landscapePlacement = appState.quickTimerLandscapePlacement
        val cardModifier = if (isLandscape) {
            Modifier
                .fillMaxWidth(
                    when (landscapePlacement) {
                        QuickTimerLandscapePlacement.Center -> 0.72f
                        QuickTimerLandscapePlacement.LeftPanel,
                        QuickTimerLandscapePlacement.RightPanel -> 0.46f
                    },
                )
                .widthIn(
                    min = if (landscapePlacement == QuickTimerLandscapePlacement.Center) 420.dp else 320.dp,
                    max = if (landscapePlacement == QuickTimerLandscapePlacement.Center) 760.dp else 520.dp,
                )
                .fillMaxHeight()
                .align(
                    when (landscapePlacement) {
                        QuickTimerLandscapePlacement.Center -> Alignment.Center
                        QuickTimerLandscapePlacement.LeftPanel -> Alignment.CenterStart
                        QuickTimerLandscapePlacement.RightPanel -> Alignment.CenterEnd
                    },
                )
                .padding(horizontal = 16.dp, vertical = 20.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter)
        }
        Card(
            modifier = cardModifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* consume to prevent scrim dismiss */ },
            shape = if (isLandscape) {
                RoundedCornerShape(28.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = popupContainerColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier)
                    .then(
                        if (isLandscape) {
                            Modifier.statusBarsPadding().navigationBarsPadding()
                        } else {
                            Modifier.navigationBarsPadding()
                        },
                    )
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                QuickTimerHeader(
                    isLandscape = isLandscape,
                    timerName = timerName,
                    onTimerNameChange = { timerName = it },
                    onOpenApp = onOpenApp,
                    onDismiss = onDismiss,
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(if (isLandscape) 10.dp else 12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = popupContainerColor,
                ) {
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

                Box(
                    modifier = if (isLandscape) {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    } else {
                        Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 300.dp)
                    },
                ) {
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
                            previewDuration = pickerPreviewDuration,
                            onDurationChanged = { customDuration = it },
                            onPreviewDurationChanged = { pickerPreviewDuration = it },
                            onStart = { attemptStart(customDuration, null, timerName) },
                            clockwiseModeEnabled = appState.defaultTimerSettings.clockwiseModeEnabled,
                            showEndTimeSecondsEnabled = appState.defaultTimerSettings.showEndTimeSecondsEnabled,
                            isOledMode = appState.isOledMode,
                            isLandscape = isLandscape,
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
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.quick_timer_name_hint)) },
        singleLine = true,
    )
}

@Composable
private fun QuickTimerHeader(
    isLandscape: Boolean,
    timerName: String,
    onTimerNameChange: (String) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (isLandscape) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.quick_timer_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                NameField(
                    name = timerName,
                    onNameChange = onTimerNameChange,
                    modifier = Modifier.widthIn(max = 340.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.quick_timer_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val openAppDescription = stringResource(R.string.quick_timer_open_app)
            IconButton(
                onClick = onOpenApp,
                modifier = Modifier.semantics {
                    contentDescription = openAppDescription
                },
            ) {
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageResource(R.mipmap.ic_launcher)
                            contentDescription = null
                        }
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        }
    }

    if (!isLandscape) {
        Spacer(Modifier.height(8.dp))
        NameField(
            name = timerName,
            onNameChange = onTimerNameChange,
        )
    }
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
    previewDuration: Long?,
    onDurationChanged: (Long) -> Unit,
    onPreviewDurationChanged: (Long?) -> Unit,
    onStart: () -> Unit,
    clockwiseModeEnabled: Boolean,
    showEndTimeSecondsEnabled: Boolean,
    isOledMode: Boolean = false,
    isLandscape: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayDuration = previewDuration ?: customDuration

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: time display
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (displayDuration == 0L) "--:--" else displayDuration.formatClockTime(),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (displayDuration == 0L)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                )
                if (displayDuration > 0L) {
                    QuickTimerEndTimeText(
                        durationMillis = displayDuration,
                        showSeconds = showEndTimeSecondsEnabled,
                    )
                }
            }
            // Middle: buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.quick_timer_set_timer),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                ) {
                    Text(text = stringResource(R.string.quick_timer_start))
                }
            }
            // Right: dial
            Box(contentAlignment = Alignment.Center) {
                QuickTimerDial(
                    durationMillis = customDuration,
                    onDurationChanged = onDurationChanged,
                    showLabel = false,
                    clockwiseModeEnabled = clockwiseModeEnabled,
                    isOledMode = isOledMode,
                    modifier = Modifier
                        .sizeIn(maxWidth = 160.dp, maxHeight = 160.dp)
                        .aspectRatio(1f),
                )
            }
        }
    } else {
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
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (displayDuration == 0L) "--:--" else displayDuration.formatClockTime(),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (displayDuration == 0L)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        if (displayDuration > 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            QuickTimerEndTimeText(
                                durationMillis = displayDuration,
                                showSeconds = showEndTimeSecondsEnabled,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quick_timer_set),
                            style = MaterialTheme.typography.bodyMedium,
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
                        clockwiseModeEnabled = clockwiseModeEnabled,
                        isOledMode = isOledMode,
                        modifier = Modifier.size(160.dp),
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
    }

    if (showPicker) {
        DurationPickerSheet(
            initialMillis = customDuration,
            onDurationSet = { millis ->
                onDurationChanged(millis)
                onPreviewDurationChanged(null)
                showPicker = false
            },
            onDismiss = {
                onPreviewDurationChanged(null)
                showPicker = false
            },
            onPreviewDurationChanged = onPreviewDurationChanged,
        )
    }
}

@Composable
private fun QuickTimerEndTimeText(
    durationMillis: Long,
    showSeconds: Boolean,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(showSeconds) {
        while (true) {
            val currentTime = System.currentTimeMillis()
            val interval = if (showSeconds) 1_000L else 60_000L
            delay(interval - (currentTime % interval))
            now = System.currentTimeMillis()
        }
    }

    val endTimeText = remember(durationMillis, now / if (showSeconds) 1_000L else 60_000L, showSeconds) {
        formatEndTimeFromNow(
            durationMillis = durationMillis,
            showSeconds = showSeconds,
            nowMillis = now,
        )
    }

    Text(
        text = endTimeText,
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFF59E0B),
        textAlign = TextAlign.Center,
    )
}
