package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private val presetMinutes = listOf(
    listOf(5, 10, 15),
    listOf(30, 45, 60),
)

@Composable
fun PresetRow(
    onPresetSelected: (Long) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presetMinutes.forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowPresets.forEach { minutes ->
                    AssistChip(
                        onClick = { onPresetSelected(minutes * 60_000L) },
                        enabled = enabled,
                        label = { Text(text = formatPresetLabel(minutes)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
                repeat(3 - rowPresets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatPresetLabel(minutes: Int): String = when {
    minutes % 60 == 0 && minutes >= 60 -> String.format(Locale.getDefault(), "%dh", minutes / 60)
    minutes > 60 -> String.format(
        Locale.getDefault(),
        "%dh %02dm",
        minutes / 60,
        minutes % 60,
    )
    else -> String.format(Locale.getDefault(), "%dm", minutes)
}
