package com.fabiantorrestech.visualtimerplus.ui.component

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.MAX_DURATION_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.clampDuration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(
    initialMillis: Long,
    onDurationSet: (Long) -> Unit,
    onDismiss: () -> Unit,
    onPreviewDurationChanged: ((Long?) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            onPreviewDurationChanged?.invoke(null)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        DurationPickerContent(
            initialMillis = initialMillis,
            onDurationSet = { millis ->
                onDurationSet(millis)
                onDismiss()
            },
            onDismiss = onDismiss,
            onPreviewDurationChanged = onPreviewDurationChanged,
        )
    }
}

@Composable
fun DurationPickerContent(
    initialMillis: Long,
    onDurationSet: (Long) -> Unit,
    onDismiss: () -> Unit,
    onPreviewDurationChanged: ((Long?) -> Unit)? = null,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.tab_duration)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.tab_end_time)) },
            )
        }

        when (selectedTab) {
            0 -> DurationTabContent(
                initialMillis = initialMillis,
                isLandscape = isLandscape,
                onDurationSet = onDurationSet,
                onDismiss = {
                    onPreviewDurationChanged?.invoke(null)
                    onDismiss()
                },
                onPreviewDurationChanged = onPreviewDurationChanged,
            )
            1 -> EndTimeTabContent(
                isLandscape = isLandscape,
                onDurationSet = onDurationSet,
                onDismiss = {
                    onPreviewDurationChanged?.invoke(null)
                    onDismiss()
                },
                onPreviewDurationChanged = onPreviewDurationChanged,
            )
        }
    }
}

// ── Shared key logic ──────────────────────────────────────────────────────────

// Applies a numpad key to a single 2-char field string. No rollover.
private fun applyKey(key: String, field: String): String = when (key) {
    "⌫" -> field.dropLast(1)
    else -> if (field.length < 2) (field + key).take(2) else field
}

// ── Duration tab ──────────────────────────────────────────────────────────────

@Composable
private fun DurationTabContent(
    initialMillis: Long,
    isLandscape: Boolean,
    onDurationSet: (Long) -> Unit,
    onDismiss: () -> Unit,
    onPreviewDurationChanged: ((Long?) -> Unit)? = null,
) {
    val initialSeconds = (initialMillis / 1000L).coerceAtLeast(0L)
    val initHH = (initialSeconds / 3600L).coerceAtMost(99L)
    val initMM = (initialSeconds % 3600L) / 60L
    val initSS = initialSeconds % 60L
    val hasInit = initialMillis > 0L

    var hhDigits by remember { mutableStateOf(if (hasInit) "%02d".format(initHH) else "") }
    var mmDigits by remember { mutableStateOf(if (hasInit) "%02d".format(initMM) else "") }
    var ssDigits by remember { mutableStateOf(if (hasInit) "%02d".format(initSS) else "") }
    // null = no field focused; 0 = hours, 1 = minutes, 2 = seconds
    var selectedField by remember { mutableStateOf<Int?>(null) }

    fun fieldVal(s: String) = s.padStart(2, '0').toLongOrNull() ?: 0L

    fun currentMillis(): Long {
        val hh = fieldVal(hhDigits)
        val mm = fieldVal(mmDigits)
        val ss = fieldVal(ssDigits)
        return clampDuration((hh * 3600L + mm * 60L + ss) * 1000L)
    }

    fun isValid(): Boolean {
        val mm = fieldVal(mmDigits)
        val ss = fieldVal(ssDigits)
        return mm < 60L && ss < 60L && currentMillis() > 0L
    }

    LaunchedEffect(hhDigits, mmDigits, ssDigits) {
        onPreviewDurationChanged?.invoke(currentMillis().takeIf { isValid() })
    }

    fun onKey(key: String) {
        when (selectedField) {
            0 -> hhDigits = applyKey(key, hhDigits)
            1 -> mmDigits = applyKey(key, mmDigits)
            2 -> ssDigits = applyKey(key, ssDigits)
        }
    }

    fun toggleField(field: Int) {
        selectedField = if (selectedField == field) null else field
    }

    val anyInput = hhDigits.isNotEmpty() || mmDigits.isNotEmpty() || ssDigits.isNotEmpty()
    val digitRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "⌫"),
    )

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1.3f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.set_duration),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DigitGroup(
                        value = hhDigits.padStart(2, '0'),
                        label = stringResource(R.string.picker_hours),
                        compact = true,
                        selected = selectedField == 0,
                        onSelect = { toggleField(0) },
                    )
                    SeparatorText(compact = true)
                    DigitGroup(
                        value = mmDigits.padStart(2, '0'),
                        label = stringResource(R.string.picker_minutes),
                        compact = true,
                        selected = selectedField == 1,
                        onSelect = { toggleField(1) },
                    )
                    SeparatorText(compact = true)
                    DigitGroup(
                        value = ssDigits.padStart(2, '0'),
                        label = stringResource(R.string.picker_seconds),
                        compact = true,
                        selected = selectedField == 2,
                        onSelect = { toggleField(2) },
                    )
                }
                if (!isValid() && anyInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.picker_invalid),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                digitRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { key ->
                            DialButton(
                                label = key,
                                modifier = Modifier.weight(1f),
                                buttonHeight = 44.dp,
                                enabled = selectedField != null,
                                onClick = { onKey(key) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { if (isValid()) onDurationSet(currentMillis()) },
                        enabled = isValid(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    ) { Text(stringResource(R.string.set_duration_confirm)) }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.set_duration),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DigitGroup(
                    value = hhDigits.padStart(2, '0'),
                    label = stringResource(R.string.picker_hours),
                    selected = selectedField == 0,
                    onSelect = { toggleField(0) },
                )
                SeparatorText()
                DigitGroup(
                    value = mmDigits.padStart(2, '0'),
                    label = stringResource(R.string.picker_minutes),
                    selected = selectedField == 1,
                    onSelect = { toggleField(1) },
                )
                SeparatorText()
                DigitGroup(
                    value = ssDigits.padStart(2, '0'),
                    label = stringResource(R.string.picker_seconds),
                    selected = selectedField == 2,
                    onSelect = { toggleField(2) },
                )
            }
            if (!isValid() && anyInput) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.picker_invalid),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            digitRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { key ->
                        DialButton(
                            label = key,
                            modifier = Modifier.weight(1f),
                            enabled = selectedField != null,
                            onClick = { onKey(key) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { if (isValid()) onDurationSet(currentMillis()) },
                    enabled = isValid(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) { Text(stringResource(R.string.set_duration_confirm)) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── End time tab ──────────────────────────────────────────────────────────────

private fun computeEndTimeMillis(digits: String, isAm: Boolean, dateOffset: Int): Long? {
    val padded = digits.padStart(4, '0')
    val hh = padded.take(2).toIntOrNull() ?: return null
    val mm = padded.substring(2, 4).toIntOrNull() ?: return null
    if (hh < 1 || hh > 12) return null
    if (mm < 0 || mm >= 60) return null
    val hour24 = when {
        isAm && hh == 12 -> 0
        !isAm && hh != 12 -> hh + 12
        else -> hh
    }
    val targetDate = LocalDate.now().plusDays(dateOffset.toLong())
    val targetDateTime = LocalDateTime.of(targetDate, LocalTime.of(hour24, mm))
    val endMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val duration = endMillis - System.currentTimeMillis()
    return if (duration > 0L && duration <= MAX_DURATION_MILLIS) duration else null
}

private fun dateChipLabel(offset: Int): String = when (offset) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> {
        val date = LocalDate.now().plusDays(offset.toLong())
        date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))
    }
}

private fun formatDurationPreview(millis: Long): String {
    val totalSecs = millis / 1000L
    val hours = totalSecs / 3600L
    val mins = (totalSecs % 3600L) / 60L
    val secs = totalSecs % 60L
    return buildString {
        if (hours > 0L) append("${hours}h ")
        if (mins > 0L || hours > 0L) append("${mins}m")
        if (secs > 0L && hours == 0L) append(" ${secs}s")
    }.trim()
}

@Composable
private fun EndTimeTabContent(
    isLandscape: Boolean,
    onDurationSet: (Long) -> Unit,
    onDismiss: () -> Unit,
    onPreviewDurationChanged: ((Long?) -> Unit)? = null,
) {
    val initTime = remember {
        LocalTime.now().plusHours(1).let { t -> LocalTime.of(t.hour, (t.minute / 5) * 5) }
    }
    val initHour12 = remember { if (initTime.hour % 12 == 0) 12 else initTime.hour % 12 }
    val initIsAm = remember { initTime.hour < 12 }

    var hhDigits by remember { mutableStateOf("%02d".format(initHour12)) }
    var mmDigits by remember { mutableStateOf("%02d".format(initTime.minute)) }
    var isAm by remember { mutableStateOf(initIsAm) }
    // null = no field focused; 0 = hours, 1 = minutes
    var selectedField by remember { mutableStateOf<Int?>(null) }

    fun combinedDigits() = hhDigits.padStart(2, '0') + mmDigits.padStart(2, '0')

    val initOffset = remember {
        val combined = "%02d%02d".format(initHour12, initTime.minute)
        (0..4).firstOrNull { computeEndTimeMillis(combined, initIsAm, it) != null } ?: 0
    }
    var selectedOffset by remember { mutableStateOf(initOffset) }

    LaunchedEffect(hhDigits, mmDigits, isAm) {
        val combined = combinedDigits()
        if (computeEndTimeMillis(combined, isAm, selectedOffset) == null) {
            val validOffset = (0..4).firstOrNull { computeEndTimeMillis(combined, isAm, it) != null }
            if (validOffset != null) selectedOffset = validOffset
        }
    }

    fun resultMillis(): Long? = computeEndTimeMillis(combinedDigits(), isAm, selectedOffset)
    fun isValid(): Boolean = resultMillis() != null

    LaunchedEffect(hhDigits, mmDigits, isAm, selectedOffset) {
        onPreviewDurationChanged?.invoke(resultMillis())
    }

    fun onKey(key: String) {
        when (selectedField) {
            0 -> hhDigits = applyKey(key, hhDigits)
            1 -> mmDigits = applyKey(key, mmDigits)
        }
    }

    fun toggleField(field: Int) {
        selectedField = if (selectedField == field) null else field
    }

    val digitRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "⌫"),
    )

    @Composable
    fun TimeDisplay(compact: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DigitGroup(
                value = hhDigits.padStart(2, '0'),
                label = "h",
                compact = compact,
                selected = selectedField == 0,
                onSelect = { toggleField(0) },
            )
            SeparatorText(compact = compact)
            DigitGroup(
                value = mmDigits.padStart(2, '0'),
                label = stringResource(R.string.picker_minutes),
                compact = compact,
                selected = selectedField == 1,
                onSelect = { toggleField(1) },
            )
            Spacer(modifier = Modifier.width(if (compact) 12.dp else 16.dp))
            AmPmToggle(isAm = isAm, onToggle = { isAm = it; selectedField = null }, compact = compact)
        }
    }

    @Composable
    fun DateChips() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (0..4).forEach { offset ->
                val enabled = computeEndTimeMillis(combinedDigits(), isAm, offset) != null
                val selected = selectedOffset == offset
                Surface(
                    onClick = { if (enabled) selectedOffset = offset },
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        enabled -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                ) {
                    Text(
                        text = dateChipLabel(offset),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun PreviewOrError() {
        val r = resultMillis()
        when {
            r != null -> Text(
                text = stringResource(R.string.end_time_runs_for, formatDurationPreview(r)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            combinedDigits().trimStart('0').isNotEmpty() -> Text(
                text = stringResource(R.string.end_time_error_past),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1.3f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.set_end_time),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TimeDisplay(compact = true)
                Spacer(modifier = Modifier.height(12.dp))
                DateChips()
                Spacer(modifier = Modifier.height(8.dp))
                PreviewOrError()
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                digitRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { key ->
                            DialButton(
                                label = key,
                                modifier = Modifier.weight(1f),
                                buttonHeight = 44.dp,
                                enabled = selectedField != null,
                                onClick = { onKey(key) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { resultMillis()?.let { onDurationSet(it) } },
                        enabled = isValid(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    ) { Text(stringResource(R.string.set_duration_confirm)) }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.set_end_time),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(20.dp))
            TimeDisplay(compact = false)
            Spacer(modifier = Modifier.height(16.dp))
            DateChips()
            Spacer(modifier = Modifier.height(10.dp))
            PreviewOrError()
            Spacer(modifier = Modifier.height(20.dp))
            digitRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { key ->
                        DialButton(
                            label = key,
                            modifier = Modifier.weight(1f),
                            enabled = selectedField != null,
                            onClick = { onKey(key) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { resultMillis()?.let { onDurationSet(it) } },
                    enabled = isValid(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) { Text(stringResource(R.string.set_duration_confirm)) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Shared display components ─────────────────────────────────────────────────

@Composable
private fun AmPmToggle(isAm: Boolean, onToggle: (Boolean) -> Unit, compact: Boolean) {
    val height = if (compact) 36.dp else 44.dp
    val textStyle = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    Row(
        modifier = Modifier.background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            RoundedCornerShape(12.dp),
        ),
    ) {
        listOf(true to "AM", false to "PM").forEach { (amValue, label) ->
            val selected = isAm == amValue
            Surface(
                onClick = { onToggle(amValue) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.height(height),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp),
                ) {
                    Text(
                        text = label,
                        style = textStyle,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitGroup(
    value: String,
    label: String,
    compact: Boolean = false,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    val textStyle = if (compact) {
        MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    } else {
        MaterialTheme.typography.displayMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val hPad = if (compact) 10.dp else 16.dp
    val vPad = if (compact) 6.dp else 8.dp

    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        label = "digitGroupBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        label = "digitGroupText",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(12.dp))
                .then(if (onSelect != null) Modifier.clickable { onSelect() } else Modifier)
                .padding(horizontal = hPad, vertical = vPad),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = textStyle,
                color = textColor,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeparatorText(compact: Boolean = false) {
    val style = if (compact) {
        MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light)
    } else {
        MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light)
    }
    Text(
        text = ":",
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = if (compact) 4.dp else 8.dp),
    )
}

@Composable
private fun DialButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonHeight: androidx.compose.ui.unit.Dp = 56.dp,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(buttonHeight),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
        )
    }
}
