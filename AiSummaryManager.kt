package com.propdf.editor.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiSummaryManager @Inject constructor(private val context: Context) {

    suspend fun summarizeText(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext "No text to summarize."
        
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        if (sentences.size <= 3) return@withContext text
        
        val wordFreq = mutableMapOf<String, Int>()
        val words = text.lowercase().replace(Regex("[^a-z0-9\\s]"), "").split("\\s+".toRegex())
        
        words.forEach { word ->
            if (word.length > 3) {
                wordFreq[word] = wordFreq.getOrDefault(word, 0) + 1
            }
        }
        
        val scoredSentences = sentences.map { sentence ->
            val sentenceWords = sentence.lowercase().replace(Regex("[^a-z0-9\\s]"), "").split("\\s+".toRegex())
            val score = sentenceWords.sumOf { wordFreq.getOrDefault(it, 0) }
            sentence to score
        }.sortedByDescending { it.second }
        
        val summary = scoredSentences.take(maxOf(3, sentences.size / 3)).map { it.first }.joinToString(" ")
        "Summary:\n\n$summary"
    }

    suspend fun translateText(text: String, targetLanguage: String): String =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext "No text to translate."
            "Translation to $targetLanguage: [Premium Feature - API Key Required]"
        }
}
