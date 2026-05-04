package com.fabiantorrestech.visualtimerplus.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.MAX_DURATION_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.clampDuration

/**
 * A bottom sheet that lets the user enter a duration via right-to-left HH:MM:SS digit entry
 * (like a standard clock-app timer input).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(
    initialMillis: Long,
    onDurationSet: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        )
    }
}

@Composable
fun DurationPickerContent(
    initialMillis: Long,
    onDurationSet: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Digit buffer: 6 chars = HHMMSS, right-to-left fill
    val initialSeconds = (initialMillis / 1000L).coerceAtLeast(0L)
    val initHH = (initialSeconds / 3600L).coerceAtMost(99L)
    val initMM = ((initialSeconds % 3600L) / 60L)
    val initSS = (initialSeconds % 60L)

    var digits by remember {
        mutableStateOf(
            if (initialMillis <= 0L) ""
            else "%02d%02d%02d".format(initHH, initMM, initSS).trimStart('0')
        )
    }

    fun displayHH(): String = digits.padStart(6, '0').take(2)
    fun displayMM(): String = digits.padStart(6, '0').substring(2, 4)
    fun displaySS(): String = digits.padStart(6, '0').substring(4, 6)

    fun currentMillis(): Long {
        val padded = digits.padStart(6, '0')
        val hh = padded.take(2).toLongOrNull() ?: 0L
        val mm = padded.substring(2, 4).toLongOrNull() ?: 0L
        val ss = padded.substring(4, 6).toLongOrNull() ?: 0L
        return clampDuration((hh * 3600L + mm * 60L + ss) * 1000L)
    }

    fun isValid(): Boolean {
        val padded = digits.padStart(6, '0')
        val mm = padded.substring(2, 4).toLongOrNull() ?: 0L
        val ss = padded.substring(4, 6).toLongOrNull() ?: 0L
        return mm < 60L && ss < 60L && currentMillis() > 0L
    }

    val digitRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "⌫"),
    )

    fun onKey(key: String) {
        when (key) {
            "⌫" -> { if (digits.isNotEmpty()) digits = digits.dropLast(1) }
            "00" -> { if (digits.length < 5) digits = (digits + "00").takeLast(6) }
            else -> { if (digits.length < 6) digits = (digits + key).trimStart('0').ifEmpty { "0" } }
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
            // Left: title + time display — wider weight so digit groups don't wrap
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
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
                    DigitGroup(value = displayHH(), label = stringResource(R.string.picker_hours), compact = true)
                    SeparatorText(compact = true)
                    DigitGroup(value = displayMM(), label = stringResource(R.string.picker_minutes), compact = true)
                    SeparatorText(compact = true)
                    DigitGroup(value = displaySS(), label = stringResource(R.string.picker_seconds), compact = true)
                }
                if (!isValid() && digits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.picker_invalid),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Right: numpad + action buttons
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                digitRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { key ->
                            DialButton(
                                label = key,
                                modifier = Modifier.weight(1f),
                                buttonHeight = 44.dp,
                                onClick = { onKey(key) },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { if (isValid()) onDurationSet(currentMillis()) },
                        enabled = isValid(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(stringResource(R.string.set_duration_confirm))
                    }
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
                DigitGroup(value = displayHH(), label = stringResource(R.string.picker_hours))
                SeparatorText()
                DigitGroup(value = displayMM(), label = stringResource(R.string.picker_minutes))
                SeparatorText()
                DigitGroup(value = displaySS(), label = stringResource(R.string.picker_seconds))
            }
            if (!isValid() && digits.isNotEmpty()) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { key ->
                        DialButton(
                            label = key,
                            modifier = Modifier.weight(1f),
                            onClick = { onKey(key) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { if (isValid()) onDurationSet(currentMillis()) },
                    enabled = isValid(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.set_duration_confirm))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DigitGroup(value: String, label: String, compact: Boolean = false) {
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = hPad, vertical = vPad),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface,
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
        modifier = Modifier.padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 0.dp),
    )
}

@Composable
private fun DialButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonHeight: androidx.compose.ui.unit.Dp = 56.dp,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(buttonHeight),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
