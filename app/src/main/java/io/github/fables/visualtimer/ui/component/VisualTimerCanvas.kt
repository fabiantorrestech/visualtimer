package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.fabiantorrestech.visualtimerplus.timer.MAX_DURATION_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.TimerState
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.ui.theme.TimerRed
import kotlin.math.atan2
import kotlin.math.min

@Composable
fun VisualTimerCanvas(
    state: TimerState,
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeDurationMillis = state.selectedDurationMillis.coerceAtLeast(1L)
    val fractionRemaining = when (state.status) {
        TimerStatus.Idle -> state.selectedDurationMillis / MAX_DURATION_MILLIS.toFloat()
        TimerStatus.Running -> state.remainingMillis / activeDurationMillis.toFloat()
        TimerStatus.Paused -> (state.pausedRemainingMillis ?: state.remainingMillis) / activeDurationMillis.toFloat()
        TimerStatus.Finished -> 0f
    }.coerceIn(0f, 1f)

    Box(
        modifier = modifier.pointerInput(state.status, state.clockwiseModeEnabled) {
            if (state.status == TimerStatus.Running) return@pointerInput
            detectDragGestures(
                onDragStart = { offset ->
                    onDurationSelected(
                        offset.toDurationMillis(
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            clockwise = state.clockwiseModeEnabled,
                        ),
                    )
                },
                onDrag = { change, _ ->
                    onDurationSelected(
                        change.position.toDurationMillis(
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            clockwise = state.clockwiseModeEnabled,
                        ),
                    )
                },
            )
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f
            val trackColor = if (state.isOledMode) {
                colorScheme.background
            } else {
                colorScheme.secondaryContainer.copy(alpha = 0.88f)
            }
            val outlineColor = colorScheme.outline.copy(alpha = if (state.isOledMode) 0.28f else 0.18f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(diameter, diameter)

            drawCircle(color = trackColor, radius = radius, center = center)

            if (fractionRemaining > 0f) {
                drawArc(
                    color = TimerRed,
                    startAngle = -90f,
                    sweepAngle = if (state.clockwiseModeEnabled) {
                        360f * fractionRemaining
                    } else {
                        -360f * fractionRemaining
                    },
                    useCenter = true,
                    topLeft = arcTopLeft,
                    size = arcSize,
                )
            }

            drawCircle(
                color = outlineColor,
                radius = radius,
                center = center,
                style = Stroke(width = diameter * 0.01f),
            )
        }
    }
}

private fun Offset.toDurationMillis(width: Float, height: Float, clockwise: Boolean): Long {
    val center = Offset(width / 2f, height / 2f)
    val dx = x - center.x
    val dy = y - center.y
    val rawAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val normalizedAngle = (rawAngle + 450f) % 360f
    val directionalAngle = if (clockwise) {
        normalizedAngle
    } else {
        (360f - normalizedAngle) % 360f
    }
    val fraction = directionalAngle / 360f
    return (fraction * MAX_DURATION_MILLIS).toLong()
}
