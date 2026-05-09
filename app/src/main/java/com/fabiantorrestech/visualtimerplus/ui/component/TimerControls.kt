package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fabiantorrestech.visualtimerplus.R
import com.fabiantorrestech.visualtimerplus.timer.TimerAction
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus

@Composable
fun TimerControls(
    timer: TimerInstance,
    onAction: (TimerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (timer.status) {
            TimerStatus.Idle -> {
                SecondaryControlButton(
                    onClick = { onAction(TimerAction.Reset()) },
                    enabled = timer.selectedDurationMillis > 0L,
                ) {
                    ControlButtonContent(iconText = "R", label = stringResource(R.string.reset))
                }
                PrimaryControlButton(
                    onClick = { onAction(TimerAction.Start()) },
                    enabled = timer.selectedDurationMillis > 0L,
                ) {
                    Text(text = stringResource(R.string.start))
                }
            }

            TimerStatus.Running -> {
                SecondaryControlButton(onClick = { onAction(TimerAction.Reset()) }) {
                    ControlButtonContent(iconText = "R", label = stringResource(R.string.reset))
                }
                PrimaryControlButton(onClick = { onAction(TimerAction.Pause()) }) {
                    ControlButtonContent(iconText = "II", label = stringResource(R.string.pause))
                }
            }

            TimerStatus.Paused -> {
                SecondaryControlButton(onClick = { onAction(TimerAction.Reset()) }) {
                    ControlButtonContent(iconText = "R", label = stringResource(R.string.reset))
                }
                PrimaryControlButton(onClick = { onAction(TimerAction.Resume()) }) {
                    ControlButtonContent(iconText = ">", label = stringResource(R.string.resume))
                }
            }

            TimerStatus.Overtime,
            TimerStatus.Finished -> {
                SecondaryControlButton(onClick = { onAction(TimerAction.DismissFinished()) }) {
                    ControlButtonContent(iconText = "X", label = stringResource(R.string.dismiss))
                }
                PrimaryControlButton(
                    onClick = { onAction(TimerAction.Restart()) },
                    enabled = timer.originalDurationMillis > 0L,
                ) {
                    ControlButtonContent(iconText = "↺", label = stringResource(R.string.restart))
                }
            }
        }
    }
}

@Composable
private fun RowScope.PrimaryControlButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f),
        ),
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .defaultMinSize(minHeight = 56.dp),
        content = content,
    )
}

@Composable
private fun RowScope.SecondaryControlButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, bottomControlBorderColor()),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            contentColor = bottomControlContentColor(),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            disabledContentColor = bottomControlContentColor().copy(alpha = 0.45f),
        ),
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .defaultMinSize(minHeight = 56.dp),
        content = content,
    )
}

@Composable
private fun ControlButtonContent(
    iconText: String,
    label: String,
) {
    Text(
        text = iconText,
        modifier = Modifier.alpha(0.88f),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = label,
        modifier = Modifier.alpha(0.94f),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun bottomControlBorderColor() = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

@Composable
private fun bottomControlContentColor() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
