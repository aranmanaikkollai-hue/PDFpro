package com.propdf.editor.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Singleton
class PdfOperationsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ---- MERGE ----------------------------------------------------------
    suspend fun mergePdfs(inputFiles: List<File>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val out = PdfDocument(PdfWriter(outputFile.absolutePath))
            try {
                val merger = PdfMerger(out)
                inputFiles.filter { it.exists() }.forEach { f ->
                    val src = PdfDocument(PdfReader(f.absolutePath))
                    try { merger.merge(src, 1, src.numberOfPages) } finally { src.close() }
                }
            } finally { out.close() }
            outputFile
        }}

    // ---- SPLIT ----------------------------------------------------------
    suspend fun splitPdf(inputFile: File, outputDir: File, ranges: List<IntRange>): Result<List<File>> =
        withContext(Dispatchers.IO) { runCatching {
            val result = mutableListOf<File>()
            val src = PdfDocument(PdfReader(inputFile.absolutePath))
            try {
                ranges.forEachIndexed { i, range ->
                    val stamp = System.currentTimeMillis()
                    val out = File(outputDir, "${inputFile.nameWithoutExtension}_part${i+1}_${stamp}.pdf")
                    val dest = PdfDocument(PdfWriter(out.absolutePath))
                    try {
                        PdfMerger(dest).merge(src,
                            range.first.coerceIn(1, src.numberOfPages),
                            range.last.coerceIn(1, src.numberOfPages))
                    } finally { dest.close() }
                    result.add(out)
                }
            } finally { src.close() }
            result
        }}

    // ---- COMPRESS (improved - multiple passes) ---------------------------
    suspend fun compressPdf(inputFile: File, outputFile: File, quality: Int = 9): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val props = WriterProperties().apply {
                setCompressionLevel(quality.coerceIn(1, 9))
                setFullCompressionMode(true)
                useSmartMode()
            }
            val doc = PdfDocument(
                PdfReader(inputFile.absolutePath),
                PdfWriter(outputFile.absolutePath, props)
            )
            try { } finally { doc.close() }
            outputFile
        }}

    // ---- ENCRYPT --------------------------------------------------------
    suspend fun encryptPdf(inputFile: File, outputFile: File, userPassword: String?, ownerPassword: String,
        allowPrinting: Boolean = true, allowCopying: Boolean = false): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            var perms = EncryptionConstants.ALLOW_SCREENREADERS
            if (allowPrinting) perms = perms or EncryptionConstants.ALLOW_PRINTING
            if (allowCopying) perms = perms or EncryptionConstants.ALLOW_COPY
            val doc = PdfDocument(PdfReader(inputFile.absolutePath),
                PdfWriter(outputFile.absolutePath, WriterProperties().setStandardEncryption(
                    userPassword?.toByteArray(), ownerPassword.toByteArray(),
                    perms, EncryptionConstants.ENCRYPTION_AES_256)))
            try { } finally { doc.close() }
            outputFile
        }}

    // ---- REMOVE PASSWORD ------------------------------------------------
    suspend fun removePdfPassword(inputFile: File, outputFile: File, password: String): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val doc = PdfDocument(
                PdfReader(inputFile.absolutePath, ReaderProperties().setPassword(password.toByteArray())),
                PdfWriter(outputFile.absolutePath))
            try { } finally { doc.close() }
            outputFile
        }}

    // ---- WATERMARK (on top) ---------------------------------------------
    suspend fun addTextWatermark(inputFile: File, outputFile: File, text: String,
        opacity: Float = 0.3f, rotation: Float = 45f): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
            try {
                val font = PdfFontFactory.createFont()
                val gs = PdfExtGState().also { it.fillOpacity = opacity; it.strokeOpacity = opacity }
                val rad = Math.toRadians(rotation.toDouble())
                for (i in 1..doc.numberOfPages) {
                    val page = doc.getPage(i); val ps = page.pageSize
                    val cx = (ps.left + ps.right) / 2f; val cy = (ps.bottom + ps.top) / 2f
                    val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, doc)
                    try {
                        canvas.saveState().setExtGState(gs).beginText()
                            .setFontAndSize(font, 60f)
                            .setFillColor(DeviceRgb(0.5f, 0.5f, 0.5f))
                            .setTextMatrix(cos(rad).toFloat(), sin(rad).toFloat(),
                                -sin(rad).toFloat(), cos(rad).toFloat(), cx, cy)
                            .showText(text).endText().restoreState()
                    } finally { canvas.release() }
                }
            } finally { doc.close() }
            outputFile
        }}

    // ---- DELETE PAGES ---------------------------------------------------
    suspend fun deletePages(inputFile: File, outputFile: File, pagesToDelete: List<Int>): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
            try { pagesToDelete.sortedDescending().forEach { n -> if (n in 1..doc.numberOfPages) doc.removePage(n) } }
            finally { doc.close() }
            outputFile
        }}

    // ---- ROTATE ---------------------------------------------------------
    suspend fun rotatePages(inputFile: File, outputFile: File, pages: Map<Int, Int>): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
            try {
                pages.forEach { (n, deg) ->
                    if (n in 1..doc.numberOfPages) {
                        val p = doc.getPage(n)
                        p.put(PdfName.Rotate, PdfNumber((p.rotation + deg) % 360))
                    }
                }
            } finally { doc.close() }
            outputFile
        }}

    // ---- PAGE NUMBERS (with alignment) ----------------------------------
    suspend fun addPageNumbers(inputFile: File, outputFile: File,
        format: String = "Page %d of %d",
        placement: String = "bottom",
        alignment: String = "center"): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
            try {
                val font = PdfFontFactory.createFont()
                val total = doc.numberOfPages
                val fontSize = 10f
                for (i in 1..total) {
                    val page = doc.getPage(i); val ps = page.pageSize
                    var text = format.replaceFirst("%d", "$i")
                    if (text.contains("%d")) text = text.replaceFirst("%d", "$total")
                    val textW = text.length * fontSize * 0.5f
                    val x = when (alignment) {
                        "left" -> ps.left + 20f
                        "right" -> ps.right - textW - 20f
                        else -> (ps.left + ps.right) / 2f - textW / 2f
                    }
                    val y = if (placement == "top") ps.top - 18f else ps.bottom + 12f
                    val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, doc)
                    try {
                        canvas.beginText().setFontAndSize(font, fontSize)
                            .setTextMatrix(1f, 0f, 0f, 1f, x, y)
                            .showText(text).endText()
                    } finally { canvas.release() }
                }
            } finally { doc.close() }
            outputFile
        }}

    // ---- HEADER / FOOTER (with alignment, Tamil bitmap support) ---------
    suspend fun addHeaderFooter(inputFile: File, outputFile: File,
        headerText: String? = null, footerText: String? = null,
        fontSize: Float = 10f,
        headerAlignment: String = "center",
        footerAlignment: String = "center"): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
            try {
                for (i in 1..doc.numberOfPages) {
                    val page = doc.getPage(i); val ps = page.pageSize
                    val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, doc)
                    try {
                        if (headerText != null) {
                            embedTextAsBitmap(canvas, doc, page, headerText, fontSize,
                                ps, headerAlignment, isHeader = true)
                        }
                        if (footerText != null) {
                            embedTextAsBitmap(canvas, doc, page, footerText, fontSize,
                                ps, footerAlignment, isHeader = false)
                        }
                    } finally { canvas.release() }
                }
            } finally { doc.close() }
            outputFile
        }}

    // Render text as a bitmap (supports Tamil, Hindi, all Unicode) and embed in PDF
    private fun embedTextAsBitmap(canvas: PdfCanvas, doc: PdfDocument, page: com.itextpdf.kernel.pdf.PdfPage,
        text: String, fontSizePt: Float, ps: com.itextpdf.kernel.geom.Rectangle,
        alignment: String, isHeader: Boolean) {
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSizePt * 2.5f
                color = Color.BLACK
                typeface = Typeface.DEFAULT
                isLinearText = true
            }
            val bounds = android.graphics.Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val bW = bounds.width() + 20
            val bH = (fontSizePt * 4).toInt().coerceAtLeast(20)
            val bmp = Bitmap.createBitmap(bW, bH, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply {
                drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                drawText(text, 10f, bH - 6f, paint)
            }
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            bmp.recycle()

            val imgData = ImageDataFactory.create(baos.toByteArray())
            val imgW = bW.toFloat() / 2.5f
            val imgH = bH.toFloat() / 2.5f
            val x = when (alignment) {
                "left" -> ps.left + 10f
                "right" -> ps.right - imgW - 10f
                else -> (ps.left + ps.right) / 2f - imgW / 2f
            }
            val y = if (isHeader) ps.top - imgH - 4f else ps.bottom + 4f
            canvas.saveState()
            canvas.addXObjectAt(com.itextpdf.kernel.pdf.xobject.PdfImageXObject(imgData), x, y)
            canvas.restoreState()
        } catch (_: Exception) {
            try {
                val font = PdfFontFactory.createFont()
                val textW = text.length * fontSizePt * 0.5f
                val x = when (alignment) {
                    "left" -> ps.left + 20f
                    "right" -> ps.right - textW - 20f
                    else -> (ps.left + ps.right) / 2f - textW / 2f
                }
                val y = if (isHeader) ps.top - 18f else ps.bottom + 12f
                canvas.beginText().setFontAndSize(font, fontSizePt)
                    .setTextMatrix(1f, 0f, 0f, 1f, x, y)
                    .showText(text).endText()
            } catch (_: Exception) {}
        }
    }

    // ---- IMAGES TO PDF --------------------------------------------------
    suspend fun imagesToPdf(imageFiles: List<File>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val pdfDoc = PdfDocument(PdfWriter(outputFile.absolutePath))
            val doc = Document(pdfDoc)
            try {
                imageFiles.filter { it.exists() && it.length() > 0 }.forEach { f ->
                    val imgData = ImageDataFactory.create(f.absolutePath)
                    val ps = PageSize(imgData.width.toFloat(), imgData.height.toFloat())
                    pdfDoc.addNewPage(ps)
                    val img = Image(imgData)
                    img.setFixedPosition(pdfDoc.numberOfPages, 0f, 0f)
                    img.setWidth(ps.width); img.setHeight(ps.height)
                    doc.add(img)
                }
            } finally { doc.close() }
            outputFile
        }}

    // ---- INSERT IMAGE ON PAGE -------------------------------------------
    suspend fun insertImageOnPage(
        inputFile: File,
        outputFile: File,
        imageFile: File,
        pageNum: Int,
        xPt: Float = 50f,
        yPt: Float = 400f
    ): Result<File> = withContext(Dispatchers.IO) { runCatching {
        val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
        try {
            val page = doc.getPage(pageNum.coerceIn(1, doc.numberOfPages))
            val ps = page.pageSize
            val imgData = ImageDataFactory.create(imageFile.absolutePath)
            val imgW = imgData.width
            val imgH = imgData.height
            val x = (ps.right - imgW - 30f).coerceAtLeast(10f)
            val y = (ps.top - imgH - 80f).coerceAtLeast(10f)
            val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, doc)
            try {
                val xobj = com.itextpdf.kernel.pdf.xobject.PdfImageXObject(imgData)
                canvas.saveState()
                canvas.addXObjectAt(xobj, x, y)
                canvas.restoreState()
            } finally { canvas.release() }
        } finally { doc.close() }
        outputFile
    }}

    // ---- RESHAPE PAGE SIZE ----------------------------------------------
    suspend fun reshapePageSize(inputFile: File, outputFile: File,
        pageWidthPt: Float, pageHeightPt: Float): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val src = PdfDocument(PdfReader(inputFile.absolutePath))
            val dest = PdfDocument(PdfWriter(outputFile.absolutePath))
            val doc = Document(dest)
            try {
                val targetSize = PageSize(pageWidthPt, pageHeightPt)
                for (i in 1..src.numberOfPages) {
                    val srcPage = src.getPage(i)
                    val srcW = srcPage.pageSize.width
                    val srcH = srcPage.pageSize.height
                    val scale = minOf(pageWidthPt / srcW, pageHeightPt / srcH)
                    val offX = (pageWidthPt - srcW * scale) / 2f
                    val offY = (pageHeightPt - srcH * scale) / 2f
                    val destPage = dest.addNewPage(targetSize)
                    val canvas = PdfCanvas(destPage.newContentStreamAfter(), destPage.resources, dest)
                    try {
                        val xobj = srcPage.copyAsFormXObject(dest)
                        canvas.saveState()
                        canvas.concatMatrix(scale.toDouble(), 0.0, 0.0, scale.toDouble(),
                            offX.toDouble(), offY.toDouble())
                        canvas.addXObjectAt(xobj, 0f, 0f)
                        canvas.restoreState()
                    } finally { canvas.release() }
                }
            } finally { doc.close(); src.close() }
            outputFile
        }}

    // ---- SAVE ANNOTATIONS TO PDF (true PDF embedding) ------------------
    suspend fun saveAnnotationsToPdf(
        inputFile: File,
        outputFile: File,
        annotations: Map<Int, List<AnnotationStroke>>,
        textAnnotations: Map<Int, List<TextAnnotation>> = emptyMap()
    ): Result<File> = withContext(Dispatchers.IO) { runCatching {
        val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
        try {
            val allPages = (annotations.keys + textAnnotations.keys).toSet()
            for (idx in allPages) {
                val pdfPageNum = idx + 1
                if (pdfPageNum > doc.numberOfPages) continue
                val pdfPage = doc.getPage(pdfPageNum)
                val pdfH = pdfPage.pageSize.height
                val canvas = PdfCanvas(pdfPage.newContentStreamAfter(), pdfPage.resources, doc)
                try {
                    // Draw strokes (freehand, highlight, shapes)
                    annotations[idx]?.forEach { stroke ->
                        drawStrokeOnPdf(canvas, stroke, pdfH)
                    }
                    // Draw text annotations as bitmaps
                    textAnnotations[idx]?.forEach { ta ->
                        drawTextAnnotationOnPdf(canvas, ta, pdfH)
                    }
                } finally { canvas.release() }
            }
        } finally { doc.close() }
        outputFile
    }}

    private fun drawStrokeOnPdf(canvas: PdfCanvas, stroke: AnnotationStroke, pdfH: Float) {
        if (stroke.tool == "eraser") return

        val r = android.graphics.Color.red(stroke.color) / 255f
        val g = android.graphics.Color.green(stroke.color) / 255f
        val b = android.graphics.Color.blue(stroke.color) / 255f
        val a = (stroke.alpha / 255f).coerceIn(0f, 1f)

        canvas.saveState()
        canvas.setExtGState(PdfExtGState().also { it.strokeOpacity = a; it.fillOpacity = a })
        canvas.setStrokeColor(DeviceRgb(r, g, b))
        canvas.setLineWidth((stroke.strokeWidth / stroke.scale).coerceAtLeast(0.5f))
        canvas.setLineCapStyle(1)
        canvas.setLineJoinStyle(1)

        // Draw path
        if (stroke.points.size >= 2) {
            val first = stroke.points[0]
            canvas.moveTo((first.x / stroke.scale).toDouble(), (pdfH - first.y / stroke.scale).toDouble())
            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                canvas.lineTo((pt.x / stroke.scale).toDouble(), (pdfH - pt.y / stroke.scale).toDouble())
            }
            canvas.stroke()
        }
        canvas.restoreState()
    }

    private fun drawTextAnnotationOnPdf(canvas: PdfCanvas, ta: TextAnnotation, pdfH: Float) {
        val bmp = renderTextBitmap(ta.text, ta.color, ta.sizePx)
        val pngBytes = bitmapToPng(bmp)
        bmp.recycle()
        val imgData = ImageDataFactory.create(pngBytes)
        val bW = imgData.width.toFloat()
        val bH = imgData.height.toFloat()
        val pdfX = ta.x / ta.scale
        val pdfY = pdfH - (ta.y / ta.scale) - (bH / ta.scale)

        canvas.saveState()
        canvas.addXObjectWithTransformationMatrix(
            com.itextpdf.kernel.pdf.xobject.PdfImageXObject(imgData),
            bW / ta.scale, 0f, 0f, bH / ta.scale, pdfX, pdfY)
        canvas.restoreState()
    }

    // ---- ADD TEXT TO PDF PAGE -------------------------------------------
    suspend fun addTextToPage(
        inputFile: File,
        outputFile: File,
        pageNum: Int,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float = 12f,
        color: Int = Color.BLACK
    ): Result<File> = withContext(Dispatchers.IO) { runCatching {
        val doc = PdfDocument(PdfReader(inputFile.absolutePath), PdfWriter(outputFile.absolutePath))
        try {
            val page = doc.getPage(pageNum.coerceIn(1, doc.numberOfPages))
            val pdfH = page.pageSize.height
            val canvas = PdfCanvas(page.newContentStreamAfter(), page.resources, doc)
            try {
                val font = PdfFontFactory.createFont()
                val r = android.graphics.Color.red(color) / 255f
                val g = android.graphics.Color.green(color) / 255f
                val b = android.graphics.Color.blue(color) / 255f
                canvas.beginText()
                    .setFontAndSize(font, fontSize)
                    .setFillColor(DeviceRgb(r, g, b))
                    .setTextMatrix(1f, 0f, 0f, 1f, x, pdfH - y)
                    .showText(text)
                    .endText()
            } finally { canvas.release() }
        } finally { doc.close() }
        outputFile
    }}

    // ---- HELPERS --------------------------------------------------------

    private fun renderTextBitmap(text: String, color: Int, sizePx: Float): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizePx.coerceAtLeast(12f)
            typeface = Typeface.DEFAULT
            isLinearText = true
            isSubpixelText = true
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val w = (bounds.width() + sizePx).toInt().coerceAtLeast(4)
        val h = (bounds.height() + sizePx * 0.5f).toInt().coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            drawText(text, sizePx * 0.2f, bounds.height().toFloat() + sizePx * 0.1f, paint)
        }
        return bmp
    }

    private fun bitmapToPng(bmp: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

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

    // ---- DATA CLASSES ---------------------------------------------------

    data class AnnotationStroke(
        val points: List<PointF>,
        val color: Int,
        val strokeWidth: Float,
        val alpha: Int = 255,
        val scale: Float = 1f,
        val tool: String = "freehand"
    )

    data class TextAnnotation(
        val x: Float,
        val y: Float,
        val text: String,
        val color: Int,
        val sizePx: Float,
        val scale: Float = 1f
    )
}
