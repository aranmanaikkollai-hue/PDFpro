package com.propdf.editor.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * File helper utility for URI to File conversion and PDF validation.
 */
object FileHelper {

    /**
     * Convert a content URI to a temporary file.
     */
    fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val fileName = getFileName(context, uri) ?: "temp.pdf"
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the display name from a content URI.
     */
    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast("/")
        }
        return name
    }

    /**
     * Check if a file is a valid PDF by reading its header.
     */
    fun isPdf(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(5)
                val read = input.read(header)
                read >= 5 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                        header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a URI points to a valid PDF file.
     */
    fun isValidPdfUri(context: Context, uri: Uri): Boolean {
        return try {
            val file = uriToFile(context, uri)
            val valid = file != null && isPdf(file!!)
            file?.delete()
            valid
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Format file size to human-readable string.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes > 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes > 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
