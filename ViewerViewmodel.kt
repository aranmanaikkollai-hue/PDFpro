package com.propdf.editor.ui.viewer

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.propdf.editor.data.repository.PdfOperationsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val pdfOperationsManager: PdfOperationsManager
) : ViewModel() {

    private val _pageCount = MutableLiveData<Int>()
    val pageCount: LiveData<Int> = _pageCount

    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            // Load PDF and get page count
            // For demo:
            _pageCount.postValue(10)
        }
    }

    fun nextPage() {
        _currentPage.value = (_currentPage.value ?: 0) + 1
    }

    fun previousPage() {
        _currentPage.value = (_currentPage.value ?: 0) - 1
    }

    fun signPdf(uri: Uri, outputFile: java.io.File, p12Stream: java.io.InputStream, password: String, reason: String, location: String): Boolean {
        return pdfOperationsManager.signPdf(uri, outputFile, p12Stream, password, reason, location)
    }

    fun compressPdf(uri: Uri, outputFile: java.io.File): Boolean {
        return pdfOperationsManager.compressPdf(uri, outputFile)
    }
}
