package com.propdf.editor.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for digital signature operations.
 * Handles signature creation, storage, and PDF embedding.
 */
@Singleton
class SignatureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("propdf_signatures", Context.MODE_PRIVATE)

    companion object {
        private const val SIGNATURE_PREFIX = "signature_"
        private const val SIGNATURE_COUNT_KEY = "signature_count"
    }

    // -------------------------------------------------------
    // SIGNATURE STORAGE
    // -------------------------------------------------------

    fun saveSignature(name: String, bitmap: Bitmap): Boolean {
        return try {
            val file = File(context.filesDir, "${SIGNATURE_PREFIX}${name}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val count = prefs.getInt(SIGNATURE_COUNT_KEY, 0)
            prefs.edit().putInt(SIGNATURE_COUNT_KEY, count + 1).apply()
            true
        } catch (_: Exception) { false }
    }

    fun getSignature(name: String): Bitmap? {
        return try {
            val file = File(context.filesDir, "${SIGNATURE_PREFIX}${name}.png")
            if (!file.exists()) return null
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) { null }
    }

    fun getAllSignatureNames(): List<String> {
        return context.filesDir.listFiles { f ->
            f.name.startsWith(SIGNATURE_PREFIX) && f.name.endsWith(".png")
        }?.map { it.name.removePrefix(SIGNATURE_PREFIX).removeSuffix(".png") } ?: emptyList()
    }

    fun deleteSignature(name: String): Boolean {
        return try {
            val file = File(context.filesDir, "${SIGNATURE_PREFIX}${name}.png")
            file.delete()
        } catch (_: Exception) { false }
    }

    // -------------------------------------------------------
    // APPLY SIGNATURE TO PDF
    // -------------------------------------------------------

    suspend fun applySignatureToPdf(
        inputFile: File,
        outputFile: File,
        signatureName: String,
        pageNumber: Int = 1,
        xPercent: Float = 0.7f,
        yPercent: Float = 0.1f,
        widthPercent: Float = 0.2f
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val signatureBmp = getSignature(signatureName)
                ?: throw IllegalStateException("Signature not found: $signatureName")

            val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
            try {
                val page = doc.getPage(pageNumber.coerceIn(1, doc.numberOfPages))
                val pageSize = page.pageSize
                val pageW = pageSize.width
                val pageH = pageSize.height

                // Calculate position
                val sigW = pageW * widthPercent
                val aspectRatio = signatureBmp.height.toFloat() / signatureBmp.width.toFloat()
                val sigH = sigW * aspectRatio
                val x = pageW * xPercent
                val y = pageH - (pageH * yPercent) - sigH

                // Convert bitmap to PDF image
                val baos = ByteArrayOutputStream()
                signatureBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val imgData = ImageDataFactory.create(baos.toByteArray())

                val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, doc)
                try {
                    canvas.saveState()
                    // Add a subtle border
                    canvas.setStrokeColor(DeviceRgb(0.3f, 0.3f, 0.3f))
                    canvas.setLineWidth(0.5f)
                    canvas.rectangle(x - 2, y - 2, sigW + 4, sigH + 4)
                    canvas.stroke()
                    // Draw signature
                    canvas.addXObjectWithTransformationMatrix(
                        com.itextpdf.kernel.pdf.xobject.PdfImageXObject(imgData),
                        sigW, 0f, 0f, sigH, x, y
                    )
                    canvas.restoreState()
                } finally {
                    canvas.release()
                }
            } finally {
                doc.close()
            }
            outputFile
        }
    }

    // -------------------------------------------------------
    // CREATE SIGNATURE WITH TEXT
    // -------------------------------------------------------

    fun createTextSignature(text: String, width: Int = 400, height: Int = 150): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = height * 0.4f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }

        // Draw underline
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        val centerX = width / 2f
        val centerY = height / 2f + paint.textSize / 3f
        canvas.drawText(text, centerX, centerY, paint)
        canvas.drawLine(
            centerX - paint.measureText(text) / 2f,
            centerY + paint.textSize * 0.2f,
            centerX + paint.measureText(text) / 2f,
            centerY + paint.textSize * 0.2f,
            linePaint
        )

        return bmp
    }

    // -------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------

    private fun PdfCanvas.addXObjectWithTransformationMatrix(
        xobj: com.itextpdf.kernel.pdf.xobject.PdfXObject,
        a: Float, b: Float, c: Float, d: Float, e: Float, f: Float
    ): PdfCanvas {
        saveState()
        concatMatrix(a.toDouble(), b.toDouble(), c.toDouble(), d.toDouble(), e.toDouble(), f.toDouble())
        addXObjectAt(xobj, 0f, 0f)
        restoreState()
        return this
    }
}
