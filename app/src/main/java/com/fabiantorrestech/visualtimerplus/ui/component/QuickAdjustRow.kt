package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val MINUTE_MILLIS = 60_000L
private const val SECOND_MILLIS = 1_000L

@Composable
fun QuickAdjustRow(
    onAdjust: (Long) -> Unit,
    enabled: Boolean,
    positiveOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!positiveOnly) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickAdjustChip(label = "−30s", enabled = enabled) { onAdjust(-30 * SECOND_MILLIS) }
                QuickAdjustChip(label = "−1 min", enabled = enabled) { onAdjust(-1 * MINUTE_MILLIS) }
                QuickAdjustChip(label = "−5 min", enabled = enabled) { onAdjust(-5 * MINUTE_MILLIS) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAdjustChip(label = "+30s", enabled = enabled) { onAdjust(30 * SECOND_MILLIS) }
            QuickAdjustChip(label = "+1 min", enabled = enabled) { onAdjust(1 * MINUTE_MILLIS) }
            QuickAdjustChip(label = "+5 min", enabled = enabled) { onAdjust(5 * MINUTE_MILLIS) }
        }
    }
}

@Composable
private fun RowScope.QuickAdjustChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.92f else 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
