package com.propdf.editor.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.propdf.editor.data.local.RecentFileEntity
import com.propdf.editor.data.repository.RecentFilesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for MainActivity.
 * Handles all file list operations, sorting, filtering, and search.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: RecentFilesRepository
) : ViewModel() {

    // -------------------------------------------------------
    // UI STATE
    // -------------------------------------------------------

    data class UiState(
        val files: List<RecentFileEntity> = emptyList(),
        val filteredFiles: List<RecentFileEntity> = emptyList(),
        val currentTab: String = "recent",
        val viewMode: String = "list",
        val sortMode: String = "date",
        val sortAsc: Boolean = false,
        val isDarkMode: Boolean = true,
        val isLoading: Boolean = false,
        val searchQuery: String = "",
        val errorMessage: String? = null,
        val categoryDetail: String = "",
        val expandedCategories: Set<String> = emptySet()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // -------------------------------------------------------
    // INIT
    // -------------------------------------------------------

    init {
        loadFiles()
        _uiState.value = _uiState.value.copy(isDarkMode = repository.isDarkMode())
    }

    // -------------------------------------------------------
    // FILE LOADING
    // -------------------------------------------------------

    private fun loadFiles() {
        viewModelScope.launch {
            repository.getAllRecentFiles().collectLatest { files ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    files = files,
                    filteredFiles = applyFilters(files, current)
                )
            }
        }
    }

    // -------------------------------------------------------
    // TAB SWITCHING
    // -------------------------------------------------------

    fun switchTab(tab: String) {
        val current = _uiState.value
        val baseFiles = when (tab) {
            "starred" -> current.files.filter { it.isFavourite }
            "categories" -> current.files
            "bookmarks" -> current.files.filter { entity ->
                val key = entity.uri.hashCode().toString()
                // Check bookmarks via repository
                true // Simplified - actual check in repository
            }
            "cat_detail" -> current.files.filter { it.category == current.categoryDetail }
            else -> current.files.filter { it.lastOpenedAt > 0L }
        }
        _uiState.value = current.copy(
            currentTab = tab,
            filteredFiles = applySort(baseFiles, current.sortMode, current.sortAsc)
        )
    }

    // -------------------------------------------------------
    // SORTING
    // -------------------------------------------------------

    fun setSortMode(mode: String, ascending: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            sortMode = mode,
            sortAsc = ascending,
            filteredFiles = applySort(current.filteredFiles, mode, ascending)
        )
    }

    fun toggleSortDirection() {
        val current = _uiState.value
        setSortMode(current.sortMode, !current.sortAsc)
    }

    private fun applySort(
        files: List<RecentFileEntity>,
        mode: String,
        asc: Boolean
    ): List<RecentFileEntity> {
        return when (mode) {
            "name" -> if (asc)
                files.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
            else
                files.sortedByDescending { it.displayName.lowercase(Locale.getDefault()) }
            "size" -> if (asc)
                files.sortedBy { it.fileSizeBytes }
            else
                files.sortedByDescending { it.fileSizeBytes }
            else -> if (asc)
                files.sortedBy { it.lastOpenedAt }
            else
                files.sortedByDescending { it.lastOpenedAt }
        }
    }

    private fun applyFilters(
        files: List<RecentFileEntity>,
        state: UiState
    ): List<RecentFileEntity> {
        val base = when (state.currentTab) {
            "starred" -> files.filter { it.isFavourite }
            "cat_detail" -> files.filter { it.category == state.categoryDetail }
            else -> files.filter { it.lastOpenedAt > 0L }
        }
        return applySort(base, state.sortMode, state.sortAsc)
    }

    // -------------------------------------------------------
    // VIEW MODE
    // -------------------------------------------------------

    fun setViewMode(mode: String) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun cycleViewMode() {
        val current = _uiState.value.viewMode
        val next = when (current) {
            "list" -> "grid"
            "grid" -> "tile"
            else -> "list"
        }
        setViewMode(next)
    }

    // -------------------------------------------------------
    // SEARCH
    // -------------------------------------------------------

    fun search(query: String) {
        val current = _uiState.value
        val results = if (query.isBlank()) {
            applyFilters(current.files, current)
        } else {
            current.files.filter {
                it.displayName.lowercase(Locale.getDefault()).contains(query.lowercase())
            }
        }
        _uiState.value = current.copy(
            searchQuery = query,
            filteredFiles = results
        )
    }

    // -------------------------------------------------------
    // FILE OPERATIONS
    // -------------------------------------------------------

    fun addFile(uri: Uri, name: String, size: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertFile(uri, name, size)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to add file: ${e.message}")
            }
        }
    }

    fun toggleFavourite(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = repository.getFileByUri(uri)
            entity?.let {
                repository.setFavourite(uri, !it.isFavourite)
            }
        }
    }

    fun deleteFile(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFile(uri)
        }
    }

    fun renameFile(uri: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = repository.getFileByUri(uri) ?: return@launch
            val finalName = if (newName.endsWith(".pdf")) newName else "$newName.pdf"
            repository.insertFile(
                Uri.parse(entity.uri),
                finalName,
                entity.fileSizeBytes,
                entity.pageCount
            )
        }
    }

    fun moveToCategory(uri: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setCategory(uri, category)
        }
    }

    // -------------------------------------------------------
    // CATEGORIES
    // -------------------------------------------------------

    fun createCategory(name: String) {
        repository.addCategory(name)
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeCategory(name)
            // Move files to uncategorized
            val files = repository.getFilesByCategory(name).collectLatest { list ->
                list.forEach { repository.setCategory(it.uri, "") }
            }
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameCategory(oldName, newName)
        }
    }

    fun toggleCategoryExpanded(category: String) {
        val current = _uiState.value
        val expanded = current.expandedCategories.toMutableSet()
        if (expanded.contains(category)) {
            expanded.remove(category)
        } else {
            expanded.add(category)
        }
        _uiState.value = current.copy(expandedCategories = expanded)
    }

    // -------------------------------------------------------
    // THEME
    // -------------------------------------------------------

    fun toggleTheme() {
        val current = _uiState.value.isDarkMode
        repository.setDarkMode(!current)
        _uiState.value = _uiState.value.copy(isDarkMode = !current)
    }

    // -------------------------------------------------------
    // CLEAR / RESET
    // -------------------------------------------------------

    fun clearRecentFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearRecentOnly()
        }
    }

    fun clearAllFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllFiles()
        }
    }

    fun scanLocalFiles(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val localFiles = repository.scanLocalPdfs()
                localFiles.forEach { file ->
                    repository.insertFile(
                        Uri.parse(file.uri),
                        file.displayName,
                        file.fileSizeBytes,
                        file.pageCount
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Scan failed: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
