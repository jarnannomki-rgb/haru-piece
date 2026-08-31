package com.example.diaryapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate
import kotlin.math.abs

private const val QUESTION_DB_LOG_TAG = "HaruQuestionDb"

suspend fun fetchDbQuestion(
    profile: Profile,
    entries: List<DiaryEntry>,
    recordDate: LocalDate,
    step: Int,
    questionLimit: Int,
    groupKey: String?
): Question? = withTimeoutOrNull(8000) {
    withContext(Dispatchers.IO) {
        runCatching {
            val targetGroupKey = groupKey?.takeIf { it.isNotBlank() } ?: "start"
            val group = fetchQuestionGroup(targetGroupKey) ?: return@runCatching null
            val questions = fetchQuestionsForGroup(group, entries.map { it.date }.toSet().size, step)
            if (questions.isEmpty()) return@runCatching null

            val todayCategories = inferRecentCategories(entries.filter { it.date == recordDate.format(DateFormatter) }).toSet()
            val recentCategories = inferRecentCategories(entries.takeLast(5)).toSet()
            val filtered = questions
                .filter { step > 1 || it.category !in todayCategories }
                .ifEmpty { questions }
                .filter { step > 1 || recentCategories.size >= 4 || it.category !in recentCategories }
                .ifEmpty { questions }

            val optionsByQuestion = fetchOptionsForQuestions(filtered)
            val candidates = filtered.mapNotNull { question ->
                val options = optionsByQuestion[question.key].orEmpty()
                if (options.size >= 4) question.copy(options = options.take(4)) else null
            }
            if (candidates.isEmpty()) return@runCatching null

            pickQuestion(candidates, profile, recordDate, entries.size, step, questionLimit)
        }.onFailure { Log.w(QUESTION_DB_LOG_TAG, "DB question fetch failed", it) }.getOrNull()
    }
}

private data class QuestionGroupRef(val id: String, val key: String)

private fun fetchQuestionGroup(groupKey: String): QuestionGroupRef? {
    val encoded = groupKey.urlEncode()
    val json = supabaseGetArray("question_groups?select=id,group_key&group_key=eq.$encoded&limit=1")
    if (json.length() == 0) return null
    val row = json.getJSONObject(0)
    return QuestionGroupRef(row.optString("id"), row.optString("group_key", groupKey))
}

private fun fetchQuestionsForGroup(group: QuestionGroupRef, recordedDays: Int, step: Int): List<Question> {
    val encodedGroupId = group.id.urlEncode()
    val rows = supabaseGetArray("questions?select=*&is_active=eq.true&group_id=eq.$encodedGroupId&limit=200")
    return List(rows.length()) { index -> rows.getJSONObject(index) }
        .filter { row -> row.optInt("min_record_days", 0) <= recordedDays }
        .filter { row -> row.isNull("max_record_days") || row.optInt("max_record_days", 9999) >= recordedDays }
        .mapNotNull { row -> row.toQuestion(group.key, step) }
}

private fun JSONObject.toQuestion(groupKey: String, step: Int): Question? {
    val id = optString("id").ifBlank { optString("question_key") }
    val title = optString("question_text").ifBlank { optString("question") }
    if (id.isBlank() || title.isBlank()) return null
    return Question(
        title = title,
        options = emptyList(),
        category = optString("category").ifBlank { inferQuestionCategory(title) },
        key = id,
        groupKey = groupKey,
        depthLevel = optInt("depth_level", step),
        customAnswerType = optString("custom_answer_type").ifBlank { "activity" },
        defaultNextGroupKey = optString("default_next_group_key").ifBlank { null },
        cooldownDays = optInt("cooldown_days", 3),
        weight = optInt("weight", 100)
    )
}

private fun fetchOptionsForQuestions(questions: List<Question>): Map<String, List<AnswerOption>> {
    if (questions.isEmpty()) return emptyMap()
    val ids = questions.map { it.key }.filter { it.isNotBlank() }
    if (ids.isEmpty()) return emptyMap()
    val inValues = ids.joinToString(",") { it }
    val rows = supabaseGetArray("question_options?select=*&question_id=in.($inValues)&order=option_order.asc&limit=800")
    return List(rows.length()) { index -> rows.getJSONObject(index) }
        .mapNotNull { row ->
            val questionId = row.optString("question_id")
            val label = row.optString("label")
            val sentence = row.optString("diary_sentence")
            if (questionId.isBlank() || label.isBlank() || sentence.isBlank()) null else questionId to AnswerOption(
                label = label,
                sentence = polishDiaryText(sentence),
                nextGroupKey = row.optString("next_group_key").ifBlank { null },
                value = row.optString("answer_value").ifBlank { null }
            )
        }
        .groupBy({ it.first }, { it.second })
}

private fun pickQuestion(
    candidates: List<Question>,
    profile: Profile,
    recordDate: LocalDate,
    entryCount: Int,
    step: Int,
    questionLimit: Int
): Question {
    val weighted = candidates.flatMap { question ->
        val topicBonus = if (profile.topics.any { topic -> question.category.contains(topic) || question.title.contains(topic) }) 2 else 0
        val repeat = ((question.weight.coerceAtLeast(1) / 50) + topicBonus).coerceIn(1, 5)
        List(repeat) { question }
    }
    val seed = abs((recordDate.dayOfYear * 31) + (entryCount * 17) + (step * 13) + questionLimit)
    return weighted[seed % weighted.size]
}

private fun supabaseGetArray(pathAndQuery: String): JSONArray {
    val connection = (URL("${BuildConfig.SUPABASE_URL}/rest/v1/$pathAndQuery").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5000
        readTimeout = 8000
        setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
        setRequestProperty("Accept", "application/json")
    }
    val status = connection.responseCode
    val responseText = if (status in 200..299) {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } else {
        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }
    connection.disconnect()
    if (status !in 200..299) {
        Log.w(QUESTION_DB_LOG_TAG, "Supabase GET failed status=$status body=${responseText.take(240)}")
        return JSONArray()
    }
    return JSONArray(responseText.ifBlank { "[]" })
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")