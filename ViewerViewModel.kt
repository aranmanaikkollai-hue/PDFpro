package com.propdf.editor.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerViewModel : ViewModel() {

    private val _pageCount = MutableLiveData<Int>()
    val pageCount: LiveData<Int> = _pageCount

    private val _currentPage = MutableLiveData<Int>()
    val currentPage: LiveData<Int> = _currentPage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var renderer: PdfRenderer? = null

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    // Renderer will be created per-page in adapter
                    _pageCount.postValue(1) // Placeholder, actual count from adapter
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isLoading.value = false
        }
    }

    fun setPageCount(count: Int) {
        _pageCount.value = count
    }

    fun goToPage(page: Int) {
        _currentPage.value = page
    }

    fun nextPage() {
        _currentPage.value?.let { current ->
            _pageCount.value?.let { total ->
                if (current < total - 1) {
                    _currentPage.value = current + 1
                }
            }
        }
    }

    fun previousPage() {
        _currentPage.value?.let { current ->
            if (current > 0) {
                _currentPage.value = current - 1
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderer?.close()
    }
}
