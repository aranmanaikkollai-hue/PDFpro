package com.propdf.editor.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.FileOutputStream

class PdfOperationsManager(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    fun mergePdfs(files: List<File>, output: File): Result<File> = runCatching {
        PDDocument().use { mergedDoc ->
            files.forEach { file ->
                PDDocument.load(file).use { doc ->
                    doc.pages.forEach { page ->
                        mergedDoc.addPage(page)
                    }
                }
            }
            mergedDoc.save(output)
        }
        output
    }

    fun splitPdf(file: File, outputDir: File, ranges: List<IntRange>): Result<List<File>> = runCatching {
        val outputFiles = mutableListOf<File>()
        PDDocument.load(file).use { doc ->
            ranges.forEachIndexed { index, range ->
                val newDoc = PDDocument()
                range.forEach { pageNum ->
                    if (pageNum in 1..doc.numberOfPages) {
                        newDoc.addPage(doc.getPage(pageNum - 1))
                    }
                }
                val outFile = File(outputDir, "split_${index + 1}_${System.currentTimeMillis()}.pdf")
                newDoc.save(outFile)
                newDoc.close()
                outputFiles.add(outFile)
            }
        }
        outputFiles
    }

    fun compressPdf(file: File, output: File, quality: Int): Result<File> = runCatching {
        PDDocument.load(file).use { doc ->
            val renderer = PDFRenderer(doc)
            val compressedDoc = PDDocument()
            
            for (i in 0 until doc.numberOfPages) {
                val bitmap = renderer.renderImageWithDPI(i, 150f)
                val page = PDPage(PDRectangle.A4)
                compressedDoc.addPage(page)
                
                PDPageContentStream(compressedDoc, page).use { cs ->
                    val img = JPEGFactory.createFromImage(compressedDoc, bitmap, quality / 10f)
                    cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                }
                bitmap.recycle()
            }
            compressedDoc.save(output)
            compressedDoc.close()
        }
        output
    }

    fun encryptPdf(file: File, output: File, userPassword: String?, ownerPassword: String): Result<File> = runCatching {
        PDDocument.load(file).use { doc ->
            val accessPermission = AccessPermission()
            val protectionPolicy = StandardProtectionPolicy(ownerPassword, userPassword ?: ownerPassword, accessPermission)
            protectionPolicy.encryptionKeyLength = 128
            doc.protect(protectionPolicy)
            doc.save(output)
        }
        output
    }

    fun addTextWatermark(file: File, output: File, text: String): Result<File> = runCatching {
        PDDocument.load(file).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 50f)
                    cs.setNonStrokingColor(200, 200, 200)
                    cs.beginText()
                    cs.newLineAtOffset(100f, 100f)
                    cs.showText(text)
                    cs.endText()
                }
            }
            doc.save(output)
        }
        output
    }

    fun rotatePages(file: File, output: File, rotations: Map<Int, Int>): Result<File> = runCatching {
        PDDocument.load(file).use { doc ->
            rotations.forEach { (pageNum, degrees) ->
                if (pageNum in 1..doc.numberOfPages) {
                    val page = doc.getPage(pageNum - 1)
                    page.rotation = (page.rotation + degrees) % 360
                }
            }
            doc.save(output)
        }
        output
    }

    fun deletePages(file: File, output: File, pagesToDelete: List<Int>): Result<File> = runCatching {
        PDDocument.load(file).use { doc ->
            val sortedPages = pagesToDelete.sortedDescending()
            sortedPages.forEach { pageNum ->
                if (pageNum in 1..doc.numberOfPages) {
                    doc.removePage(pageNum - 1)
                }
            }
            doc.save(output)
        }
        output
    }

    fun addPageNumbers(file: File, output: File): Result<File> = runCatching {
        PDDocument.load(file).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.setNonStrokingColor(0, 0, 0)
                    cs.beginText()
                    cs.newLineAtOffset(page.mediaBox.width / 2, 30f)
                    cs.showText("${i + 1}")
                    cs.endText()
                }
            }
            doc.save(output)
        }
        output
    }

    fun addHeaderFooter(file: File, output: File, header: String?, footer: String?): Result<File> = runCatching {
        PDDocument.load(file).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setFont(PDType1Font.HELVETICA, 10f)
                    cs.setNonStrokingColor(100, 100, 100)
                    
                    header?.let {
                        cs.beginText()
                        cs.newLineAtOffset(50f, page.mediaBox.height - 30f)
                        cs.showText(it)
                        cs.endText()
                    }
                    
                    footer?.let {
                        cs.beginText()
                        cs.newLineAtOffset(50f, 20f)
                        cs.showText(it)
                        cs.endText()
                    }
                }
            }
            doc.save(output)
        }
        output
    }

    fun imagesToPdf(images: List<File>, output: File): Result<File> = runCatching {
        val doc = PDDocument()
        images.forEach { imageFile ->
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            
            PDPageContentStream(doc, page).use { cs ->
                val img = JPEGFactory.createFromImage(doc, bitmap, 0.9f)
                val scale = minOf(page.mediaBox.width / bitmap.width, page.mediaBox.height / bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val x = (page.mediaBox.width - width) / 2
                val y = (page.mediaBox.height - height) / 2
                cs.drawImage(img, x, y, width, height)
            }
            bitmap.recycle()
        }
        doc.save(output)
        doc.close()
        output
    }

    fun pdfToImages(file: File, outputDir: File): Result<List<File>> = runCatching {
        val images = mutableListOf<File>()
        PDDocument.load(file).use { doc ->
            val renderer = PDFRenderer(doc)
            for (i in 0 until doc.numberOfPages) {
                val bitmap = renderer.renderImageWithDPI(i, 300f)
                val outFile = File(outputDir, "page_${i + 1}.png")
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                images.add(outFile)
                bitmap.recycle()
            }
        }
        images
    }
}
