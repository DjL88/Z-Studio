package com.example.editor.service

import android.graphics.ColorMatrix

object ImageFilterProcessor {
    /**
     * Computes the combined ColorMatrix for brightness, contrast, saturation, shadows, highlights,
     * exposure, temperature, tint, and preset filters.
     */
    fun createColorMatrix(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        shadows: Float,
        highlights: Float,
        exposure: Float,
        temperature: Float,
        tint: Float,
        filterName: String,
        whites: Float = 0f,
        blacks: Float = 0f
    ): ColorMatrix {
        val cm = ColorMatrix()

        // 1. Brightness adjustments
        val bMatrix = ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(bMatrix)

        // 2. Contrast adjustments
        val translate = (-.5f * contrast + .5f) * 255f
        val cMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(cMatrix)

        // 3. Saturation adjustments
        val sMatrix = ColorMatrix().apply { setSaturation(saturation) }
        cm.postConcat(sMatrix)

        // 4. Shadows, Highlights, Whites & Blacks adjustments
        val shadowsCoeff = shadows * 30f
        val highlightsCoeff = highlights * 30f
        val whitesCoeff = whites * 40f
        val blacksCoeff = blacks * 40f
        val totalOffset = shadowsCoeff + highlightsCoeff + whitesCoeff + blacksCoeff
        val shadowHighlightMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, totalOffset,
            0f, 1f, 0f, 0f, totalOffset,
            0f, 0f, 1f, 0f, totalOffset,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(shadowHighlightMatrix)

        // 5. Exposure adjustments
        val expMult = if (exposure >= 0f) (1f + exposure) else (1f / (1f - exposure))
        val exposureMatrix = ColorMatrix(floatArrayOf(
            expMult, 0f, 0f, 0f, 0f,
            0f, expMult, 0f, 0f, 0f,
            0f, 0f, expMult, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(exposureMatrix)

        // 6. Temperature (Warmth) adjustments (-1f..1f)
        val tempMatrix = ColorMatrix(floatArrayOf(
            1f + temperature * 0.12f, 0f, 0f, 0f, temperature * 15f,
            0f, 1f + temperature * 0.05f, 0f, 0f, temperature * 5f,
            0f, 0f, 1f - temperature * 0.15f, 0f, -temperature * 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(tempMatrix)

        // 7. Tint adjustments (-1f..1f)
        val tintMatrix = ColorMatrix(floatArrayOf(
            1f + tint * 0.05f, 0f, 0f, 0f, tint * 5f,
            0f, 1f - tint * 0.10f, 0f, 0f, -tint * 10f,
            0f, 0f, 1f + tint * 0.05f, 0f, tint * 5f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(tintMatrix)

        // 8. Preset filters
        when (filterName) {
            "Monochrome" -> {
                val bwMatrix = ColorMatrix().apply { setSaturation(0.0f) }
                cm.postConcat(bwMatrix)
            }
            "Warm Sepia" -> {
                val sepiaMatrix = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(sepiaMatrix)
            }
            "Cinematic Vintage" -> {
                val vintageMatrix = ColorMatrix(floatArrayOf(
                    0.95f, 0f, 0f, 0f, 10f,
                    0f, 0.90f, 0f, 0f, 5f,
                    0f, 0f, 0.80f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(vintageMatrix)
            }
            "Cool Cyan" -> {
                val cyanMatrix = ColorMatrix(floatArrayOf(
                    0.80f, 0f, 0f, 0f, -15f,
                    0f, 0.95f, 0f, 0f, 10f,
                    0f, 0f, 1.10f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyanMatrix)
            }
            "High Contrast Punch" -> {
                val punchMatrix = ColorMatrix().apply { setSaturation(1.3f) }
                val highContr = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -25f,
                    0f, 1.2f, 0f, 0f, -25f,
                    0f, 0f, 1.2f, 0f, -25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(punchMatrix)
                cm.postConcat(highContr)
            }
            "Golden Hour" -> {
                val goldenMatrix = ColorMatrix(floatArrayOf(
                    1.25f, 0f, 0f, 0f, 15f,
                    0f, 1.10f, 0f, 0f, 5f,
                    0f, 0f, 0.90f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(goldenMatrix)
            }
            "Forest Green" -> {
                val forestMatrix = ColorMatrix(floatArrayOf(
                    0.90f, 0f, 0f, 0f, -10f,
                    0f, 1.20f, 0f, 0f, 15f,
                    0f, 0f, 0.95f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(forestMatrix)
            }
            "Cyberpunk" -> {
                val cyberMatrix = ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 25f,
                    0f, 0.90f, 0f, 0f, -10f,
                    0f, 0f, 1.25f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyberMatrix)
            }
            "Moody Noir" -> {
                val bw = ColorMatrix().apply { setSaturation(0.0f) }
                val noirMatrix = ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -35f,
                    0f, 1.4f, 0f, 0f, -35f,
                    0f, 0f, 1.4f, 0f, -35f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(bw)
                cm.postConcat(noirMatrix)
            }
            "Soft Pastel" -> {
                val pastelMatrix = ColorMatrix(floatArrayOf(
                    0.95f, 0f, 0f, 0f, 20f,
                    0f, 0.95f, 0f, 0f, 20f,
                    0f, 0f, 0.95f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                val desatMatrix = ColorMatrix().apply { setSaturation(0.8f) }
                cm.postConcat(pastelMatrix)
                cm.postConcat(desatMatrix)
            }
        }

        return cm
    }
}
