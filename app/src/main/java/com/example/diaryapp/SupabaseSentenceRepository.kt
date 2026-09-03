package com.example.diaryapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PolishedSentence(
    val sentence: String,
    val needsReview: Boolean
)

private const val SENTENCE_API_LOG_TAG = "HaruSentenceApi"
private const val SENTENCE_FUNCTION_NAME = "polish-diary-sentence"

suspend fun polishCustomDiarySentence(
    question: String,
    answer: String,
    localDraft: String
): PolishedSentence? = withTimeoutOrNull(10_000) {
    withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("question", question)
                .put("answer", answer)
                .put("draft", localDraft)
                .toString()

            val connection = (
                URL("${BuildConfig.SUPABASE_URL}/functions/v1/$SENTENCE_FUNCTION_NAME")
                    .openConnection() as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 9_000
                doOutput = true
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val responseText = if (status in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()

            if (status !in 200..299) {
                Log.w(SENTENCE_API_LOG_TAG, "Sentence function failed status=$status body=${responseText.take(240)}")
                return@runCatching null
            }

            val response = JSONObject(responseText)
            val sentence = response.optString("sentence").trim()
            if (sentence.isBlank()) {
                Log.w(SENTENCE_API_LOG_TAG, "Sentence function returned an empty sentence")
                return@runCatching null
            }
            PolishedSentence(
                sentence = DiarySentenceEngine.polish(sentence),
                needsReview = response.optBoolean("needsReview", false)
            )
        }.onFailure {
            Log.w(SENTENCE_API_LOG_TAG, "Sentence polishing failed; using local fallback", it)
        }.getOrNull()
    }
}
