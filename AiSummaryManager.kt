package com.propdf.editor.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI-powered text analysis manager.
 * Provides summarization, key point extraction, and document analysis.
 * Uses a placeholder API - replace with your actual AI service endpoint.
 */
@Singleton
class AiSummaryManager @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        // Replace with your actual AI API endpoint
        private const val PLACEHOLDER_API_URL = "https://api.example.com/v1/analyze"
        private const val MAX_TEXT_LENGTH = 8000
    }

    // -------------------------------------------------------
    // SUMMARIZATION
    // -------------------------------------------------------

    suspend fun summarize(text: String, maxSentences: Int = 5): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (text.isBlank()) return@runCatching "No text to summarize."

                val truncated = text.take(MAX_TEXT_LENGTH)

                // Placeholder: In production, call your AI API
                // For now, return an extractive summary
                val sentences = truncated.split(Regex("[.!?]+"))
                    .map { it.trim() }
                    .filter { it.length > 10 }

                if (sentences.isEmpty()) return@runCatching "Text too short to summarize."

                // Simple extractive summarization: pick first N sentences
                val summary = sentences.take(maxSentences.coerceAtLeast(1))
                    .joinToString(". ") + "."

                // Try API call if configured
                val apiResult = callAiApi("summarize", truncated, maxSentences)
                apiResult ?: "Summary:

$summary"
            }
        }

    // -------------------------------------------------------
    // KEY POINTS EXTRACTION
    // -------------------------------------------------------

    suspend fun extractKeyPoints(text: String, maxPoints: Int = 7): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (text.isBlank()) return@runCatching emptyList<String>()

                val truncated = text.take(MAX_TEXT_LENGTH)
                val sentences = truncated.split(Regex("[.!?]+"))
                    .map { it.trim() }
                    .filter { it.length > 15 }

                // Simple heuristic: sentences with numbers, quotes, or key phrases
                val scored = sentences.map { sentence ->
                    var score = 0
                    if (sentence.contains(Regex("\d+"))) score += 2
                    if (sentence.contains(Regex("(important|key|main|primary|critical|essential|significant)"), RegexOption.IGNORE_CASE)) score += 3
                    if (sentence.contains(Regex("(conclusion|result|finding|summary|therefore|thus)"), RegexOption.IGNORE_CASE)) score += 2
                    if (sentence.contains(""")) score += 1
                    if (sentence.length in 50..200) score += 1
                    sentence to score
                }

                val topPoints = scored.sortedByDescending { it.second }
                    .take(maxPoints.coerceAtLeast(1))
                    .map { it.first }

                topPoints
            }
        }

    // -------------------------------------------------------
    // DOCUMENT ANALYSIS
    // -------------------------------------------------------

    suspend fun analyzeDocument(text: String): Result<DocumentAnalysis> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (text.isBlank()) return@runCatching DocumentAnalysis()

                val wordCount = text.split(Regex("\s+")).size
                val sentenceCount = text.split(Regex("[.!?]+")).size
                val paragraphCount = text.split(Regex("\n\s*\n")).size

                // Estimate reading time (average 200 words per minute)
                val readingTimeMinutes = (wordCount / 200.0).toInt().coerceAtLeast(1)

                // Detect language (simple heuristic)
                val language = detectLanguage(text)

                // Sentiment (simple keyword-based)
                val sentiment = analyzeSentiment(text)

                DocumentAnalysis(
                    wordCount = wordCount,
                    sentenceCount = sentenceCount,
                    paragraphCount = paragraphCount,
                    estimatedReadingTime = "$readingTimeMinutes min",
                    language = language,
                    sentiment = sentiment,
                    topKeywords = extractKeywords(text)
                )
            }
        }

    // -------------------------------------------------------
    // PLACEHOLDER API CALL
    // -------------------------------------------------------

    private suspend fun callAiApi(action: String, text: String, param: Int): String? {
        return try {
            val json = JSONObject().apply {
                put("action", action)
                put("text", text)
                put("param", param)
            }

            val request = Request.Builder()
                .url(PLACEHOLDER_API_URL)
                .post(json.toString().toRequestBody(jsonMediaType))
                .header("Authorization", "Bearer YOUR_API_KEY")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val jsonResponse = JSONObject(body)
                jsonResponse.optString("result", null)
            }
        } catch (_: Exception) { null }
    }

    // -------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------

    private fun detectLanguage(text: String): String {
        // Simple heuristic - in production use ML Kit Language Identification
        val sample = text.take(500).lowercase()
        return when {
            sample.contains(Regex("[\u0B80-\u0BFF]")) -> "Tamil"
            sample.contains(Regex("[\u0900-\u097F]")) -> "Hindi"
            sample.contains(Regex("[\u0600-\u06FF]")) -> "Arabic"
            sample.contains(Regex("[\u4E00-\u9FFF]")) -> "Chinese"
            sample.contains(Regex("[\u3040-\u309F\u30A0-\u30FF]")) -> "Japanese"
            else -> "English"
        }
    }

    private fun analyzeSentiment(text: String): String {
        val positiveWords = listOf("good", "great", "excellent", "amazing", "wonderful", "best", "love", "happy", "success", "positive")
        val negativeWords = listOf("bad", "terrible", "awful", "worst", "hate", "sad", "failure", "negative", "problem", "issue")

        val lower = text.lowercase()
        val posCount = positiveWords.count { lower.contains(it) }
        val negCount = negativeWords.count { lower.contains(it) }

        return when {
            posCount > negCount * 1.5 -> "Positive"
            negCount > posCount * 1.5 -> "Negative"
            else -> "Neutral"
        }
    }

    private fun extractKeywords(text: String): List<String> {
        val words = text.lowercase()
            .split(Regex("[^a-zA-Z]+"))
            .filter { it.length > 4 }
            .filterNot { it in stopWords }

        return words.groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .map { it.first }
    }

    private val stopWords = setOf(
        "about", "above", "after", "again", "against", "all", "also", "am", "an", "and", "any", "are",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
        "by", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for", "from",
        "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him",
        "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "me", "more",
        "most", "my", "myself", "nor", "of", "on", "once", "only", "or", "other", "ought", "our",
        "ours", "ourselves", "out", "over", "own", "same", "she", "should", "so", "some", "such",
        "than", "that", "the", "their", "theirs", "them", "themselves", "then", "there", "these",
        "they", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was",
        "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why", "will",
        "with", "would", "you", "your", "yours", "yourself", "yourselves"
    )

    // -------------------------------------------------------
    // DATA CLASS
    // -------------------------------------------------------

    data class DocumentAnalysis(
        val wordCount: Int = 0,
        val sentenceCount: Int = 0,
        val paragraphCount: Int = 0,
        val estimatedReadingTime: String = "",
        val language: String = "Unknown",
        val sentiment: String = "Neutral",
        val topKeywords: List<String> = emptyList()
    ) {
        fun toDisplayString(): String {
            return buildString {
                appendLine("Document Analysis")
                appendLine("-----------------")
                appendLine("Words: $wordCount")
                appendLine("Sentences: $sentenceCount")
                appendLine("Paragraphs: $paragraphCount")
                appendLine("Reading Time: $estimatedReadingTime")
                appendLine("Language: $language")
                appendLine("Sentiment: $sentiment")
                if (topKeywords.isNotEmpty()) {
                    appendLine("Top Keywords: ${topKeywords.joinToString(", ")}")
                }
            }
        }
    }
}
