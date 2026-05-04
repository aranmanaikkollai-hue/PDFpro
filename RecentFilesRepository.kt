package com.propdf.editor.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.propdf.editor.data.local.RecentFileEntity
import com.propdf.editor.data.local.RecentFilesDao
import com.propdf.editor.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for recent files, starred files, categories, and bookmarks.
 * Centralizes all file metadata operations.
 */
@Singleton
class RecentFilesRepository @Inject constructor(
    private val dao: RecentFilesDao,
    private val context: Context
) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("propdf_prefs", Context.MODE_PRIVATE)

    private val bookmarkPrefs: SharedPreferences
        get() = context.getSharedPreferences("propdf_bookmarks", Context.MODE_PRIVATE)

    // -------------------------------------------------------
    // RECENT FILES
    // -------------------------------------------------------

    fun getAllRecentFiles(): Flow<List<RecentFileEntity>> = dao.getAll()

    fun getFavouriteFiles(): Flow<List<RecentFileEntity>> = dao.getFavourites()

    fun getFilesByCategory(category: String): Flow<List<RecentFileEntity>> =
        dao.getByCategory(category)

    fun searchFiles(query: String): Flow<List<RecentFileEntity>> =
        dao.search("%$query%")

    suspend fun insertFile(uri: Uri, displayName: String, fileSizeBytes: Long, pageCount: Int = 0) {
        withContext(Dispatchers.IO) {
            dao.insert(
                RecentFileEntity(
                    uri = uri.toString(),
                    name = displayName,
                    path = uri.toString(),
                    size = fileSizeBytes,
                    lastOpened = System.currentTimeMillis(),
                    pageCount = pageCount
                )
            )
        }
    }

    suspend fun deleteFile(uri: String) {
        withContext(Dispatchers.IO) { dao.deleteByUri(uri) }
    }

    suspend fun clearAllFiles() {
        withContext(Dispatchers.IO) { dao.deleteAll() }
    }

    suspend fun clearRecentOnly() {
        withContext(Dispatchers.IO) { dao.deleteNonFavourites() }
    }

    suspend fun setFavourite(uri: String, favourite: Boolean) {
        withContext(Dispatchers.IO) { dao.updateFavourite(uri, favourite) }
    }

    suspend fun setCategory(uri: String, category: String) {
        withContext(Dispatchers.IO) { dao.updateCategory(uri, category) }
    }

    suspend fun updatePageCount(uri: String, count: Int) {
        withContext(Dispatchers.IO) { dao.updatePageCount(uri, count) }
    }

    suspend fun getFileByUri(uri: String): RecentFileEntity? {
        return withContext(Dispatchers.IO) { dao.findByUri(uri) }
    }

    suspend fun getFileCount(): Int {
        return withContext(Dispatchers.IO) { dao.count() }
    }

    // -------------------------------------------------------
    // CATEGORIES / VAULT
    // -------------------------------------------------------

    fun getUserCategories(): Set<String> {
        return prefs.getStringSet("user_categories", emptySet()) ?: emptySet()
    }

    fun addCategory(name: String) {
        val set = getUserCategories().toMutableSet()
        set.add(name)
        prefs.edit().putStringSet("user_categories", set).apply()
    }

    fun removeCategory(name: String) {
        val set = getUserCategories().toMutableSet()
        set.removeAll { it == name || it.startsWith("$name/") }
        prefs.edit().putStringSet("user_categories", set).apply()
    }

    fun renameCategory(oldName: String, newName: String) {
        val set = getUserCategories().toMutableSet()
        val updated = set.map {
            when {
                it == oldName -> newName
                it.startsWith("$oldName/") -> newName + it.substring(oldName.length)
                else -> it
            }
        }.toMutableSet()
        prefs.edit().putStringSet("user_categories", updated).apply()
    }

    fun addSubCategory(parent: String, subName: String) {
        addCategory("$parent/$subName")
    }

    // -------------------------------------------------------
    // BOOKMARKS
    // -------------------------------------------------------

    fun getBookmarkedPages(uri: Uri): Set<String> {
        val key = uri.toString().hashCode().toString()
        return bookmarkPrefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    fun addBookmark(uri: Uri, pageIndex: Int, label: String = "") {
        val key = uri.toString().hashCode().toString()
        val set = getBookmarkedPages(uri).toMutableSet()
        set.removeAll { it.startsWith("$pageIndex:") }
        set.add("$pageIndex:$label")
        bookmarkPrefs.edit().putStringSet(key, set).apply()
    }

    fun removeBookmark(uri: Uri, pageIndex: Int) {
        val key = uri.toString().hashCode().toString()
        val set = getBookmarkedPages(uri).toMutableSet()
        set.removeAll { it.startsWith("$pageIndex:") || it == pageIndex.toString() }
        bookmarkPrefs.edit().putStringSet(key, set).apply()
    }

    fun isPageBookmarked(uri: Uri, pageIndex: Int): Boolean {
        return getBookmarkedPages(uri).any { it.startsWith("$pageIndex:") || it == pageIndex.toString() }
    }

    suspend fun getBookmarkedFiles(): List<RecentFileEntity> {
        val allFiles = getAllRecentFiles().first()
        return allFiles.filter { entity ->
            val key = entity.uri.hashCode().toString()
            (bookmarkPrefs.getStringSet(key, emptySet()) ?: emptySet()).isNotEmpty()
        }
    }

    // -------------------------------------------------------
    // LOCAL FILE SCANNING
    // -------------------------------------------------------

    suspend fun scanLocalPdfs(): List<RecentFileEntity> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<RecentFileEntity>()
            val dirs = listOf(
                context.getExternalFilesDir(null),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            )
            dirs.filterNotNull().forEach { dir ->
                dir.walkTopDown().maxDepth(3).filter {
                    it.isFile && it.extension.equals("pdf", ignoreCase = true)
                }.forEach { file ->
                    val uri = Uri.fromFile(file)
                    results.add(
                        RecentFileEntity(
                            uri = uri.toString(),
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            lastOpened = file.lastModified(),
                            pageCount = 0
                        )
                    )
                }
            }
            results
        }
    }

    // -------------------------------------------------------
    // THEME
    // -------------------------------------------------------

    fun isDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean("dark_mode", dark).apply()
    }
}
