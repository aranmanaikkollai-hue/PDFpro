package com.propdf.editor.data.repository

import android.content.Context
import com.propdf.editor.data.local.RecentFilesDao
import com.propdf.editor.data.local.RecentFileEntity
import java.io.File

class RecentFilesRepository(
    private val dao: RecentFilesDao,
    private val context: Context
) {
    fun getRecentFiles(): List<File> {
        return dao.getRecentFiles().map { File(it.path) }.filter { it.exists() }
    }

    fun addRecentFile(file: File) {
        dao.insert(RecentFileEntity(
            path = file.absolutePath,
            name = file.name,
            size = file.length(),
            lastOpened = System.currentTimeMillis()
        ))
    }
}
