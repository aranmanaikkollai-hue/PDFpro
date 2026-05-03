package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.propdf.editor.data.repository.RecentFilesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ViewModel for ViewerActivity.
 * Handles PDF loading, page rendering, annotations, search, and bookmarks.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: RecentFilesRepository
) : ViewModel() {

    // -------------------------------------------------------
    // UI STATE
    // -------------------------------------------------------

    data class UiState(
        val isLoading: Boolean = false,
        val totalPages: Int = 0,
        val currentPage: Int = 0,
        val errorMessage: String? = null,
        val isDarkMode: Boolean = true,
        val readingMode: String = "normal",
        val isSearchVisible: Boolean = false,
        val searchQuery: String = "",
        val searchResults: List<Int> = emptyList(),
        val searchResultIndex: Int = 0,
        val isAnnotToolbarExpanded: Boolean = false,
        val activeTool: String? = null,
        val activeColor: Int = android.graphics.Color.parseColor("#007AFF"),
        val highlightColor: Int = android.graphics.Color.parseColor("#FFFF00"),
        val strokeWidth: Float = 5f,
        val isBookmarked: Boolean = false,
        val pageTextMap: Map<Int, String> = emptyMap(),
        val extractedText: String = "",
        val showTextDialog: Boolean = false,
        val textDialogTitle: String = "",
        val ocrResult: String = "",
        val isOcrRunning: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // -------------------------------------------------------
    // PDF STATE
    // -------------------------------------------------------

    private var pdfUri: Uri? = null
    private var pdfFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private val pageBitmapCache = android.util.LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceAtLeast(8 * 1024)
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    // -------------------------------------------------------
    // ANNOTATION STATE
    // -------------------------------------------------------

    private val annotationStore = mutableMapOf<Int, MutableList<AnnotationData>>()
    private val undoStack = ArrayDeque<AnnotationAction>()
    private val redoStack = ArrayDeque<AnnotationAction>()

    data class AnnotationData(
        val id: String = java.util.UUID.randomUUID().toString(),
        val pageIndex: Int,
        val type: String,
        val points: List<android.graphics.PointF>,
        val color: Int,
        val strokeWidth: Float,
        val text: String? = null
    )

    sealed class AnnotationAction {
        data class Add(val annotation: AnnotationData) : AnnotationAction()
        data class Remove(val annotation: AnnotationData) : AnnotationAction()
    }

    // -------------------------------------------------------
    // PDF LOADING
    // -------------------------------------------------------

    fun loadPdf(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val file = withContext(Dispatchers.IO) {
                    copyUriToCache(context, uri)
                }
                if (file == null || !file.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Cannot read PDF file"
                    )
                    return@launch
                }
                pdfFile = file
                pdfUri = uri
                withContext(Dispatchers.IO) {
                    openRenderer(file)
                }
                val pageCount = pdfRenderer?.pageCount ?: 0
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalPages = pageCount,
                    currentPage = 0,
                    isDarkMode = repository.isDarkMode()
                )
                loadAnnotationsFromCache(context)
                updateBookmarkStatus()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error opening PDF: ${e.message}"
                )
            }
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val dest = File(context.cacheDir, "viewer_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.length() > 0) dest else null
        } catch (_: Exception) { null }
    }

    private fun openRenderer(file: File) {
        closeRenderer()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(pfd)
    }

    private fun closeRenderer() {
        try { pdfRenderer?.close() } catch (_: Exception) {}
        pdfRenderer = null
    }

    // -------------------------------------------------------
    // PAGE RENDERING
    // -------------------------------------------------------

    fun renderPage(pageIndex: Int, screenWidth: Int): Bitmap? {
        val cached = pageBitmapCache.get(pageIndex)
        if (cached != null && !cached.isRecycled) return cached

        val renderer = pdfRenderer ?: return null
        return try {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val scale = screenWidth.toFloat() / page.width.coerceAtLeast(1)
                val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pageBitmapCache.put(pageIndex, bmp)
                bmp
            }
        } catch (_: Exception) { null }
    }

    fun preloadPages(anchorPage: Int, screenWidth: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = (anchorPage - 2).coerceAtLeast(0)
            val end = (anchorPage + 2).coerceAtMost((_uiState.value.totalPages - 1).coerceAtLeast(0))
            for (i in start..end) {
                if (pageBitmapCache.get(i) == null) {
                    renderPage(i, screenWidth)
                }
            }
        }
    }

    fun getPageScale(pageIndex: Int, screenWidth: Int): Float {
        val renderer = pdfRenderer ?: return 1f
        return try {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val scale = screenWidth.toFloat() / page.width.coerceAtLeast(1)
                page.close()
                scale
            }
        } catch (_: Exception) { 1f }
    }

    // -------------------------------------------------------
    // PAGE NAVIGATION
    // -------------------------------------------------------

    fun goToPage(page: Int) {
        val total = _uiState.value.totalPages
        if (page in 0 until total) {
            _uiState.value = _uiState.value.copy(currentPage = page)
            updateBookmarkStatus()
        }
    }

    fun nextPage() {
        goToPage(_uiState.value.currentPage + 1)
    }

    fun previousPage() {
        goToPage(_uiState.value.currentPage - 1)
    }

    // -------------------------------------------------------
    // SEARCH
    // -------------------------------------------------------

    fun toggleSearch() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isSearchVisible = !current.isSearchVisible,
            searchQuery = if (current.isSearchVisible) "" else current.searchQuery,
            searchResults = if (current.isSearchVisible) emptyList() else current.searchResults
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun runSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchQuery = query, isLoading = true)
            val results = withContext(Dispatchers.IO) {
                val q = query.lowercase()
                (0 until _uiState.value.totalPages).filter { idx ->
                    extractPageText(idx).lowercase().contains(q)
                }
            }
            _uiState.value = _uiState.value.copy(
                searchResults = results,
                searchResultIndex = 0,
                isLoading = false
            )
        }
    }

    fun nextSearchResult() {
        val current = _uiState.value
        if (current.searchResults.isNotEmpty()) {
            val next = (current.searchResultIndex + 1) % current.searchResults.size
            _uiState.value = current.copy(searchResultIndex = next)
            goToPage(current.searchResults[next])
        }
    }

    fun previousSearchResult() {
        val current = _uiState.value
        if (current.searchResults.isNotEmpty()) {
            val prev = (current.searchResultIndex - 1 + current.searchResults.size) % current.searchResults.size
            _uiState.value = current.copy(searchResultIndex = prev)
            goToPage(current.searchResults[prev])
        }
    }

    private fun extractPageText(pageIndex: Int): String {
        val file = pdfFile ?: return ""
        return try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(doc).trim()
            }
        } catch (_: Exception) { "" }
    }

    // -------------------------------------------------------
    // ANNOTATIONS
    // -------------------------------------------------------

    fun addAnnotation(annotation: AnnotationData) {
        val list = annotationStore.getOrPut(annotation.pageIndex) { mutableListOf() }
        list.add(annotation)
        undoStack.addLast(AnnotationAction.Add(annotation))
        redoStack.clear()
    }

    fun removeAnnotation(pageIndex: Int, annotation: AnnotationData) {
        annotationStore[pageIndex]?.remove(annotation)
        undoStack.addLast(AnnotationAction.Remove(annotation))
        redoStack.clear()
    }

    fun getAnnotations(pageIndex: Int): List<AnnotationData> {
        return annotationStore[pageIndex] ?: emptyList()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        when (action) {
            is AnnotationAction.Add -> {
                annotationStore[action.annotation.pageIndex]?.remove(action.annotation)
                redoStack.addLast(action)
            }
            is AnnotationAction.Remove -> {
                annotationStore.getOrPut(action.annotation.pageIndex) { mutableListOf() }
                    .add(action.annotation)
                redoStack.addLast(action)
            }
        }
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is AnnotationAction.Add -> {
                annotationStore.getOrPut(action.annotation.pageIndex) { mutableListOf() }
                    .add(action.annotation)
                undoStack.addLast(action)
            }
            is AnnotationAction.Remove -> {
                annotationStore[action.annotation.pageIndex]?.remove(action.annotation)
                undoStack.addLast(action)
            }
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun hasAnnotations(): Boolean = annotationStore.values.any { it.isNotEmpty() }

    // -------------------------------------------------------
    // ANNOTATION TOOL STATE
    // -------------------------------------------------------

    fun setActiveTool(tool: String?) {
        _uiState.value = _uiState.value.copy(activeTool = tool)
    }

    fun setActiveColor(color: Int) {
        _uiState.value = _uiState.value.copy(activeColor = color)
    }

    fun setHighlightColor(color: Int) {
        _uiState.value = _uiState.value.copy(highlightColor = color)
    }

    fun setStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.copy(strokeWidth = width)
    }

    fun toggleAnnotToolbar() {
        val current = _uiState.value
        _uiState.value = current.copy(isAnnotToolbarExpanded = !current.isAnnotToolbarExpanded)
    }

    // -------------------------------------------------------
    // READING MODE
    // -------------------------------------------------------

    fun setReadingMode(mode: String) {
        _uiState.value = _uiState.value.copy(readingMode = mode)
    }

    // -------------------------------------------------------
    // BOOKMARKS
    // -------------------------------------------------------

    fun toggleBookmark() {
        val uri = pdfUri ?: return
        val page = _uiState.value.currentPage
        if (_uiState.value.isBookmarked) {
            repository.removeBookmark(uri, page)
        } else {
            repository.addBookmark(uri, page, "Page ${page + 1}")
        }
        updateBookmarkStatus()
    }

    private fun updateBookmarkStatus() {
        val uri = pdfUri ?: return
        val page = _uiState.value.currentPage
        _uiState.value = _uiState.value.copy(
            isBookmarked = repository.isPageBookmarked(uri, page)
        )
    }

    // -------------------------------------------------------
    // OCR
    // -------------------------------------------------------

    fun runOcrOnPage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOcrRunning = true)
            try {
                val ocrManager = com.propdf.editor.data.repository.OcrManager()
                val result = ocrManager.recognizeText(bitmap)
                result.onSuccess { text ->
                    _uiState.value = _uiState.value.copy(
                        ocrResult = text,
                        isOcrRunning = false,
                        showTextDialog = true,
                        textDialogTitle = "OCR Result"
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "OCR failed: ${e.message}",
                        isOcrRunning = false
                    )
                }
                ocrManager.release()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "OCR error: ${e.message}",
                    isOcrRunning = false
                )
            }
        }
    }

    fun extractAllText() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val text = withContext(Dispatchers.IO) {
                val file = pdfFile ?: return@withContext "No PDF loaded"
                try {
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                        com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                    }
                } catch (e: Exception) {
                    "Error extracting text: ${e.message}"
                }
            }
            _uiState.value = _uiState.value.copy(
                extractedText = text,
                isLoading = false,
                showTextDialog = true,
                textDialogTitle = "Document Text"
            )
        }
    }

    fun dismissTextDialog() {
        _uiState.value = _uiState.value.copy(showTextDialog = false)
    }

    // -------------------------------------------------------
    // SAVE ANNOTATIONS
    // -------------------------------------------------------

    fun saveAnnotations(context: Context, saveAs: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = withContext(Dispatchers.IO) {
                try {
                    saveAnnotationsToPdf(context, saveAs)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = if (result) null else "Failed to save annotations"
            )
        }
    }

    private fun saveAnnotationsToPdf(context: Context, saveAs: Boolean): Boolean {
        val input = pdfFile ?: return false
        val output = File(context.cacheDir, "annotated_${System.currentTimeMillis()}.pdf")
        val doc = android.graphics.pdf.PdfDocument()
        try {
            for (i in 0 until _uiState.value.totalPages) {
                val bmp = renderPage(i, 1080) ?: continue
                val outBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(outBmp)
                // Render annotations
                val anns = getAnnotations(i)
                anns.forEach { ann ->
                    // Simplified rendering - full implementation in PdfOperationsManager
                }
                val pi = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                    outBmp.width, outBmp.height, i + 1
                ).create()
                val page = doc.startPage(pi)
                page.canvas.drawBitmap(outBmp, 0f, 0f, null)
                doc.finishPage(page)
                outBmp.recycle()
            }
            FileOutputStream(output).use { doc.writeTo(it) }
            persistAnnotationCache(context)
            true
        } finally {
            doc.close()
        }
    }

    private fun persistAnnotationCache(context: Context) {
        val uri = pdfUri ?: return
        val safeId = uri.toString().hashCode().toString()
        val file = File(context.cacheDir, "annot_${safeId}.json")
        // Serialize annotations to JSON
        try {
            val json = buildString {
                append("{"pages":{")
                annotationStore.entries.forEachIndexed { idx, entry ->
                    if (idx > 0) append(",")
                    append(""${entry.key}":[")
                    entry.value.forEachIndexed { aIdx, ann ->
                        if (aIdx > 0) append(",")
                        append("{"id":"${ann.id}","type":"${ann.type}","color":${ann.color},"strokeWidth":${ann.strokeWidth}}")
                    }
                    append("]")
                }
                append("}}")
            }
            file.writeText(json)
        } catch (_: Exception) {}
    }

    private fun loadAnnotationsFromCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = pdfUri ?: return@launch
                val safeId = uri.toString().hashCode().toString()
                val file = File(context.cacheDir, "annot_${safeId}.json")
                if (!file.exists()) return@launch
                // Parse JSON and restore annotations
                // Simplified - full JSON parsing would go here
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------
    // CLEANUP
    // -------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        closeRenderer()
        pageBitmapCache.evictAll()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
