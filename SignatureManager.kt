package com.propdf.editor.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages PDF digital/visual signatures.
 * Uses Android PdfRenderer to read pages and PdfDocument to write output.
 * No iText dependency required.
 */
@Singleton
class SignatureManager @Inject constructor(private val context: Context) {

    /**
     * Stamp a signature bitmap onto a specific page of a PDF.
     *
     * @param inputFile    Source PDF file
     * @param outputFile   Destination PDF file
     * @param signatureBitmap  Bitmap of the drawn/typed signature
     * @param pageNumber   1-based page number to stamp (default: last page)
     * @param xFraction    Horizontal position as fraction of page width (0.0-1.0)
     * @param yFraction    Vertical position as fraction of page height (0.0-1.0)
     */
    suspend fun stampSignature(
        inputFile: File,
        outputFile: File,
        signatureBitmap: Bitmap,
        pageNumber: Int = -1,
        xFraction: Float = 0.6f,
        yFraction: Float = 0.1f
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val pdfDoc = PdfDocument()

            val targetPage = if (pageNumber < 1) renderer.pageCount else pageNumber

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 2
                val w = page.width * scale
                val h = page.height * scale

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Stamp on the target page
                if (i == targetPage - 1) {
                    val sigMaxW = (w * 0.3f).toInt()
                    val sigAspect = signatureBitmap.height.toFloat() / signatureBitmap.width
                    val sigW = sigMaxW
                    val sigH = (sigMaxW * sigAspect).toInt()
                    val scaledSig = Bitmap.createScaledBitmap(signatureBitmap, sigW, sigH, true)

                    val x = (w * xFraction).toInt().coerceIn(0, w - sigW)
                    val y = (h * (1f - yFraction)).toInt().coerceIn(sigH, h) - sigH

                    val sigCanvas = Canvas(bmp)
                    sigCanvas.drawBitmap(scaledSig, x.toFloat(), y.toFloat(), null)
                    scaledSig.recycle()
                }

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                val docPage = pdfDoc.startPage(pageInfo)
                docPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdfDoc.finishPage(docPage)
                bmp.recycle()
            }

            renderer.close()
            fd.close()

            FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            outputFile
        }
    }

    /**
     * Draw a text-based signature (typed name) onto a page.
     */
    suspend fun stampTextSignature(
        inputFile: File,
        outputFile: File,
        signerName: String,
        pageNumber: Int = -1
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val pdfDoc = PdfDocument()

            val targetPage = if (pageNumber < 1) renderer.pageCount else pageNumber

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 2
                val w = page.width * scale
                val h = page.height * scale

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                if (i == targetPage - 1) {
                    val sigCanvas = Canvas(bmp)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1A237E")
                        textSize = w * 0.04f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    }
                    val x = w * 0.55f
                    val y = h * 0.88f
                    sigCanvas.drawText(signerName, x, y, paint)

                    // Underline
                    val linePaint = Paint().apply {
                        color = Color.parseColor("#1A237E")
                        strokeWidth = 2f
                    }
                    val textW = paint.measureText(signerName)
                    sigCanvas.drawLine(x, y + 4f, x + textW, y + 4f, linePaint)
                }

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                val docPage = pdfDoc.startPage(pageInfo)
                docPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdfDoc.finishPage(docPage)
                bmp.recycle()
            }

            renderer.close()
            fd.close()

            FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            outputFile
        }
    }
}
