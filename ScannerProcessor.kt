package com.propdf.editor.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image processing utilities for document scanner.
 * Handles color modes, filters, brightness, and edge detection.
 */
class ScannerProcessor {

    companion object {
        private const val HISTOGRAM_STRETCH_THRESHOLD = 5
    }

    // -------------------------------------------------------
    // COLOR MODES
    // -------------------------------------------------------

    suspend fun applyColorMode(bitmap: Bitmap, mode: String): Bitmap = withContext(Dispatchers.Default) {
        when (mode.lowercase()) {
            "gray" -> convertToGrayscale(bitmap)
            "black_white" -> convertToBlackWhite(bitmap)
            "auto" -> autoEnhance(bitmap)
            else -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun convertToBlackWhite(bitmap: Bitmap): Bitmap {
        val gray = convertToGrayscale(bitmap)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                set(floatArrayOf(
                    85f, 85f, 85f, 0f, -128f * 255f,
                    85f, 85f, 85f, 0f, -128f * 255f,
                    85f, 85f, 85f, 0f, -128f * 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }
        canvas.drawBitmap(gray, 0f, 0f, paint)
        gray.recycle()
        return result
    }

    private fun autoEnhance(bitmap: Bitmap): Bitmap {
        val gray = convertToGrayscale(bitmap)
        val histogram = IntArray(256) { 0 }
        val pixels = IntArray(bitmap.width * bitmap.height)
        gray.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        pixels.forEach { pixel ->
            val brightness = Color.red(pixel)
            histogram[brightness]++
        }

        var min = 0
        var max = 255
        val total = pixels.size
        var cumulative = 0

        for (i in 0..255) {
            cumulative += histogram[i]
            if (cumulative > total * HISTOGRAM_STRETCH_THRESHOLD / 100) {
                min = i
                break
            }
        }

        cumulative = 0
        for (i in 255 downTo 0) {
            cumulative += histogram[i]
            if (cumulative > total * HISTOGRAM_STRETCH_THRESHOLD / 100) {
                max = i
                break
            }
        }

        if (max <= min) { gray.recycle(); return bitmap.copy(Bitmap.Config.ARGB_8888, true) }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                set(floatArrayOf(
                    255f / (max - min), 0f, 0f, 0f, -min * 255f / (max - min),
                    0f, 255f / (max - min), 0f, 0f, -min * 255f / (max - min),
                    0f, 0f, 255f / (max - min), 0f, -min * 255f / (max - min),
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }
        canvas.drawBitmap(gray, 0f, 0f, paint)
        gray.recycle()
        return result
    }

    // -------------------------------------------------------
    // FILTERS
    // -------------------------------------------------------

    suspend fun applyFilter(bitmap: Bitmap, filter: String): Bitmap = withContext(Dispatchers.Default) {
        when (filter.lowercase()) {
            "contrast" -> adjustContrast(bitmap, 1.5f)
            "brightness" -> adjustBrightness(bitmap, 30)
            "sharpen" -> sharpen(bitmap)
            "document" -> documentEnhance(bitmap)
            else -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                set(floatArrayOf(
                    contrast, 0f, 0f, 0f, 128f * (1f - contrast),
                    0f, contrast, 0f, 0f, 128f * (1f - contrast),
                    0f, 0f, contrast, 0f, 128f * (1f - contrast),
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun adjustBrightness(bitmap: Bitmap, brightness: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                set(floatArrayOf(
                    1f, 0f, 0f, 0f, brightness.toFloat(),
                    0f, 1f, 0f, 0f, brightness.toFloat(),
                    0f, 0f, 1f, 0f, brightness.toFloat(),
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun sharpen(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                set(floatArrayOf(
                    0f, -1f, 0f, 0f, 255f,
                    -1f, 5f, -1f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
            })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun documentEnhance(bitmap: Bitmap): Bitmap {
        val gray = convertToGrayscale(bitmap)
        val contrasted = adjustContrast(gray, 1.3f)
        gray.recycle()
        return contrasted
    }

    // -------------------------------------------------------
    // TRANSFORMATIONS
    // -------------------------------------------------------

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun crop(bitmap: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height)
        )
        if (safeRect.width() <= 0 || safeRect.height() <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
    }

    fun resize(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (ratio >= 1f) return bitmap
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // -------------------------------------------------------
    // EDGE DETECTION (Sobel)
    // -------------------------------------------------------

    fun detectEdges(bitmap: Bitmap): Bitmap {
        val gray = convertToGrayscale(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        gray.getPixels(pixels, 0, width, 0, 0, width, height)

        val sobelX = intArrayOf(-1, 0, 1, -2, 0, 2, -1, 0, 1)
        val sobelY = intArrayOf(-1, -2, -1, 0, 0, 0, 1, 2, 1)

        val output = IntArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val brightness = Color.red(pixels[idx])
                        val kernelIdx = (ky + 1) * 3 + (kx + 1)
                        gx += brightness * sobelX[kernelIdx]
                        gy += brightness * sobelY[kernelIdx]
                    }
                }
                val magnitude = kotlin.math.min(255, kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt())
                output[y * width + x] = Color.rgb(magnitude, magnitude, magnitude)
            }
        }

        result.setPixels(output, 0, width, 0, 0, width, height)
        gray.recycle()
        return result
    }

    // -------------------------------------------------------
    // PERSPECTIVE CORRECTION (stub for future OpenCV integration)
    // -------------------------------------------------------

    fun perspectiveCorrection(bitmap: Bitmap, srcPoints: List<android.graphics.PointF>): Bitmap {
        // Placeholder for perspective correction
        // In production, integrate OpenCV or manual homography transform
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }
}
