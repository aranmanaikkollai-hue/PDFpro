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

/**
 * OCR Manager using ML Kit Text Recognition.
 * Supports Latin script recognition from bitmaps.
 */
class OcrManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val TAG = "OcrManager"
    }

    /**
     * Recognize text from a bitmap image.
     * @param bitmap The image to process
     * @return Result containing the recognized text or an error
     */
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

    /**
     * Recognize text from a bitmap with structured block information.
     * @param bitmap The image to process
     * @return Result containing structured text blocks
     */
    suspend fun recognizeTextStructured(bitmap: Bitmap): Result<List<TextBlock>> = withContext(Dispatchers.IO) {
        runCatching {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = suspendCancellableCoroutine<List<TextBlock>> { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks.map { block ->
                            TextBlock(
                                text = block.text,
                                boundingBox = block.boundingBox,
                                lines = block.lines.map { line ->
                                    TextLine(
                                        text = line.text,
                                        boundingBox = line.boundingBox,
                                        elements = line.elements.map { element ->
                                            TextElement(
                                                text = element.text,
                                                boundingBox = element.boundingBox,
                                                confidence = element.confidence
                                            )
                                        }
                                    )
                                }
                            )
                        }
                        continuation.resume(blocks)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR structured failed", e)
                        continuation.resume(emptyList())
                    }
            }
            result
        }
    }

    /**
     * Release the OCR recognizer to free resources.
     */
    fun release() {
        recognizer.close()
    }

    // -------------------------------------------------------
    // DATA CLASSES
    // -------------------------------------------------------

    data class TextBlock(
        val text: String,
        val boundingBox: android.graphics.Rect?,
        val lines: List<TextLine>
    )

    data class TextLine(
        val text: String,
        val boundingBox: android.graphics.Rect?,
        val elements: List<TextElement>
    )

    data class TextElement(
        val text: String,
        val boundingBox: android.graphics.Rect?,
        val confidence: Float?
    )
}
