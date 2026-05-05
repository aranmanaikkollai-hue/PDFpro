package com.propdf.editor.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

class ScannerProcessor {

    fun applyColorMode(bitmap: Bitmap, mode: String): Bitmap {
        return when (mode) {
            "gray" -> toGrayscale(bitmap)
            "black_white" -> toBlackAndWhite(bitmap)
            else -> enhanceDocument(bitmap)
        }
    }

    fun detectEdges(bitmap: Bitmap): Bitmap {
        return enhanceDocument(bitmap)
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun applyFilter(bitmap: Bitmap, filter: String): Bitmap {
        return when (filter) {
            "document" -> enhanceDocument(bitmap)
            else -> bitmap
        }
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun toBlackAndWhite(bitmap: Bitmap): Bitmap {
        val gray = toGrayscale(bitmap)
        val result = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.set(floatArrayOf(
            85f, 85f, 85f, 0f, -128f * 255f,
            85f, 85f, 85f, 0f, -128f * 255f,
            85f, 85f, 85f, 0f, -128f * 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(gray, 0f, 0f, paint)
        gray.recycle()
        return result
    }

    private fun enhanceDocument(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.set(floatArrayOf(
            1.5f, 0f, 0f, 0f, -20f,
            0f, 1.5f, 0f, 0f, -20f,
            0f, 0f, 1.5f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}
