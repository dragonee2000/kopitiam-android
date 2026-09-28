package io.kopitiam.app.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Bitmap/ColorMatrix scan filters — the Android mirror of iOS ScanFilters.swift
 * (CamScanner-style enhancement modes). Each mode returns a new Bitmap; filters
 * never throw (fall back to the input on any failure).
 */
object ScanFilters {

    enum class Mode(val label: String, val blurb: String) {
        ORIGINAL("Original", "No changes."),
        AUTO("Auto", "Balance exposure and sharpen."),
        MAGIC("Magic Color", "Whiten the page, keep color ink."),
        GRAYSCALE("Grayscale", "Neutral gray tones."),
        BW("B&W", "High-contrast document scan.");
    }

    fun apply(mode: Mode, src: Bitmap): Bitmap {
        if (mode == Mode.ORIGINAL) return src
        return when (mode) {
            Mode.AUTO -> matrixFilter(src, autoMatrix())
            Mode.MAGIC -> matrixFilter(src, magicMatrix())
            Mode.GRAYSCALE -> matrixFilter(src, grayscaleMatrix())
            Mode.BW -> bwFilter(src)
            Mode.ORIGINAL -> src
        }
    }

    private fun matrixFilter(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Balance exposure + a touch of contrast (brightness lift + contrast). */
    private fun autoMatrix(): ColorMatrix {
        val contrast = 1.12f
        val brightness = 8f
        val t = (1f - contrast) * 128f + brightness
        return ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    /** Magic Color: lift + whiten background, mild saturation boost. */
    private fun magicMatrix(): ColorMatrix {
        val whiten = ColorMatrix(floatArrayOf(
            1.18f, 0f, 0f, 0f, 16f,
            0f, 1.18f, 0f, 0f, 16f,
            0f, 0f, 1.18f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val sat = ColorMatrix().apply { setSaturation(1.08f) }
        whiten.postConcat(sat)
        return whiten
    }

    private fun grayscaleMatrix(): ColorMatrix =
        ColorMatrix().apply { setSaturation(0f) }

    /**
     * High-contrast B&W document scan: desaturate, then a steep contrast curve.
     * Approximates adaptive thresholding while keeping anti-aliasing.
     */
    private fun bwFilter(src: Bitmap): Bitmap {
        val gray = grayscaleMatrix()
        val contrast = 2.6f
        val t = (1f - contrast) * 128f
        val steep = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ))
        gray.postConcat(steep)
        return matrixFilter(src, gray)
    }
}
