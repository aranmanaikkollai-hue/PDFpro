package com.propdf.editor.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class FileHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        fun uriToFile(context: Context, uri: Uri): File? {
            return try {
                val contentResolver = context.contentResolver
                val fileName = getFileName(context, uri) ?: "temp_file"
                val tempFile = File(context.cacheDir, fileName)

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun getFileName(context: Context, uri: Uri): String? {
            var result: String? = null
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) result = cursor.getString(index)
                    }
                }
            }
            if (result == null) {
                result = uri.path
                val cut = result?.lastIndexOf('/')
                if (cut != null && cut != -1) {
                    result = result?.substring(cut + 1)
                }
            }
            return result
        }

        fun isPdf(file: File): Boolean {
            return file.extension.equals("pdf", ignoreCase = true) ||
                    file.name.endsWith(".pdf", ignoreCase = true)
        }
    }

    fun uriToFile(uri: Uri): File? = uriToFile(context, uri)

    fun getFileName(uri: Uri): String? = getFileName(context, uri)
}