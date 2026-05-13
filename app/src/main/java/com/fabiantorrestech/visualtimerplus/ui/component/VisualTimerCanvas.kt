package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.fabiantorrestech.visualtimerplus.timer.DRAG_MAX_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.ONE_HOUR_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.TimerInstance
import com.fabiantorrestech.visualtimerplus.timer.TimerStatus
import com.fabiantorrestech.visualtimerplus.timer.clampDuration
import com.fabiantorrestech.visualtimerplus.timer.snapDuration
import com.fabiantorrestech.visualtimerplus.ui.theme.TimerRed
import com.fabiantorrestech.visualtimerplus.ui.theme.TimerRedDark
import kotlin.math.atan2
import kotlin.math.min

@Composable
fun VisualTimerCanvas(
    timer: TimerInstance,
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isOledMode: Boolean = false,
    onDragActiveChanged: (Boolean) -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val displayMs = timer.displayMillis

    val isAlarming = timer.status == TimerStatus.Overtime
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blinkAlpha",
    )
    val canvasAlpha = if (isAlarming) blinkAlpha else 1f

    val baseArcFraction: Float
    val overlayArcFraction: Float
    val showTwoLayer: Boolean

    val useFullClock = timer.settings.fullClockMode && timer.status != TimerStatus.Idle

    if (useFullClock) {
        showTwoLayer = false
        overlayArcFraction = 0f
        val total = timer.selectedDurationMillis.coerceAtLeast(1L)
        baseArcFraction = (displayMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
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
    }

    val currentTimer by rememberUpdatedState(timer)

    var totalAngle by remember { mutableFloatStateOf(0f) }
    var prevAngle by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.pointerInput(timer.status, timer.settings.clockwiseModeEnabled) {
            if (timer.status != TimerStatus.Idle && timer.status != TimerStatus.Paused) return@pointerInput
            detectDragGestures(
                onDragStart = { offset ->
                    val angle = toDirectionalAngle(
                        offset, size.width.toFloat(), size.height.toFloat(), currentTimer.settings.clockwiseModeEnabled,
                    )
                    prevAngle = angle
                    // Initialize from current duration — prevents a jump when the touch
                    // position doesn't match the handle position (e.g. touching at 2:00:00).
                    // Uses rememberUpdatedState so the second drag reads the updated duration,
                    // not the stale value captured when pointerInput was first launched.
                    totalAngle = currentTimer.displayMillis.toFloat() / ONE_HOUR_MILLIS.toFloat() * 360f
                    onDragActiveChanged(true)
                },
                onDragEnd = { onDragActiveChanged(false) },
                onDragCancel = { onDragActiveChanged(false) },
                onDrag = { change, _ ->
                    val newAngle = toDirectionalAngle(
                        change.position, size.width.toFloat(), size.height.toFloat(), currentTimer.settings.clockwiseModeEnabled,
                    )
                    // Shortest-path delta handles the 0°/360° wrap-around without any
                    // threshold checks, making boundary crossing robust against fast swipes
                    var delta = newAngle - prevAngle
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f

                    totalAngle = (totalAngle + delta).coerceIn(0f, 720f)
                    prevAngle = newAngle

                    val rawMs = totalAngle / 360f * ONE_HOUR_MILLIS
                    onDurationSelected(snapDuration(clampDuration(rawMs.toLong()).coerceAtMost(DRAG_MAX_MILLIS)))
                },
            )
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = canvasAlpha }) {
            val diameter = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f
            val trackColor = if (isOledMode) colorScheme.background else colorScheme.secondaryContainer.copy(alpha = 0.88f)
            val outlineColor = colorScheme.outline.copy(alpha = if (isOledMode) 0.28f else 0.18f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(diameter, diameter)
            val sweepSign = if (timer.settings.clockwiseModeEnabled) 1f else -1f

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
            if (handleFraction > 0f || timer.status == TimerStatus.Idle || timer.status == TimerStatus.Paused) {
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
