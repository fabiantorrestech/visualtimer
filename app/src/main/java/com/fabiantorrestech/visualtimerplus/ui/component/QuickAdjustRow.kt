package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val MINUTE_MILLIS = 60_000L
private const val SECOND_MILLIS = 1_000L

@Composable
fun QuickAdjustRow(
    onAdjust: (Long) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAdjustChip(label = "-5", enabled = enabled) { onAdjust(-5 * MINUTE_MILLIS) }
            QuickAdjustChip(label = "-1", enabled = enabled) { onAdjust(-1 * MINUTE_MILLIS) }
            QuickAdjustChip(label = "-:30", enabled = enabled) { onAdjust(-30 * SECOND_MILLIS) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAdjustChip(label = "+:30", enabled = enabled) { onAdjust(30 * SECOND_MILLIS) }
            QuickAdjustChip(label = "+1", enabled = enabled) { onAdjust(1 * MINUTE_MILLIS) }
            QuickAdjustChip(label = "+5", enabled = enabled) { onAdjust(5 * MINUTE_MILLIS) }
        }
    }
}

@Composable
private fun RowScope.QuickAdjustChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(text = label) },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(22.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
