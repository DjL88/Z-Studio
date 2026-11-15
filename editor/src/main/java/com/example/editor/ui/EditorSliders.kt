package com.example.editor.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorSliders(
    initialExposure: Float,
    initialContrast: Float,
    initialSaturation: Float,
    onValuesChanged: (exposure: Float, contrast: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFE5A93B),
    borderColor: Color = Color(0xFF2C2C2E),
    textColorPrimary: Color = Color(0xFFE5E5EA),
    textColorSecondary: Color = Color(0xFF8E8E93),
    onValueChangeFinished: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    var exposure by remember(initialExposure) { mutableStateOf(initialExposure) }
    var contrast by remember(initialContrast) { mutableStateOf(initialContrast) }
    var saturation by remember(initialSaturation) { mutableStateOf(initialSaturation) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Sliders Header / Info with Black & White quick toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BASIC ADJUSTMENTS (EDITOR MODULE)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColorSecondary,
                letterSpacing = 1.sp
            )

            // Modern, easy-to-tap finger-friendly B&W quick-toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "B&W",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (saturation == 0f) accentColor else textColorSecondary
                )
                Switch(
                    checked = saturation == 0f,
                    onCheckedChange = { isChecked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val newSat = if (isChecked) 0f else 1f
                        saturation = newSat
                        onValuesChanged(exposure, contrast, newSat)
                        onValueChangeFinished?.invoke()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.5f),
                        uncheckedThumbColor = textColorSecondary,
                        uncheckedTrackColor = borderColor
                    ),
                    modifier = Modifier.testTag("editor_bw_toggle")
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Exposure (Default: 0.0f, Range: -2.0f..2.0f)
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            var lastExposure by remember { mutableStateOf(exposure) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                onValueChangeFinished?.invoke()
                                exposure = 0f
                                onValuesChanged(0f, contrast, saturation)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exposure",
                    fontSize = 11.sp,
                    color = textColorPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%+.2f", exposure),
                    fontSize = 10.sp,
                    color = accentColor
                )
            }
            Slider(
                value = exposure,
                onValueChange = { newVal ->
                    if ((lastExposure < 0f && newVal >= 0f) || (lastExposure > 0f && newVal <= 0f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    lastExposure = newVal
                    exposure = newVal
                    onValuesChanged(newVal, contrast, saturation)
                },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = -2f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = borderColor
                ),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("editor_exposure_slider")
            )
        }

        // Contrast (Default: 1.0f, Range: 0.0f..2.0f)
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            var lastContrast by remember { mutableStateOf(contrast) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                onValueChangeFinished?.invoke()
                                contrast = 1f
                                onValuesChanged(exposure, 1f, saturation)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contrast",
                    fontSize = 11.sp,
                    color = textColorPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.2f", contrast),
                    fontSize = 10.sp,
                    color = accentColor
                )
            }
            Slider(
                value = contrast,
                onValueChange = { newVal ->
                    if ((lastContrast < 1f && newVal >= 1f) || (lastContrast > 1f && newVal <= 1f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    lastContrast = newVal
                    contrast = newVal
                    onValuesChanged(exposure, newVal, saturation)
                },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = borderColor
                ),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("editor_contrast_slider")
            )
        }

        // Saturation (Default: 1.0f, Range: 0.0f..2.0f)
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            var lastSaturation by remember { mutableStateOf(saturation) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                onValueChangeFinished?.invoke()
                                saturation = 1f
                                onValuesChanged(exposure, contrast, 1f)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saturation",
                    fontSize = 11.sp,
                    color = textColorPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.2f", saturation),
                    fontSize = 10.sp,
                    color = accentColor
                )
            }
            Slider(
                value = saturation,
                onValueChange = { newVal ->
                    if ((lastSaturation < 1f && newVal >= 1f) || (lastSaturation > 1f && newVal <= 1f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    lastSaturation = newVal
                    saturation = newVal
                    onValuesChanged(exposure, contrast, newVal)
                },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = borderColor
                ),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("editor_saturation_slider")
            )
        }
    }
}
