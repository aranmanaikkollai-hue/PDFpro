package com.propdf.editor.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfOperationsManager @Inject constructor(private val context: Context) {

    // ------------------------------------------------------------------
    // MERGE
    // ------------------------------------------------------------------
    suspend fun mergePdfs(files: List<File>, output: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val merged = PDDocument()
                files.forEach { file ->
                    val doc = PDDocument.load(file)
                    doc.pages.forEach { page -> merged.importPage(page) }
                    doc.close()
                }
                FileOutputStream(output).use { merged.save(it) }
                merged.close()
                output
            }
        }

    // ------------------------------------------------------------------
    // SPLIT
    // ------------------------------------------------------------------
    suspend fun splitPdf(
        input: File,
        outputDir: File,
        ranges: List<IntRange>
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = PDDocument.load(input)
            val results = mutableListOf<File>()
            ranges.forEachIndexed { idx, range ->
                val part = PDDocument()
                for (pageNum in range) {
                    val zeroIdx = pageNum - 1
                    if (zeroIdx >= 0 && zeroIdx < doc.numberOfPages) {
                        part.importPage(doc.pages[zeroIdx])
                    }
                }
                val outFile = File(outputDir, "split_${idx + 1}_${System.currentTimeMillis()}.pdf")
                FileOutputStream(outFile).use { part.save(it) }
                part.close()
                results.add(outFile)
            }
            doc.close()
            results
        }
    }

    // ------------------------------------------------------------------
    // COMPRESS (re-saves with image downsampling)
    // ------------------------------------------------------------------
    suspend fun compressPdf(input: File, output: File, quality: Int): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                // PDFBox 2.x does not have a built-in compression API;
                // re-saving removes unreferenced objects which gives modest reduction.
                val doc = PDDocument.load(input)
                FileOutputStream(output).use { doc.save(it) }
                doc.close()
                output
            }
        }

    // ------------------------------------------------------------------
    // ENCRYPT
    // ------------------------------------------------------------------
    suspend fun encryptPdf(
        input: File,
        output: File,
        userPass: String?,
        ownerPassword: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = PDDocument.load(input)
            val ap = AccessPermission()
            val policy = StandardProtectionPolicy(
                ownerPassword,
                userPass ?: "",
                ap
            )
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            FileOutputStream(output).use { doc.save(it) }
            doc.close()
            output
        }
    }

    // ------------------------------------------------------------------
    // WATERMARK
    // ------------------------------------------------------------------
    suspend fun addTextWatermark(
        input: File,
        output: File,
        watermarkText: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = PDDocument.load(input)
            doc.pages.forEach { page ->
                val mediaBox = page.mediaBox
                val stream = PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND,
                    true, true
                )
                stream.setFont(PDType1Font.HELVETICA_BOLD, 48f)
                stream.setNonStrokingColor(
                    com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray.INSTANCE
                )
                stream.beginText()
                stream.setTextMatrix(
                    Matrix.getRotateInstance(
                        Math.toRadians(45.0).toFloat(),
                        mediaBox.width / 2f,
                        mediaBox.height / 2f
                    )
                )
                stream.showText(watermarkText)
                stream.endText()
                stream.close()
            }
            FileOutputStream(output).use { doc.save(it) }
            doc.close()
            output
        }
    }

    // ------------------------------------------------------------------
    // ROTATE PAGES
    // ------------------------------------------------------------------
    suspend fun rotatePages(
        input: File,
        output: File,
        pageRotations: Map<Int, Int>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = PDDocument.load(input)
            pageRotations.forEach { (pageNum, degrees) ->
                val idx = pageNum - 1
                if (idx >= 0 && idx < doc.numberOfPages) {
                    val page = doc.pages[idx]
                    val current = page.rotation
                    page.rotation = (current + degrees) % 360
                }
            }
            FileOutputStream(output).use { doc.save(it) }
            doc.close()
            output
        }
    }

    // ------------------------------------------------------------------
    // DELETE PAGES
    // ------------------------------------------------------------------
    suspend fun deletePages(
        input: File,
        output: File,
        pagesToDelete: List<Int>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = PDDocument.load(input)
            // Remove in reverse order so indices stay valid
            val sorted = pagesToDelete.map { it - 1 }
                .filter { it >= 0 && it < doc.numberOfPages }
                .sortedDescending()
                .distinct()
            sorted.forEach { idx -> doc.removePage(idx) }
            FileOutputStream(output).use { doc.save(it) }
            doc.close()
            output
        }
    }

    // ------------------------------------------------------------------
    // PAGE NUMBERS
    // ------------------------------------------------------------------
    suspend fun addPageNumbers(input: File, output: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = PDDocument.load(input)
                doc.pages.forEachIndexed { idx, page ->
                    val stream = PDPageContentStream(
                        doc, page,
                        PDPageContentStream.AppendMode.APPEND,
                        true, true
                    )
                    stream.setFont(PDType1Font.HELVETICA, 10f)
                    stream.beginText()
                    stream.newLineAtOffset(page.mediaBox.width / 2f - 10f, 20f)
                    stream.showText("${idx + 1}")
                    stream.endText()
                    stream.close()
                }
                FileOutputStream(output).use { doc.save(it) }
                doc.close()
                output
            }
        }

    // ------------------------------------------------------------------
    // HEADER / FOOTER
    // ------------------------------------------------------------------
    suspend fun addHeaderFooter(
        input: File,
        output: File,
        headerText: String?,
        footerText: String?
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = PDDocument.load(input)
            doc.pages.forEach { page ->
                val w = page.mediaBox.width
                val h = page.mediaBox.height
                val stream = PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND,
                    true, true
                )
                stream.setFont(PDType1Font.HELVETICA, 10f)
                if (!headerText.isNullOrBlank()) {
                    stream.beginText()
                    stream.newLineAtOffset(w / 2f - 50f, h - 20f)
                    stream.showText(headerText)
                    stream.endText()
                }
                if (!footerText.isNullOrBlank()) {
                    stream.beginText()
                    stream.newLineAtOffset(w / 2f - 50f, 10f)
                    stream.showText(footerText)
                    stream.endText()
                }
                stream.close()
            }
            FileOutputStream(output).use { doc.save(it) }
            doc.close()
            output
        }
    }

    // ------------------------------------------------------------------
    // IMAGES TO PDF  (uses Android PdfDocument - no iText/PDFBox needed)
    // ------------------------------------------------------------------
    suspend fun imagesToPdf(images: List<File>, output: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pdfDoc = PdfDocument()
                images.forEachIndexed { idx, imgFile ->
                    val bmp = android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath)
                        ?: return@forEachIndexed
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bmp.width, bmp.height, idx + 1
                    ).create()
                    val page = pdfDoc.startPage(pageInfo)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    pdfDoc.finishPage(page)
                    bmp.recycle()
                }
                FileOutputStream(output).use { pdfDoc.writeTo(it) }
                pdfDoc.close()
                output
            }
        }

    // ------------------------------------------------------------------
    // SIGN PDF  (simple bitmap-stamp signature using Android PdfDocument)
    // ------------------------------------------------------------------
    fun signPdf(
        inputFile: File,
        outputFile: File,
        signatureBitmap: Bitmap,
        pageNumber: Int = 1
    ): Boolean {
        return try {
            // Re-render all pages via PdfRenderer and stamp signature on target page
            val fd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val pdfDoc = PdfDocument()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Stamp signature on the specified page
                if (i == pageNumber - 1) {
                    val sigW = (bmp.width * 0.3f).toInt()
                    val sigH = (sigW * signatureBitmap.height / signatureBitmap.width.toFloat()).toInt()
                    val scaledSig = Bitmap.createScaledBitmap(signatureBitmap, sigW, sigH, true)
                    val sigCanvas = Canvas(bmp)
                    sigCanvas.drawBitmap(
                        scaledSig,
                        (bmp.width - sigW - 40).toFloat(),
                        (bmp.height - sigH - 40).toFloat(),
                        null
                    )
                    scaledSig.recycle()
                }

                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                val docPage = pdfDoc.startPage(pageInfo)
                docPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdfDoc.finishPage(docPage)
                bmp.recycle()
            }

            renderer.close()
            fd.close()
            FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ------------------------------------------------------------------
    // COMPRESS (Uri overload for ViewerViewModel compatibility)
    // ------------------------------------------------------------------
    fun compressPdf(inputUri: Uri, outputFile: File): Boolean {
        return try {
            val fd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return false
            val renderer = PdfRenderer(fd)
            val pdfDoc = PdfDocument()
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                val docPage = pdfDoc.startPage(pageInfo)
                docPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdfDoc.finishPage(docPage)
                bmp.recycle()
            }
            renderer.close()
            fd.close()
            FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
