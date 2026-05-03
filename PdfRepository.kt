package com.propdf.editor.data.repository

import android.content.Context
import android.net.Uri
import com.propdf.editor.utils.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for PDF file operations.
 * Centralizes PDF loading, validation, and caching.
 */
@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileHelper: FileHelper
) {

    // -------------------------------------------------------
    // PDF LOADING
    // -------------------------------------------------------

    suspend fun loadPdfFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (uri.toString().isBlank()) {
                throw IllegalArgumentException("Empty URI")
            }

            val file = fileHelper.uriToFile(context, uri)
                ?: throw IllegalStateException("Cannot read file from URI")

            if (!file.exists() || file.length() == 0L) {
                throw IllegalStateException("File is empty or does not exist")
            }

            if (!fileHelper.isPdf(file)) {
                throw IllegalStateException("File is not a valid PDF")
            }

            file
        }
    }

    // -------------------------------------------------------
    // PDF INFO
    // -------------------------------------------------------

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        try {
            val pfd = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (_: Exception) { 0 }
    }

    suspend fun getPdfMetadata(file: File): PdfMetadata = withContext(Dispatchers.IO) {
        try {
            val pfd = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val width = page.width
            val height = page.height
            page.close()
            renderer.close()
            pfd.close()

            PdfMetadata(
                pageCount = renderer.pageCount,
                pageWidth = width,
                pageHeight = height,
                fileSize = file.length(),
                fileName = file.name
            )
        } catch (e: Exception) {
            PdfMetadata(
                pageCount = 0,
                pageWidth = 0,
                pageHeight = 0,
                fileSize = file.length(),
                fileName = file.name,
                error = e.message
            )
        }
    }

    // -------------------------------------------------------
    // CACHE MANAGEMENT
    // -------------------------------------------------------

    fun getCacheFile(name: String): File {
        return File(context.cacheDir, name)
    }

    fun clearOldCache(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        context.cacheDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                file.delete()
            }
        }
    }

    // -------------------------------------------------------
    // DATA CLASS
    // -------------------------------------------------------

    data class PdfMetadata(
        val pageCount: Int,
        val pageWidth: Int,
        val pageHeight: Int,
        val fileSize: Long,
        val fileName: String,
        val error: String? = null
    )
}
