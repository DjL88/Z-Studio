package com.example.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun RotationDial(
    angle: Float,
    onAngleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -45f..45f,
    accentColor: Color = Color(0xFFE5A93B),
    borderColor: Color = Color(0xFF2C2C2E),
    textColorPrimary: Color = Color(0xFFE5E5EA),
    textColorSecondary: Color = Color(0xFF8E8E93),
    onValueChangeFinished: (() -> Unit)? = null
) {
    val density = LocalDensity.current

    // Number of pixels per degree on the dial
    val pxPerDegree = with(density) { 6.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(borderColor.copy(alpha = 0.3f))
            .pointerInput(angle) {
                detectTapGestures(
                    onDoubleTap = {
                        onValueChangeFinished?.invoke()
                        onAngleChange(0f)
                    }
                )
            }
            .pointerInput(angle, range) {
                detectDragGestures(
                    onDragEnd = {
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        onValueChangeFinished?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaAngle = -dragAmount.x / pxPerDegree
                        val targetAngle = (angle + deltaAngle).coerceIn(range.start, range.endInclusive)
                        onAngleChange(targetAngle)
                    }
                )
            }
            .testTag("rotation_dial_control")
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerX = widthPx / 2f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerAngle = angle

            // Determine visible degree range
            val halfWidthInDegrees = centerX / pxPerDegree
            val startDegree = (centerAngle - halfWidthInDegrees).roundToInt() - 1
            val endDegree = (centerAngle + halfWidthInDegrees).roundToInt() + 1

            for (deg in startDegree..endDegree) {
                if (deg < range.start.roundToInt() || deg > range.endInclusive.roundToInt()) continue

                // Calculate x position of this tick
                val tickX = centerX + (deg - centerAngle) * pxPerDegree

                // Tick styling: taller for 5s/10s
                val isMajor = deg % 5 == 0
                val isLabel = deg % 10 == 0 || deg == 0
                
                val tickHeight = when {
                    deg == 0 -> heightPx * 0.45f
                    isMajor -> heightPx * 0.32f
                    else -> heightPx * 0.18f
                }
                
                val tickColor = when {
                    deg == 0 -> accentColor
                    isMajor -> textColorPrimary.copy(alpha = 0.8f)
                    else -> textColorSecondary.copy(alpha = 0.4f)
                }

                val strokeWidth = when {
                    deg == 0 -> 2.dp.toPx()
                    isMajor -> 1.5.dp.toPx()
                    else -> 1.dp.toPx()
                }

                // Draw tick line starting from the top
                drawLine(
                    color = tickColor,
                    start = Offset(tickX, 0f),
                    end = Offset(tickX, tickHeight),
                    strokeWidth = strokeWidth
                )

                // Draw label for major ticks
                if (isLabel) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = if (deg == 0) accentColor.toArgb() else textColorPrimary.copy(alpha = 0.6f).toArgb()
                            textSize = with(density) { 9.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        
                        canvas.nativeCanvas.drawText(
                            if (deg == 0) "0°" else "${if (deg > 0) "+" else ""}$deg°",
                            tickX,
                            heightPx - 10.dp.toPx(),
                            paint
                        )
                    }
                }
            }

            // Draw a baseline across the dial
            drawLine(
                color = textColorSecondary.copy(alpha = 0.15f),
                start = Offset(0f, heightPx * 0.5f),
                end = Offset(widthPx, heightPx * 0.5f),
                strokeWidth = 1.dp.toPx()
            )

            // Draw center pointer (triangle indicating the exact current value at centerX)
            val pointerPath = Path().apply {
                moveTo(centerX - 6.dp.toPx(), heightPx - 2.dp.toPx())
                lineTo(centerX + 6.dp.toPx(), heightPx - 2.dp.toPx())
                lineTo(centerX, heightPx - 8.dp.toPx())
                close()
            }
            drawPath(
                path = pointerPath,
                color = accentColor
            )

            // Center vertical reference line
            drawLine(
                color = accentColor.copy(alpha = 0.5f),
                start = Offset(centerX, 0f),
                end = Offset(centerX, heightPx - 8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
