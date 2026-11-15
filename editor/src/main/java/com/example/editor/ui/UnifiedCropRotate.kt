package com.example.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.*

@Composable
fun UnifiedCropRotate(
    imageUri: String,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    straighten: Float,
    aspectRatio: Float, // target aspect ratio (width/height)
    onCropRotateChange: (cL: Float, cT: Float, cR: Float, cB: Float, angle: Float) -> Unit,
    modifier: Modifier = Modifier,
    colorMatrix: ColorMatrix? = null,
    accentColor: Color = Color(0xFFE5A93B),
    borderColor: Color = Color(0xFF2C2C2E),
    textColorPrimary: Color = Color(0xFFE5E5EA),
    textColorSecondary: Color = Color(0xFF8E8E93)
) {
    var containerSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                containerSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
            .testTag("unified_crop_rotate_container"),
        contentAlignment = Alignment.Center
    ) {
        if (containerSize == Size.Zero) return@Box

        // Calculate the central crop box dimensions based on the target aspect ratio and container size
        val maxBoxWidth = containerSize.width * 0.85f
        val maxBoxHeight = containerSize.height * 0.75f

        val (cropBoxWidth, cropBoxHeight) = remember(aspectRatio, containerSize) {
            val candidateHeight = maxBoxWidth / aspectRatio
            if (candidateHeight <= maxBoxHeight) {
                maxBoxWidth to candidateHeight
            } else {
                (maxBoxHeight * aspectRatio) to maxBoxHeight
            }
        }

        // We render the image in the container.
        val imageWidth = containerSize.width
        val imageHeight = containerSize.height

        // Minimum scale to ensure the image always covers the crop box area:
        val minScaleX = cropBoxWidth / imageWidth
        val minScaleY = cropBoxHeight / imageHeight
        val minScale = maxOf(minScaleX, minScaleY, 1.0f)

        // Local state for interactive gestures
        var scale by remember { mutableStateOf(minScale) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var rotation by remember { mutableStateOf(straighten) }

        // Track the last photo and aspect ratio we initialized for, to avoid resetting during active gestures!
        var initializedKey by remember { mutableStateOf("") }
        val currentKey = "${imageUri}_${aspectRatio}"

        // Helper function to calculate crop coordinates from scale, pan, and rotation
        fun updateCropState(newScale: Float, newPan: Offset, newRotation: Float) {
            // Center of the viewport/cropbox maps to normalized image coordinates (u, v)
            val dx2 = -newPan.x
            val dy2 = -newPan.y

            // Rotate back to image's coordinates to handle straightened angles correctly
            val angleRad = (-newRotation * (Math.PI.toFloat() / 180f))
            val cosVal = cos(angleRad)
            val sinVal = sin(angleRad)
            val unrotatedX = dx2 * cosVal - dy2 * sinVal
            val unrotatedY = dx2 * sinVal + dy2 * cosVal

            // Un-scale
            val imgX = unrotatedX / newScale
            val imgY = unrotatedY / newScale

            // Normalized center of crop window
            val u = 0.5f + imgX / imageWidth
            val v = 0.5f + imgY / imageHeight

            // Normalized width and height of the crop box inside the image bounds
            val cropWidthNormalized = (cropBoxWidth / newScale) / imageWidth
            val cropHeightNormalized = (cropBoxHeight / newScale) / imageHeight

            // Coerce the center u, v to ensure the crop bounds never spill outside [0, 1]
            // This prevents aspect ratio distortion when reaching boundaries!
            val minU = cropWidthNormalized / 2f
            val maxU = 1f - minU
            val uCoerced = if (maxU >= minU) u.coerceIn(minU, maxU) else 0.5f

            val minV = cropHeightNormalized / 2f
            val maxV = 1f - minV
            val vCoerced = if (maxV >= minV) v.coerceIn(minV, maxV) else 0.5f

            val cL = (uCoerced - cropWidthNormalized / 2f).coerceIn(0f, 1f)
            val cT = (vCoerced - cropHeightNormalized / 2f).coerceIn(0f, 1f)
            val cR = (uCoerced + cropWidthNormalized / 2f).coerceIn(0f, 1f)
            val bL = (vCoerced + cropHeightNormalized / 2f).coerceIn(0f, 1f)

            // Adjust coordinates to ensure correct aspect ratio
            val width = cR - cL
            val height = bL - cT
            if (width <= 0.05f || height <= 0.05f) return

            onCropRotateChange(cL, cT, cR, bL, newRotation)
        }

        // Sync initial scale and pan based on incoming cropLeft, cropTop, cropRight, cropBottom ONLY on target change
        LaunchedEffect(currentKey, containerSize) {
            if (containerSize != Size.Zero && initializedKey != currentKey) {
                initializedKey = currentKey
                val cw = cropRight - cropLeft
                val ch = cropBottom - cropTop
                if (cw > 0f && ch > 0f) {
                    val targetScale = maxOf(1f / maxOf(cw, ch) * minScale, minScale)
                    scale = targetScale

                    val cropCenterX = (cropLeft + cropRight) / 2f
                    val cropCenterY = (cropTop + cropBottom) / 2f
                    pan = Offset(
                        x = -(cropCenterX - 0.5f) * imageWidth * targetScale,
                        y = -(cropCenterY - 0.5f) * imageHeight * targetScale
                    )
                } else {
                    scale = minScale
                    pan = Offset.Zero
                }
                rotation = straighten
            }
        }

        // Sync local state when external rotation dial changes
        LaunchedEffect(straighten) {
            if (rotation != straighten) {
                rotation = straighten
                updateCropState(scale, pan, straighten)
            }
        }

        // The transformed image view
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(containerSize) {
                    // Lock pan/zoom to ignore noisy rotation events, making pinch-zoom butter-smooth!
                    detectTransformGestures(panZoomLock = true) { _, panAmount, zoom, _ ->
                        // 1. Update scale
                        val prevScale = scale
                        scale = (scale * zoom).coerceIn(minScale, minScale * 8f)

                        // 2. Update pan with containment bounds
                        val maxPanX = (scale - minScaleX) * imageWidth / 2f
                        val maxPanY = (scale - minScaleY) * imageHeight / 2f
                        pan = Offset(
                            x = (pan.x + panAmount.x).coerceIn(-maxOf(0f, maxPanX), maxOf(0f, maxPanX)),
                            y = (pan.y + panAmount.y).coerceIn(-maxOf(0f, maxPanY), maxOf(0f, maxPanY))
                        )

                        // 3. Trigger state update callback
                        updateCropState(scale, pan, rotation)
                    }
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUri)
                    .size(coil.size.Size.ORIGINAL) // Fetch high-resolution representation
                    .build(),
                contentDescription = "Crop and rotate viewport source",
                contentScale = ContentScale.Fit,
                colorFilter = colorMatrix?.let { ColorFilter.colorMatrix(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                        rotationZ = rotation
                    }
            )
        }

        // Semi-transparent overlay mask to highlight the cropped region
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("crop_boundary_grid")
        ) {
            val l = (size.width - cropBoxWidth) / 2f
            val t = (size.height - cropBoxHeight) / 2f
            val r = l + cropBoxWidth
            val b = t + cropBoxHeight

            // Dim outside crop window
            drawRect(color = Color.Black.copy(alpha = 0.75f), size = Size(l, size.height))
            drawRect(color = Color.Black.copy(alpha = 0.75f), topLeft = Offset(l, 0f), size = Size(cropBoxWidth, t))
            drawRect(color = Color.Black.copy(alpha = 0.75f), topLeft = Offset(r, 0f), size = Size(size.width - r, size.height))
            drawRect(color = Color.Black.copy(alpha = 0.75f), topLeft = Offset(l, b), size = Size(cropBoxWidth, size.height - b))

            // Draw crop window borders
            drawRect(
                color = accentColor,
                topLeft = Offset(l, t),
                size = Size(cropBoxWidth, cropBoxHeight),
                style = Stroke(width = 3f)
            )

            // Grid lines (Rule of Thirds)
            val col1 = l + cropBoxWidth / 3f
            val col2 = l + 2 * cropBoxWidth / 3f
            val row1 = t + cropBoxHeight / 3f
            val row2 = t + 2 * cropBoxHeight / 3f

            drawLine(Color.White.copy(alpha = 0.5f), Offset(col1, t), Offset(col1, b), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(col2, t), Offset(col2, b), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(l, row1), Offset(r, row1), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(l, row2), Offset(r, row2), strokeWidth = 1f)

            // Draw professional bracket corners
            val handleLen = 30f
            val handleStroke = 6f
            
            // Top Left Corner
            drawLine(accentColor, Offset(l, t), Offset(l + handleLen, t), strokeWidth = handleStroke)
            drawLine(accentColor, Offset(l, t), Offset(l, t + handleLen), strokeWidth = handleStroke)
            
            // Top Right Corner
            drawLine(accentColor, Offset(r, t), Offset(r - handleLen, t), strokeWidth = handleStroke)
            drawLine(accentColor, Offset(r, t), Offset(r, t + handleLen), strokeWidth = handleStroke)

            // Bottom Left Corner
            drawLine(accentColor, Offset(l, b), Offset(l + handleLen, b), strokeWidth = handleStroke)
            drawLine(accentColor, Offset(l, b), Offset(l, b - handleLen), strokeWidth = handleStroke)

            // Bottom Right Corner
            drawLine(accentColor, Offset(r, b), Offset(r - handleLen, b), strokeWidth = handleStroke)
            drawLine(accentColor, Offset(r, b), Offset(r, b - handleLen), strokeWidth = handleStroke)
        }

        // Angle indicator floating pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = String.format("Straighten Angle: %+.1f°", rotation),
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
