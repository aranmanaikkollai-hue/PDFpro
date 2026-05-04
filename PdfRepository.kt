package com.propdf.editor.data.repository

import android.content.Context
import android.net.Uri
import com.propdf.editor.utils.FileHelper
import java.io.File
import javax.inject.Inject

class PdfRepository @Inject constructor(
    private val context: Context,
    private val fileHelper: FileHelper
) {

    fun getPdfFromUri(uri: Uri): File? {
        return fileHelper.uriToFile(uri)
    }

    fun isPdfFile(uri: Uri): Boolean {
        val file = getPdfFromUri(uri) ?: return false
        return file.extension.equals("pdf", ignoreCase = true) ||
               file.name.endsWith(".pdf", ignoreCase = true)
    }

    fun getPdfFilesFromDirectory(directory: File): List<File> {
        return directory.listFiles { file ->
            file.isFile && file.extension.equals("pdf", ignoreCase = true)
        }?.toList() ?: emptyList()
    }
}
