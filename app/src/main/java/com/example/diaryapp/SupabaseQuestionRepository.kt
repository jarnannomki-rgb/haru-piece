package com.example.diaryapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate
import kotlin.math.abs

private const val QUESTION_DB_LOG_TAG = "HaruQuestionDb"
private const val QUESTION_CACHE_NAME = "haru_question_cache"
private const val QUESTION_FETCH_TIMEOUT_MS = 15_000L
private const val QUESTION_FETCH_RETRY_DELAY_MS = 700L
private val questionCacheMutex = Mutex()

private val allStartTopics = listOf(
    "식사", "기분", "일", "사람", "건강", "날씨", "소비", "운동",
    "가족", "집", "취미", "휴식", "공부", "이동", "약속", "생각"
)
private val highFrequencyTopics = listOf("식사", "건강", "휴식", "이동", "집")
private val mediumFrequencyTopics = listOf("기분", "날씨", "소비")
private val safeTopicPool = highFrequencyTopics.flatMap { topic -> List(13) { topic } } +
    mediumFrequencyTopics.flatMap { topic -> List(9) { topic } }

suspend fun fetchDbQuestion(
    context: Context,
    profile: Profile,
    entries: List<DiaryEntry>,
    recordDate: LocalDate,
    step: Int,
    questionLimit: Int,
    groupKey: String?
): Question? = withTimeoutOrNull(QUESTION_FETCH_TIMEOUT_MS) {
    withContext(Dispatchers.IO) {
        val targetGroupKey = groupKey?.takeIf { it.isNotBlank() } ?: "start"
        val seed = questionSeed(profile, recordDate, entries.size, step, questionLimit, targetGroupKey)
        val category = if (targetGroupKey == "start") chooseStartCategory(profile.topics, seed) else null
        val cacheKey = questionCacheKey(recordDate, entries.size, step, questionLimit, targetGroupKey, category, profile.topics, seed)

        loadCachedQuestion(context, cacheKey) ?: questionCacheMutex.withLock {
            loadCachedQuestion(context, cacheKey) ?: fetchQuestionWithRetry(
                groupKey = targetGroupKey,
                category = category,
                seed = seed,
                recordedDays = entries.map { it.date }.toSet().size
            )?.also { question ->
                saveCachedQuestion(context, cacheKey, question)
            }
        }
    }
}

private suspend fun fetchQuestionWithRetry(
    groupKey: String,
    category: String?,
    seed: Int,
    recordedDays: Int
): Question? {
    repeat(2) { attempt ->
        val question = runCatching {
            val candidates = fetchQuestionCandidates(groupKey, category)
                .filter { it.minRecordDays <= recordedDays }
                .filter { it.maxRecordDays == null || it.maxRecordDays >= recordedDays }
                .mapNotNull { it.question.takeIf { question -> question.options.size >= 4 } }
            candidates
                .takeIf { it.isNotEmpty() }
                ?.get(Math.floorMod(seed, candidates.size))
        }.onFailure {
            Log.w(QUESTION_DB_LOG_TAG, "DB question fetch failed (attempt ${attempt + 1})", it)
        }.getOrNull()

        if (question != null) return question
        if (attempt == 0) delay(QUESTION_FETCH_RETRY_DELAY_MS)
    }
    return null
}

suspend fun prefetchDbNextQuestions(
    context: Context,
    profile: Profile,
    entries: List<DiaryEntry>,
    recordDate: LocalDate,
    step: Int,
    questionLimit: Int,
    groupKeys: List<String>
) {
    withTimeoutOrNull(5_000) {
        withContext(Dispatchers.IO) {
            val targets = groupKeys
                .filter { it.isNotBlank() && it != "start" }
                .distinct()
                .map { groupKey ->
                    val seed = questionSeed(profile, recordDate, entries.size, step, questionLimit, groupKey)
                    PrefetchTarget(
                        groupKey = groupKey,
                        seed = seed,
                        cacheKey = questionCacheKey(
                            recordDate,
                            entries.size,
                            step,
                            questionLimit,
                            groupKey,
                            null,
                            profile.topics,
                            seed
                        )
                    )
                }
            if (targets.isEmpty() || targets.all { loadCachedQuestion(context, it.cacheKey) != null }) {
                return@withContext
            }

            questionCacheMutex.withLock {
                val pending = targets.filter { loadCachedQuestion(context, it.cacheKey) == null }
                if (pending.isEmpty()) return@withLock

                runCatching {
                    val recordedDays = entries.map { it.date }.toSet().size
                    val candidatesByGroup = fetchQuestionCandidatesForGroups(pending.map { it.groupKey })
                        .filter { it.minRecordDays <= recordedDays }
                        .filter { it.maxRecordDays == null || it.maxRecordDays >= recordedDays }
                        .filter { it.question.options.size >= 4 }
                        .groupBy { it.question.groupKey }

                    pending.forEach { target ->
                        val candidates = candidatesByGroup[target.groupKey].orEmpty()
                        if (candidates.isNotEmpty()) {
                            val question = candidates[Math.floorMod(target.seed, candidates.size)].question
                            saveCachedQuestion(context, target.cacheKey, question)
                        }
                    }
                }.onFailure {
                    Log.w(QUESTION_DB_LOG_TAG, "Next question prefetch failed", it)
                }
            }
        }
    }
}

private data class PrefetchTarget(
    val groupKey: String,
    val seed: Int,
    val cacheKey: String
)

private fun candidateCount(groupKey: String): Int = when {
    groupKey == "start" -> 5
    groupKey.endsWith("_d2") -> 3
    groupKey.endsWith("_d3") -> 2
    else -> 1
}

internal fun chooseStartCategory(selectedTopics: List<String>, seed: Int): String {
    val selected = selectedTopics.filter { it in allStartTopics }.distinct()
    val useSelectedTopic = selected.isNotEmpty() && Math.floorMod(seed, 10) < 7
    val pool = if (useSelectedTopic) selected else safeTopicPool
    return pool[Math.floorMod(seed / 10, pool.size)]
}

private fun questionSeed(
    profile: Profile,
    recordDate: LocalDate,
    entryCount: Int,
    step: Int,
    questionLimit: Int,
    groupKey: String
): Int {
    var result = 17
    listOf(
        profile.name,
        profile.gender,
        profile.age,
        profile.topics.sorted().joinToString("|"),
        recordDate.toString(),
        entryCount.toString(),
        step.toString(),
        questionLimit.toString(),
        groupKey
    ).forEach { value -> result = 31 * result + value.hashCode() }
    return if (result == Int.MIN_VALUE) 0 else abs(result)
}

private data class QuestionCandidate(
    val question: Question,
    val minRecordDays: Int,
    val maxRecordDays: Int?
)

private fun fetchQuestionCandidates(groupKey: String, category: String?): List<QuestionCandidate> {
    val select = questionSelect()
    val filters = buildList {
        add("select=$select")
        add("question_groups.group_key=eq.${groupKey.urlEncode()}")
        add("is_active=eq.true")
        category?.let { add("category=eq.${it.urlEncode()}") }
        add("order=question_key.asc")
        add("question_options.order=option_order.asc")
        add("limit=${candidateCount(groupKey)}")
    }.joinToString("&")
    val rows = supabaseGetArray("questions?$filters")
    return List(rows.length()) { index -> rows.getJSONObject(index) }
        .mapNotNull(JSONObject::toQuestionCandidate)
}

private fun fetchQuestionCandidatesForGroups(groupKeys: List<String>): List<QuestionCandidate> {
    if (groupKeys.isEmpty()) return emptyList()
    val select = questionSelect()
    val encodedGroups = groupKeys.joinToString("%2C") { it.urlEncode() }
    val limit = groupKeys.sumOf(::candidateCount)
    val filters = listOf(
        "select=$select",
        "question_groups.group_key=in.%28$encodedGroups%29",
        "is_active=eq.true",
        "order=question_key.asc",
        "question_options.order=option_order.asc",
        "limit=$limit"
    ).joinToString("&")
    val rows = supabaseGetArray("questions?$filters")
    return List(rows.length()) { index -> rows.getJSONObject(index) }
        .mapNotNull(JSONObject::toQuestionCandidate)
}

private fun questionSelect(): String = listOf(
    "id", "question_key", "question_text", "category", "depth_level",
    "custom_answer_type", "default_next_group_key", "cooldown_days", "weight",
    "min_record_days", "max_record_days", "question_groups!inner(group_key)",
    "question_options(label,diary_sentence,next_group_key,answer_value,option_order)"
).joinToString(",")

private fun JSONObject.toQuestionCandidate(): QuestionCandidate? {
    val id = optString("id").ifBlank { optString("question_key") }
    val title = optString("question_text").ifBlank { optString("question") }
    if (id.isBlank() || title.isBlank()) return null

    val groupKey = optJSONObject("question_groups")?.optString("group_key").orEmpty()
    val optionRows = optJSONArray("question_options") ?: JSONArray()
    val options = List(optionRows.length()) { index -> optionRows.getJSONObject(index) }
        .sortedBy { row -> row.optInt("option_order", Int.MAX_VALUE) }
        .mapNotNull { row ->
            val label = row.optString("label")
            val sentence = row.optString("diary_sentence")
            if (label.isBlank() || sentence.isBlank()) null else AnswerOption(
                label = label,
                sentence = polishDiaryText(sentence),
                nextGroupKey = row.nullableString("next_group_key"),
                value = row.nullableString("answer_value")
            )
        }

    return QuestionCandidate(
        question = Question(
            title = title,
            options = options.take(4),
            category = optString("category").ifBlank { inferQuestionCategory(title) },
            key = id,
            groupKey = groupKey,
            depthLevel = optInt("depth_level", 1),
            customAnswerType = optString("custom_answer_type").ifBlank { "activity" },
            defaultNextGroupKey = nullableString("default_next_group_key"),
            cooldownDays = optInt("cooldown_days", 3),
            weight = optInt("weight", 100)
        ),
        minRecordDays = optInt("min_record_days", 0),
        maxRecordDays = if (isNull("max_record_days")) null else optInt("max_record_days")
    )
}

private fun questionCacheKey(
    recordDate: LocalDate,
    entryCount: Int,
    step: Int,
    questionLimit: Int,
    groupKey: String,
    category: String?,
    topics: List<String>,
    seed: Int
): String = listOf(
    "question",
    recordDate.toString(),
    entryCount.toString(),
    step.toString(),
    questionLimit.toString(),
    groupKey,
    category.orEmpty(),
    topics.sorted().joinToString("-"),
    seed.toString()
).joinToString("|")

private fun loadCachedQuestion(context: Context, key: String): Question? {
    val raw = context.getSharedPreferences(QUESTION_CACHE_NAME, Context.MODE_PRIVATE)
        .getString(key, null) ?: return null
    return runCatching { JSONObject(raw).toCachedQuestion() }.getOrNull()
}

private fun saveCachedQuestion(context: Context, key: String, question: Question) {
    val preferences = context.getSharedPreferences(QUESTION_CACHE_NAME, Context.MODE_PRIVATE)
    val currentDate = key.substringAfter("question|").substringBefore("|")
    val editor = preferences.edit()
    preferences.all.keys
        .filter { storedKey -> storedKey.startsWith("question|") && !storedKey.startsWith("question|$currentDate|") }
        .forEach(editor::remove)
    editor.putString(key, question.toCacheJson().toString()).apply()
}

private fun Question.toCacheJson(): JSONObject = JSONObject()
    .put("title", title)
    .put("category", category)
    .put("key", key)
    .put("groupKey", groupKey)
    .put("depthLevel", depthLevel)
    .put("customAnswerType", customAnswerType)
    .put("defaultNextGroupKey", defaultNextGroupKey)
    .put("cooldownDays", cooldownDays)
    .put("weight", weight)
    .put("options", JSONArray().apply {
        options.forEach { option ->
            put(JSONObject()
                .put("label", option.label)
                .put("sentence", option.sentence)
                .put("nextGroupKey", option.nextGroupKey)
                .put("value", option.value))
        }
    })

private fun JSONObject.toCachedQuestion(): Question? {
    val title = optString("title")
    if (title.isBlank()) return null
    val rows = optJSONArray("options") ?: JSONArray()
    val options = List(rows.length()) { index -> rows.getJSONObject(index) }.mapNotNull { row ->
        val label = row.optString("label")
        val sentence = row.optString("sentence")
        if (label.isBlank() || sentence.isBlank()) null else AnswerOption(
            label = label,
            sentence = sentence,
            nextGroupKey = row.nullableString("nextGroupKey"),
            value = row.nullableString("value")
        )
    }
    if (options.size < 4) return null
    return Question(
        title = title,
        options = options.take(4),
        category = optString("category"),
        key = optString("key"),
        groupKey = optString("groupKey"),
        depthLevel = optInt("depthLevel", 1),
        customAnswerType = optString("customAnswerType", "activity"),
        defaultNextGroupKey = nullableString("defaultNextGroupKey"),
        cooldownDays = optInt("cooldownDays", 3),
        weight = optInt("weight", 100)
    )
}

private fun supabaseGetArray(pathAndQuery: String): JSONArray {
    val connection = (URL("${BuildConfig.SUPABASE_URL}/rest/v1/$pathAndQuery").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 3_000
        readTimeout = 5_000
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

internal fun JSONObject.nullableString(name: String): String? {
    if (isNull(name)) return null
    return optString(name).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
