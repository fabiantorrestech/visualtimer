package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.fabiantorrestech.visualtimerplus.timer.DRAG_MAX_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.ONE_HOUR_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.TimerState
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.timer.clampDuration
import com.fabiantorrestech.visualtimerplus.timer.snapDuration
import com.fabiantorrestech.visualtimerplus.ui.theme.TimerRed
import com.fabiantorrestech.visualtimerplus.ui.theme.TimerRedDark
import kotlin.math.atan2
import kotlin.math.min

@Composable
fun VisualTimerCanvas(
    state: TimerState,
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val displayMs = state.displayMillis

    val baseArcFraction: Float
    val overlayArcFraction: Float
    val showTwoLayer: Boolean

    when {
        displayMs >= DRAG_MAX_MILLIS -> {
            showTwoLayer = true
            baseArcFraction = 1f
            overlayArcFraction = 1f
        }
        displayMs > ONE_HOUR_MILLIS -> {
            showTwoLayer = true
            baseArcFraction = 1f
            overlayArcFraction = ((displayMs - ONE_HOUR_MILLIS).toFloat() / ONE_HOUR_MILLIS).coerceIn(0f, 1f)
        }
        else -> {
            showTwoLayer = false
            baseArcFraction = (displayMs.toFloat() / ONE_HOUR_MILLIS).coerceIn(0f, 1f)
            overlayArcFraction = 0f
        }
    }

    var dragLap by remember { mutableIntStateOf(0) }
    var prevAngle by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.pointerInput(state.status, state.clockwiseModeEnabled) {
            if (state.status == TimerStatus.Running) return@pointerInput
            detectDragGestures(
                onDragStart = { offset ->
                    val angle = toDirectionalAngle(
                        offset, size.width.toFloat(), size.height.toFloat(), state.clockwiseModeEnabled,
                    )
                    prevAngle = angle
                    dragLap = if (state.displayMillis >= ONE_HOUR_MILLIS) 1 else 0
                },
                onDrag = { change, _ ->
                    val newAngle = toDirectionalAngle(
                        change.position, size.width.toFloat(), size.height.toFloat(), state.clockwiseModeEnabled,
                    )
                    if (prevAngle > 270f && newAngle < 90f) {
                        dragLap = (dragLap + 1).coerceAtMost(1)
                    } else if (prevAngle < 90f && newAngle > 270f) {
                        dragLap = (dragLap - 1).coerceAtLeast(0)
                    }
                    prevAngle = newAngle
                    onDurationSelected(computeDragDuration(newAngle, dragLap))
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
            val sweepSign = if (state.clockwiseModeEnabled) 1f else -1f

            drawCircle(color = trackColor, radius = radius, center = center)

            if (showTwoLayer) {
                // Base layer: full dark-red circle = 60 min base
                drawCircle(color = TimerRedDark, radius = radius, center = center)
                // Overlay layer: bright-red arc for time above 60 min
                if (overlayArcFraction > 0f) {
                    drawArc(
                        color = TimerRed,
                        startAngle = -90f,
                        sweepAngle = sweepSign * 360f * overlayArcFraction,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize,
                    )
                }
            } else {
                if (baseArcFraction > 0f) {
                    drawArc(
                        color = TimerRed,
                        startAngle = -90f,
                        sweepAngle = sweepSign * 360f * baseArcFraction,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize,
                    )
                }
            }

            drawCircle(
                color = outlineColor,
                radius = radius,
                center = center,
                style = Stroke(width = diameter * 0.01f),
            )

            // Handle dot
            val handleFraction = if (showTwoLayer) overlayArcFraction else baseArcFraction
            if (handleFraction > 0f || displayMs > 0L) {
                val handleAngleRad = Math.toRadians(
                    ((-90f + sweepSign * 360f * handleFraction).toDouble()),
                )
                val handleX = center.x + radius * Math.cos(handleAngleRad).toFloat()
                val handleY = center.y + radius * Math.sin(handleAngleRad).toFloat()
                drawCircle(
                    color = colorScheme.onSurface.copy(alpha = 0.8f),
                    radius = diameter * 0.035f,
                    center = Offset(handleX, handleY),
                )
            }
        }
    }
}

private fun toDirectionalAngle(offset: Offset, width: Float, height: Float, clockwise: Boolean): Float {
    val center = Offset(width / 2f, height / 2f)
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val rawAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val normalizedAngle = (rawAngle + 450f) % 360f
    return if (clockwise) normalizedAngle else (360f - normalizedAngle) % 360f
}

private fun computeDragDuration(angle: Float, lap: Int): Long {
    val fraction = angle / 360f
    val raw = (fraction + lap) * ONE_HOUR_MILLIS
    return snapDuration(clampDuration(raw.toLong()).coerceAtMost(DRAG_MAX_MILLIS))
}
