package com.propdf.editor.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.propdf.editor.data.repository.PdfOperationsManager
import com.propdf.editor.utils.FileHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val pdfOperationsManager: PdfOperationsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _pageCount = MutableLiveData<Int>()
    val pageCount: LiveData<Int> = _pageCount

    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            // Resolve page count using PdfRenderer
            try {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                fd?.use {
                    val renderer = android.graphics.pdf.PdfRenderer(it)
                    _pageCount.postValue(renderer.pageCount)
                    renderer.close()
                }
            } catch (e: Exception) {
                _pageCount.postValue(0)
            }
        }
    }

    fun nextPage() {
        _currentPage.value = (_currentPage.value ?: 0) + 1
    }

    fun previousPage() {
        val cur = _currentPage.value ?: 0
        if (cur > 0) _currentPage.value = cur - 1
    }

    fun goToPage(page: Int) {
        _currentPage.value = page
    }

    /**
     * Compress a PDF given its Uri. Returns true on success.
     * Converts Uri -> File, runs compressPdf, result is delivered via callback.
     */
    fun compressPdf(uri: Uri, outputFile: File, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val file = FileHelper.uriToFile(context, uri)
            if (file == null) { onDone(false); return@launch }
            val result = pdfOperationsManager.compressPdf(file, outputFile, 9)
            onDone(result.isSuccess)
        }
    }
}
