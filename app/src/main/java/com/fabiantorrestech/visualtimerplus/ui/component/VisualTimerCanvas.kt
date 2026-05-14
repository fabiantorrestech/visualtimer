package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
    onPreviewDurationChanged: (Long) -> Unit,
    onDragCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isOledMode: Boolean = false,
    onDragActiveChanged: (Boolean) -> Unit = {},
    displayMillisOverride: Long? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val displayMs = displayMillisOverride ?: timer.displayMillis

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
    val currentDisplayMillis by rememberUpdatedState(displayMillisOverride ?: timer.displayMillis)
    val previewDurationChanged by rememberUpdatedState(onPreviewDurationChanged)
    val dragCommit by rememberUpdatedState(onDragCommit)

    val startFillAnim = remember { Animatable(-1f) }
    var prevStatus by remember { mutableStateOf(timer.status) }

    LaunchedEffect(timer.status) {
        if (timer.status == TimerStatus.Running && prevStatus == TimerStatus.Idle) {
            val target = if (timer.settings.fullClockMode) 1f
                         else (timer.selectedDurationMillis.toFloat() / ONE_HOUR_MILLIS.toFloat()).coerceIn(0f, 1f)
            startFillAnim.snapTo(0f)
            startFillAnim.animateTo(target, animationSpec = tween(700, easing = FastOutSlowInEasing))
            startFillAnim.snapTo(-1f)
        }
        prevStatus = timer.status
    }

    val isStartAnimating = startFillAnim.value >= 0f
    val effectiveShowTwoLayer = if (isStartAnimating) false else showTwoLayer
    val effectiveBaseArcFraction = if (isStartAnimating) startFillAnim.value else baseArcFraction
    val effectiveOverlayArcFraction = if (isStartAnimating) 0f else overlayArcFraction

    var totalAngle by remember { mutableFloatStateOf(0f) }
    var prevAngle by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.pointerInput(timer.status, timer.settings.clockwiseModeEnabled) {
            if (timer.status != TimerStatus.Idle && timer.status != TimerStatus.Paused) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startPos = down.position

                // If touch starts inside the circle, claim winding immediately.
                // Outside the circle, use direction-based intent detection so horizontal
                // swipes on the margin still reach the pager.
                val circleCenter = Offset(size.width / 2f, size.height / 2f)
                val circleRadius = min(size.width, size.height) / 2f
                val touchedInsideCircle = (down.position - circleCenter).getDistance() <= circleRadius

                var claimedForWinding = touchedInsideCircle
                if (!touchedInsideCircle) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break

                        val dragOffset = change.position - startPos
                        if (dragOffset.getDistance() > viewConfiguration.touchSlop) {
                            val isHorizontalSwipe = kotlin.math.abs(dragOffset.x) > kotlin.math.abs(dragOffset.y) * 2f
                            if (!isHorizontalSwipe) {
                                change.consume()
                                claimedForWinding = true
                            }
                            break
                        }
                    }
                }
                if (!claimedForWinding) return@awaitEachGesture

                // Initialize winding state — same logic as former onDragStart.
                // Uses rememberUpdatedState so a second drag reads the updated duration,
                // not the stale value captured when pointerInput was first launched.
                prevAngle = toDirectionalAngle(
                    down.position, size.width.toFloat(), size.height.toFloat(), currentTimer.settings.clockwiseModeEnabled,
                )
                totalAngle = currentDisplayMillis.toFloat() / ONE_HOUR_MILLIS.toFloat() * 360f
                onDragActiveChanged(true)
                val initialPreviewDuration = snapDuration(
                    clampDuration(currentDisplayMillis).coerceAtMost(DRAG_MAX_MILLIS),
                )
                var latestPreviewDuration = initialPreviewDuration

                // Track drag — same logic as former onDrag.
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
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
                    val previewDuration = snapDuration(
                        clampDuration(rawMs.toLong()).coerceAtMost(DRAG_MAX_MILLIS),
                    )
                    if (previewDuration != latestPreviewDuration) {
                        latestPreviewDuration = previewDuration
                        previewDurationChanged(previewDuration)
                    }
                    if (!change.pressed) break
                    change.consume()
                }
                dragCommit(latestPreviewDuration)
                onDragActiveChanged(false)
            }
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = canvasAlpha }) {
            val diameter = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f
            val trackColor = if (isOledMode) colorScheme.background else colorScheme.secondaryContainer.copy(alpha = 0.88f)
            val outlineColor = colorScheme.outline.copy(alpha = if (isOledMode) 0.28f else 0.18f)
            val pivotFillColor = colorScheme.background
            val pivotOutlineColor = colorScheme.onSurface.copy(alpha = if (isOledMode) 0.34f else 0.18f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(diameter, diameter)
            val sweepSign = if (timer.settings.clockwiseModeEnabled) 1f else -1f

            drawCircle(color = trackColor, radius = radius, center = center)

            if (effectiveShowTwoLayer) {
                // Base layer: full dark-red circle = 60 min base
                drawCircle(color = TimerRedDark, radius = radius, center = center)
                // Overlay layer: bright-red arc for time above 60 min
                if (effectiveOverlayArcFraction > 0f) {
                    drawArc(
                        color = TimerRed,
                        startAngle = -90f,
                        sweepAngle = sweepSign * 360f * effectiveOverlayArcFraction,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize,
                    )
                }
            } else {
                if (effectiveBaseArcFraction > 0f) {
                    drawArc(
                        color = TimerRed,
                        startAngle = -90f,
                        sweepAngle = sweepSign * 360f * effectiveBaseArcFraction,
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
            drawCircle(
                color = pivotFillColor,
                radius = diameter * 0.026f,
                center = center,
            )
            drawCircle(
                color = pivotOutlineColor,
                radius = diameter * 0.026f,
                center = center,
                style = Stroke(width = diameter * 0.006f),
            )

            // Handle dot
            val handleFraction = if (effectiveShowTwoLayer) effectiveOverlayArcFraction else effectiveBaseArcFraction
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
