package com.fabiantorrestech.visualtimerplus.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import com.fabiantorrestech.visualtimerplus.timer.ONE_HOUR_MILLIS
import com.fabiantorrestech.visualtimerplus.timer.snapDuration
import com.fabiantorrestech.visualtimerplus.util.formatClockTime
import kotlin.math.PI
import kotlin.math.atan2

@Composable
fun QuickTimerDial(
    durationMillis: Long,
    onDurationChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    var totalAngle by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var lastAngle = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            lastAngle = angleFromCenter(offset, center)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val newAngle = angleFromCenter(change.position, center)
                            var delta = newAngle - lastAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            lastAngle = newAngle
                            totalAngle = (totalAngle + delta).coerceIn(0f, 720f)
                            val millis = (totalAngle / 360f * ONE_HOUR_MILLIS).toLong()
                            onDurationChanged(snapDuration(millis))
                        },
                    )
                },
        ) {
            val strokeWidth = size.minDimension * 0.1f
            val padding = strokeWidth
            val arcTopLeft = Offset(padding, padding)
            val arcSize = Size(size.width - padding * 2f, size.height - padding * 2f)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Primary arc (current hour progress)
            // At exactly 360° (1h) or 720° (2h), % gives 0 — keep the ring full instead
            val sweepAngle = when {
                totalAngle <= 0f -> 0f
                totalAngle >= 360f -> 360f
                else -> totalAngle
            }
            if (sweepAngle > 0.5f) {
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            // Second hour: inner ring
            if (totalAngle >= 360f) {
                val innerStroke = strokeWidth * 0.55f
                val innerPad = padding + strokeWidth * 1.6f
                val innerTopLeft = Offset(innerPad, innerPad)
                val innerSize = Size(size.width - innerPad * 2f, size.height - innerPad * 2f)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = innerTopLeft,
                    size = innerSize,
                    style = Stroke(width = innerStroke),
                )
                val secondSweep = ((totalAngle - 360f) / 360f).coerceIn(0f, 1f) * 360f
                if (secondSweep > 0.5f) {
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = secondSweep,
                        useCenter = false,
                        topLeft = innerTopLeft,
                        size = innerSize,
                        style = Stroke(width = innerStroke, cap = StrokeCap.Round),
                    )
                }
            }
        }

        if (showLabel) {
            Text(
                text = if (durationMillis == 0L) "--:--" else durationMillis.formatClockTime(),
                style = MaterialTheme.typography.titleMedium,
                color = onSurfaceColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun angleFromCenter(offset: Offset, center: Offset): Float {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    var angle = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat() + 90f
    if (angle < 0f) angle += 360f
    if (angle >= 360f) angle -= 360f
    return angle
}
