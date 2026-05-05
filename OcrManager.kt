package com.propdf.editor.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class OcrManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val TAG = "OcrManager"
    }

    suspend fun recognizeText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = suspendCancellableCoroutine<String> { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        if (text.isBlank()) {
                            continuation.resume("No text detected in image.")
                        } else {
                            continuation.resume(text)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR failed", e)
                        continuation.resume("OCR Error: ${e.message}")
                    }
            }
            result
        }
    }

    fun release() {
        recognizer.close()
    }
}
