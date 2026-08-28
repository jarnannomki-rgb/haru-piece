package com.example.diaryapp

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.os.Build
import android.widget.ImageView
import android.net.Uri
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS_NAME = "haru_piece_prefs"
private val QUESTION_FUNCTION_URL = BuildConfig.QUESTION_FUNCTION_URL
private const val AI_LOG_TAG = "HaruAi"
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val Blush = Color(0xFFFFEEEE)
private val Coral = Color(0xFFEF8585)
private val CoralDark = Color(0xFFD66D70)
private val Peach = Color(0xFFFFC8B8)
private val PeachSoft = Color(0xFFFFE4DB)
private val Lavender = Color(0xFFE7DAF2)
private val Mint = Color(0xFFD6EFE5)
private val Sky = Color(0xFFDCEAF8)
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF3B3030)
private val TopicOptions = listOf(
    "식사", "기분", "일", "사람",
    "건강", "날씨", "소비", "운동",
    "가족", "집", "취미", "휴식",
    "공부", "이동", "약속", "생각"
)
private val ReminderDayOptions = listOf("안 함", "매일", "평일", "주말", "일", "월", "화", "수", "목", "금", "토")
private val Muted = Color(0xFF897978)
private val Line = Color(0xFFF0DADA)

private val HaruColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Lavender,
    tertiary = Mint,
    background = Blush,
    surface = Paper,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    outline = Line
)

private val NightColorScheme = lightColorScheme(
    primary = Color(0xFFE79A9A),
    onPrimary = Color(0xFF2A2228),
    secondary = Color(0xFF6E617B),
    tertiary = Color(0xFF6C8178),
    background = Color(0xFF201C22),
    surface = Color(0xFF2C2630),
    onSurface = Color(0xFFF8EEF3),
    onSurfaceVariant = Color(0xFFCABDC5),
    outline = Color(0xFF554852)
)

private val PaperColorScheme = lightColorScheme(
    primary = Color(0xFFD9837B),
    onPrimary = Color.White,
    secondary = Color(0xFFE5D5C8),
    tertiary = Color(0xFFD9E6D6),
    background = Color(0xFFFFF7F1),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF3A302B),
    onSurfaceVariant = Color(0xFF84746B),
    outline = Color(0xFFE8D9CF)
)

fun colorSchemeFor(theme: String) = when (theme) {
    "밤" -> NightColorScheme
    "종이" -> PaperColorScheme
    else -> HaruColorScheme
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            val context = LocalContext.current
            var themeName by remember { mutableStateOf(loadTheme(context)) }
            MaterialTheme(colorScheme = colorSchemeFor(themeName)) {
                HaruPieceApp(
                    themeName = themeName,
                    onThemeChange = {
                        themeName = it
                        saveTheme(context, it)
                    }
                )
            }
        }
    }
}

data class Profile(
    val name: String,
    val gender: String,
    val age: String,
    val notifyTimes: List<String>,
    val topics: List<String>
)

data class DiaryEntry(
    val date: String,
    val time: String,
    val text: String,
    val kind: String,
    val photoUri: String? = null
)

data class AnswerOption(
    val label: String,
    val sentence: String
)

data class Question(
    val title: String,
    val options: List<AnswerOption>,
    val category: String = ""
)

@Composable
fun HaruPieceApp(themeName: String, onThemeChange: (String) -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(loadProfile(context)) }
    var launchDone by remember { mutableStateOf(false) }
    var topicFollowUpDismissed by remember { mutableStateOf(false) }
    val entries = remember { mutableStateListOf<DiaryEntry>().also { it.addAll(loadEntries(context)) } }
    val followUpTopics = remember(profile?.topics) { mutableStateListOf<String>().also { it.addAll(profile?.topics.orEmpty()) } }
    val recordedDays = entries.map { it.date }.toSet().size
    val shouldShowTopicFollowUp = profile != null && launchDone && profile!!.topics.isEmpty() && recordedDays >= 3 && !topicFollowUpDismissed

    LaunchedEffect(profile?.notifyTimes) {
        profile?.let { scheduleDiaryReminders(context, it.notifyTimes) }
    }

    if (profile == null) {
        OnboardingFlow { savedProfile ->
            profile = savedProfile
            saveProfile(context, savedProfile)
            scheduleDiaryReminders(context, savedProfile.notifyTimes)
            launchDone = true
        }
    } else if (!launchDone) {
        SplashScreen { launchDone = true }
    } else if (shouldShowTopicFollowUp) {
        TopicScreen(followUpTopics) {
            if (followUpTopics.isNotEmpty()) {
                val savedProfile = profile!!.copy(topics = followUpTopics.distinct())
                profile = savedProfile
                saveProfile(context, savedProfile)
            }
            topicFollowUpDismissed = true
        }
    } else {
        MainTabs(
            profile = profile!!,
            entries = entries,
            onSaveEntry = { entry ->
                entries.add(entry)
                saveEntries(context, entries)
            },
            onDeleteEntry = { entry ->
                entries.remove(entry)
                saveEntries(context, entries)
            },
            onSaveProfile = { savedProfile ->
                profile = savedProfile
                saveProfile(context, savedProfile)
                scheduleDiaryReminders(context, savedProfile.notifyTimes)
            },
            themeName = themeName,
            onThemeChange = onThemeChange,
            onReset = {
                scheduleDiaryReminders(context, emptyList())
                clearHaruPieceTestData(context)
                entries.clear()
                profile = null
                launchDone = false
                topicFollowUpDismissed = false
            }
        )
    }
}


@Composable
fun OnboardingFlow(onComplete: (Profile) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("여성") }
    var age by remember { mutableStateOf("") }
    val topics = remember { mutableStateListOf<String>() }
    val notifyTimes = remember { mutableStateListOf<String>() }

    when (step) {
        0 -> SplashScreen { step = 1 }
        1 -> IntroQuestionScreen { step = 2 }
        2 -> ProfileScreen(
            name = name,
            gender = gender,
            age = age,
            onNameChange = { name = it },
            onGenderChange = { gender = it },
            onAgeChange = { age = it },
            onNext = { step = 3 }
        )
        3 -> TopicScreen(
            selectedTopics = topics,
            onNext = { step = 4 }
        )
        4 -> NotificationScreen(
            notifyTimes = notifyTimes,
            onComplete = {
                onComplete(
                    Profile(
                        name = name.trim(),
                        gender = gender,
                        age = age.trim(),
                        notifyTimes = notifyTimes.toList(),
                        topics = topics.toList()
                    )
                )
            }
        )
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1700)
        onDone()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                PieceCluster()
                Text("하루조각", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("조각들이 모여 하루가 돼요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun IntroQuestionScreen(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { alpha.animateTo(1f, animationSpec = tween(720)) }
            launch { offsetY.animateTo(0f, animationSpec = tween(720)) }
        }
        delay(850)
        coroutineScope {
            launch { alpha.animateTo(0f, animationSpec = tween(620)) }
            launch { offsetY.animateTo(-14f, animationSpec = tween(620)) }
        }
        onDone()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "1분만 오늘을\n기록해볼까요?",
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha.value
                    translationY = offsetY.value
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2F2525),
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )
        }
    }
}

@Composable
fun OnboardingShell(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 23.sp)
            WhitePanel { content() }
        }
    }
}

@Composable
fun ProfileScreen(
    name: String,
    gender: String,
    age: String,
    onNameChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onNext: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    OnboardingShell("먼저, 가볍게", "질문을 조금 더 자연스럽게 건네기 위한 기본 정보예요.") {
        HaruTextField(name, onNameChange, "이름")
        Text("성별", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("여성", "남성", "선택 안 함").forEach { item ->
                PillButton(item, gender == item, Modifier.weight(1f)) { onGenderChange(item) }
            }
        }
        HaruTextField(
            value = age,
            onValueChange = { input -> onAgeChange(input.filter { it.isDigit() }.take(3)) },
            label = "나이",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
            })
        )
        PrimaryButton("다음", enabled = name.isNotBlank() && age.isNotBlank(), onClick = onNext)
    }
}

@Composable
fun TopicScreen(selectedTopics: MutableList<String>, onNext: () -> Unit) {
    val topicOptions = TopicOptions
    OnboardingShell("자주 남기고 싶은 것", "지금 고르지 않아도 괜찮아요. 나중에 다시 물어볼게요.") {
        Text("중복 선택 가능", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
        topicOptions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { topic ->
                    PillButton(topic, topic in selectedTopics, Modifier.weight(1f)) {
                        if (topic in selectedTopics) selectedTopics.remove(topic) else selectedTopics.add(topic)
                    }
                }
            }
        }
        PrimaryButton("다음", onClick = onNext)
        TextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("나중에 골라볼게요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NotificationScreen(notifyTimes: MutableList<String>, onComplete: () -> Unit) {
    var period by remember { mutableStateOf("오후") }
    var hour by remember { mutableStateOf(10) }
    var minute by remember { mutableStateOf(0) }
    val selectedDays = remember { mutableStateListOf("매일") }
    val selectedReminder = formatReminder(selectedDays, period, hour, minute)

    OnboardingShell("기록 알림 시간", "원하는 시간에 하루조각이 조용히 찾아갈게요.") {
        RepeatSelector(selectedDays)
        TimeWheelPicker(period, hour, minute, { period = it }, { hour = it }, { minute = it })
        PrimaryButton("추가") {
            if (selectedReminder !in notifyTimes) notifyTimes.add(selectedReminder)
        }
        if (notifyTimes.isEmpty()) {
            Text("아직 추가한 시간이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            notifyTimes.forEach { reminder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PeachSoft)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(normalizeReminderText(reminder), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("알림", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        PrimaryButton("하루조각 시작하기", enabled = notifyTimes.isNotEmpty(), onClick = onComplete)
    }
}
@Composable
fun MainTabs(
    profile: Profile,
    entries: List<DiaryEntry>,
    onSaveEntry: (DiaryEntry) -> Unit,
    onDeleteEntry: (DiaryEntry) -> Unit,
    onSaveProfile: (Profile) -> Unit,
    themeName: String,
    onThemeChange: (String) -> Unit,
    onReset: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("오늘") }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                "오늘" -> TodayScreen(profile, entries, onSaveEntry, onSaveProfile, onReset) { selectedTab = "달력" }
                "달력" -> CalendarScreen(entries, onDeleteEntry)
                "검색" -> SearchScreen(entries)
                "설정" -> SettingsScreen(profile, themeName, onSaveProfile, onThemeChange) { selectedTab = "달력" }
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            listOf("오늘", "달력", "검색", "설정").forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    icon = { Spacer(Modifier.size(0.dp)) },
                    label = { Text(tab) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.outline,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun TodayScreen(profile: Profile, entries: List<DiaryEntry>, onSaveEntry: (DiaryEntry) -> Unit, onSaveProfile: (Profile) -> Unit, onReset: () -> Unit, onMoveCalendar: () -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var mode by remember { mutableStateOf("start") }
    var questionIndex by remember { mutableStateOf(0) }
    val answers = remember { mutableStateListOf<String>() }
    var customInput by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var recordDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val aiQuestions = remember { mutableStateListOf<Question>() }
    var aiLoading by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectedPhotoUri = uri.toString()
        }
    }
    val questionLimit = when {
        entries.size >= 10 -> 4
        entries.size >= 6 -> 3
        entries.size >= 3 -> 2
        else -> 1
    }
    val localQuestions = buildQuestions(profile, questionLimit, recordDate, entries)
    val currentQuestion = aiQuestions.getOrNull(questionIndex) ?: localQuestions[questionIndex]

    fun submitCustomAnswer() {
        val input = customInput.trim()
        if (input.isBlank()) return
        val answer = sentenceFromCustomAnswer(input, currentQuestion)
        handleAnswer(answer, answers, questionIndex, questionLimit, { questionIndex = it }, { draft = it; mode = "review" })
        customInput = ""
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(mode, questionIndex, recordDate, answers.size) {
        if (mode == "question" && aiQuestions.getOrNull(questionIndex) == null && !aiLoading) {
            aiLoading = true
            val aiQuestion = fetchAiQuestion(profile, entries, recordDate, questionIndex + 1, answers.toList(), questionLimit)
            if (aiQuestion != null && shouldUseAiQuestion(aiQuestion, entries, recordDate, questionIndex + 1)) {
                while (aiQuestions.size <= questionIndex) aiQuestions.add(localQuestions[aiQuestions.size])
                aiQuestions[questionIndex] = aiQuestion
            }
            aiLoading = false
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("처음부터 다시 시작할까요?") },
            text = { Text("프로필과 기록이 지워지고, 첫 화면부터 다시 입력할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onReset()
                }) { Text("초기화", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("취소") }
            }
        )
    }

    AppScreen("하루조각", recordDate.format(DateFormatter)) {
        when (mode) {
            "start" -> WhitePanel {
                Text("오늘의 기억을 남겨볼까요?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, lineHeight = 36.sp)
TestDatePicker(recordDate, { recordDate = it })
                PrimaryButton("남겨볼게요") {
                    answers.clear()
                    questionIndex = 0
                    customInput = ""
                    selectedPhotoUri = null
                    aiQuestions.clear()
                    mode = "question"
                }
                OutlinedSoftButton("오늘은 쉴게요") {
                    onSaveEntry(newEntry("오늘은 아무것도 남기지 않고 싶은 하루였다.", "rest", recordDate))
                    mode = "restDone"
                }
                TextButton(onClick = { showResetConfirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("테스트 초기화", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            "question" -> WhitePanel {
                Text("${questionIndex + 1} / $questionLimit", color = CoralDark, fontWeight = FontWeight.Bold)
                Text(currentQuestion.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, lineHeight = 32.sp)
                currentQuestion.options.forEach { option ->
                    OutlinedSoftButton(option.label) { handleAnswer(option.sentence, answers, questionIndex, questionLimit, { questionIndex = it }, { draft = it; mode = "review" }) }
                }
                HaruTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = "기타(입력)",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitCustomAnswer() })
                )
                PrimaryButton("기타로 남기기", enabled = customInput.isNotBlank()) {
                    submitCustomAnswer()
                }
                if (questionIndex > 0) {
                    TextButton(onClick = { draft = makeDiaryText(answers); mode = "review" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("여기까지", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            "review" -> WhitePanel {
                Text("오늘의 조각", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                HaruTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "기록 문장",
                    modifier = Modifier.height(150.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    })
                )
                OutlinedSoftButton(if (selectedPhotoUri == null) "사진 추가" else "사진 바꾸기") {
                    photoPicker.launch(arrayOf("image/*"))
                }
                selectedPhotoUri?.let { uri ->
                    PhotoPreview(uri, Modifier.height(150.dp))
                }
                PrimaryButton("확인", enabled = draft.isNotBlank()) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onSaveEntry(newEntry(draft, "normal", recordDate, selectedPhotoUri))
                    mode = "done"
                }
            }
            "done" -> WhitePanel {
                LaunchedEffect(Unit) {
                    delay(900)
                    onMoveCalendar()
                }
                PieceCluster()
                Text("오늘도 한 조각이 쌓였어요.", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, lineHeight = 32.sp)
                Text("잠시 후 달력에서 오늘의 조각을 보여드릴게요.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                PrimaryButton("달력 보기") { onMoveCalendar() }
            }
            "restDone" -> WhitePanel {
                LaunchedEffect(Unit) {
                    delay(900)
                    onMoveCalendar()
                }
                PieceCluster()
                Text("오늘 하루 푹 쉬세요.", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, lineHeight = 32.sp)
                Text("쉬어가기로 한 마음도 오늘의 기록으로 남겨둘게요.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                PrimaryButton("달력 보기") { onMoveCalendar() }
            }
        }
    }
}


fun handleAnswer(
    answer: String,
    answers: MutableList<String>,
    questionIndex: Int,
    questionLimit: Int,
    moveQuestion: (Int) -> Unit,
    finish: (String) -> Unit
) {
    answers.add(answer)
    if (questionIndex + 1 >= questionLimit) finish(makeDiaryText(answers)) else moveQuestion(questionIndex + 1)
}

@Composable
fun AppScreen(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniPieceCluster()
                Column {
                    Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            content()
        }
    }
}

@Composable
fun WhitePanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun CalendarScreen(entries: List<DiaryEntry>, onDeleteEntry: (DiaryEntry) -> Unit) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    val selectedKey = selectedDate.format(DateFormatter)
    val byDate = entries.groupBy { it.date }

    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("${entry.date} ${entry.time}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    entry.photoUri?.let { PhotoPreview(it, Modifier.height(190.dp)) }
                    Text(entry.text, lineHeight = 24.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEntry = null }) {
                    Text("닫기")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDeleteEntry(entry)
                    selectedEntry = null
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                }
            }
        )
    }

    AppScreen("달력", "조각이 남은 날은 은은하게 표시돼요.") {
        WhitePanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val previousMonth = selectedMonth.minusMonths(1)
                    selectedMonth = previousMonth
                    selectedDate = previousMonth.atDay(1)
                }) { Text("<", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${selectedMonth.year}년 ${selectedMonth.monthValue}월", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = {
                    val nextMonth = selectedMonth.plusMonths(1)
                    selectedMonth = nextMonth
                    selectedDate = nextMonth.atDay(1)
                }) { Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            CalendarGrid(selectedMonth, byDate, selectedDate) { selectedDate = it }
        }
        WhitePanel {
            val selectedHoliday = holidayName(selectedDate)
            Text("$selectedKey 기록", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (selectedHoliday != null) {
                Text(
                    selectedHoliday,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            val dayEntries = byDate[selectedKey].orEmpty()
            if (dayEntries.isEmpty()) {
                Text("아직 남겨진 조각이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
            } else {
                dayEntries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (entry.kind == "rest") Lavender.copy(alpha = 0.55f) else PeachSoft.copy(alpha = 0.72f))
                                .clickable { selectedEntry = entry }
                                .padding(14.dp)
                        ) {
                            Text(entry.time, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (entry.photoUri != null) Text("사진이 함께 남았어요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Text(entry.text, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        GhostTrashButton { onDeleteEntry(entry) }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarGrid(month: YearMonth, byDate: Map<String, List<DiaryEntry>>, selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    val cells = List(offset) { 0 } + (1..month.lengthOfMonth()).toList()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    if (day == 0) {
                        Spacer(modifier = Modifier.weight(1f).height(42.dp))
                    } else {
                        val date = month.atDay(day)
                        val dayEntries = byDate[date.format(DateFormatter)].orEmpty()
                        val hasRest = dayEntries.any { it.kind == "rest" }
                        val fill = when {
                            date == selectedDate -> Peach
                            dayEntries.isEmpty() -> Color(0xFFFFF8F8)
                            hasRest -> Lavender
                            else -> PeachSoft
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(fill)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (holidayName(date) != null) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFE45C5C)))
                                } else {
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(day.toString(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f).height(42.dp)) }
            }
        }
    }
}

fun holidayName(date: LocalDate): String? {
    val fixed = when (date.monthValue to date.dayOfMonth) {
        1 to 1 -> "신정"
        3 to 1 -> "삼일절"
        5 to 5 -> "어린이날"
        6 to 6 -> "현충일"
        8 to 15 -> "광복절"
        10 to 3 -> "개천절"
        10 to 9 -> "한글날"
        12 to 25 -> "성탄절"
        else -> null
    }
    if (fixed != null) return fixed

    return koreanHolidayOverrides()[date]
}

fun koreanHolidayOverrides(): Map<LocalDate, String> = mapOf(
    LocalDate.of(2026, 2, 16) to "설날",
    LocalDate.of(2026, 2, 17) to "설날",
    LocalDate.of(2026, 2, 18) to "설날",
    LocalDate.of(2026, 3, 2) to "삼일절 대체공휴일",
    LocalDate.of(2026, 5, 24) to "부처님오신날",
    LocalDate.of(2026, 8, 17) to "광복절 대체공휴일",
    LocalDate.of(2026, 9, 24) to "추석",
    LocalDate.of(2026, 9, 25) to "추석",
    LocalDate.of(2026, 9, 26) to "추석",
    LocalDate.of(2026, 10, 5) to "개천절 대체공휴일",
    LocalDate.of(2027, 2, 6) to "설날",
    LocalDate.of(2027, 2, 7) to "설날",
    LocalDate.of(2027, 2, 8) to "설날",
    LocalDate.of(2027, 2, 9) to "설날 대체공휴일",
    LocalDate.of(2027, 5, 13) to "부처님오신날",
    LocalDate.of(2027, 8, 16) to "광복절 대체공휴일",
    LocalDate.of(2027, 9, 14) to "추석",
    LocalDate.of(2027, 9, 15) to "추석",
    LocalDate.of(2027, 9, 16) to "추석",
    LocalDate.of(2027, 10, 4) to "개천절 대체공휴일",
    LocalDate.of(2027, 10, 11) to "한글날 대체공휴일"
)
@Composable
fun SearchScreen(entries: List<DiaryEntry>) {
    var query by remember { mutableStateOf("") }
    val results = entries.filter { query.isNotBlank() && it.text.contains(query, ignoreCase = true) }

    AppScreen("검색", "기억나는 단어 하나면 충분해요.") {
        SearchTextField(query, { query = it })
        if (query.isBlank()) {
            Spacer(modifier = Modifier.height(1.dp))
        } else if (results.isEmpty()) {
            WhitePanel { Text("아직 찾은 조각이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            results.forEach { entry ->
                WhitePanel {
                    Text(entry.date, fontWeight = FontWeight.Bold, color = CoralDark)
                    Text(entry.text, color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp)
                }
            }
        }
    }
}

@Composable
fun SearchTextField(value: String, onValueChange: (String) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val finishSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("찾고 싶은 단어") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finishSearch() }),
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    .clickable { finishSearch() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val color = Color(0x993B3030)
                    val stroke = Stroke(width = 1.8f)
                    drawCircle(color = color, radius = size.minDimension * 0.32f, style = stroke)
                    drawLine(
                        color,
                        Offset(size.width * 0.68f, size.height * 0.68f),
                        Offset(size.width * 0.9f, size.height * 0.9f),
                        strokeWidth = 1.8f
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun SettingsScreen(
    profile: Profile,
    themeName: String,
    onSaveProfile: (Profile) -> Unit,
    onThemeChange: (String) -> Unit,
    onMoveCalendar: () -> Unit
) {
    val context = LocalContext.current
    var section by remember { mutableStateOf("menu") }
    var period by remember { mutableStateOf("오전") }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(30) }
    val selectedDays = remember { mutableStateListOf("매일") }
    val selectedReminder = formatReminder(selectedDays, period, hour, minute)
    val notifyTimes = remember(profile) {
        mutableStateListOf<String>().also { list ->
            list.addAll(profile.notifyTimes.map(::normalizeReminderText).distinct())
        }
    }

    when (section) {
        "alarm" -> AppScreen("알람", "기록할 시간을 조용히 정해둘게요.") {
            WhitePanel {
                TextButton(onClick = { section = "menu" }) { Text("설정으로") }
                RepeatSelector(selectedDays)
                TimeWheelPicker(period, hour, minute, { period = it }, { hour = it }, { minute = it })
                PrimaryButton("추가") {
                    if (selectedReminder !in notifyTimes) notifyTimes.add(selectedReminder)
                }
                if (notifyTimes.isEmpty()) Text("아직 추가한 시간이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                notifyTimes.forEach { reminder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(PeachSoft.copy(alpha = 0.72f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(normalizeReminderText(reminder), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { notifyTimes.remove(reminder) }) {
                            Text("삭제", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                }
                PrimaryButton("저장") {
                    onSaveProfile(profile.copy(notifyTimes = notifyTimes.map(::normalizeReminderText).distinct()))
                    onMoveCalendar()
                }
                OutlinedSoftButton("알림 테스트") { postDiaryReminderNow(context) }
            }
        }
        "theme" -> AppScreen("분위기", "오늘 보기 좋은 화면 톤을 골라주세요.") {
            WhitePanel {
                TextButton(onClick = { section = "menu" }) { Text("설정으로") }
                listOf(
                    "기본" to "포근한 살구빛 기본 화면",
                    "밤" to "어두운 배경에 조용한 색감",
                    "종이" to "밝고 차분한 종이 느낌"
                ).forEach { (name, desc) ->
                    SettingRow(
                        title = name,
                        subtitle = if (themeName == name) "$desc · 선택됨" else desc,
                        onClick = { onThemeChange(name) }
                    )
                }
            }
        }
        else -> AppScreen("설정", "${profile.name}님의 하루조각") {
            WhitePanel {
                SettingRow("알람", "${profile.notifyTimes.size}개의 기록 알림", onClick = { section = "alarm" })
                SettingRow("분위기", themeName, onClick = { section = "theme" })
            }
        }
    }
}
@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PeachSoft.copy(alpha = 0.58f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TimeWheelPicker(
    period: String,
    hour: Int,
    minute: Int,
    onPeriodChange: (String) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("시간 선택", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF3C3335))
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScrollWheelColumn(
                values = listOf("오전", "오후"),
                selected = period,
                onSelected = onPeriodChange,
                modifier = Modifier.weight(1f)
            )
            ScrollWheelColumn(
                values = (1..12).map { it.toString().padStart(2, '0') },
                selected = hour.toString().padStart(2, '0'),
                onSelected = { onHourChange(it.toInt()) },
                modifier = Modifier.weight(1f)
            )
            ScrollWheelColumn(
                values = (0..59).map { it.toString().padStart(2, '0') },
                selected = minute.toString().padStart(2, '0'),
                onSelected = { onMinuteChange(it.toInt()) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun RepeatSelector(selectedDays: MutableList<String>) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("반복", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(PeachSoft.copy(alpha = 0.72f))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("반복", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayReminderDays(selectedDays), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("반복") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ReminderDayOptions.forEach { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val next = toggleReminderDay(selectedDays, day)
                                    selectedDays.clear()
                                    selectedDays.addAll(next)
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(reminderOptionLabel(day), color = MaterialTheme.colorScheme.onSurface)
                            if (day in selectedDays) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text("완료") }
            }
        )
    }
}

@Composable
fun ScrollWheelColumn(
    values: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val paddedValues = listOf("") + values + listOf("")
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { firstIndex ->
            val valueIndex = firstIndex.coerceIn(0, values.lastIndex)
            val value = values[valueIndex]
            if (value != selected) onSelected(value)
        }
    }

    LaunchedEffect(selected) {
        val target = values.indexOf(selected).coerceAtLeast(0)
        if (state.firstVisibleItemIndex != target) state.animateScrollToItem(target)
    }

    Box(modifier = modifier.height(132.dp)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(paddedValues) { index, value ->
                val valueIndex = index - 1
                val isSelected = valueIndex == state.firstVisibleItemIndex && value.isNotBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF665B5E) else Color.Transparent)
                        .clickable(enabled = value.isNotBlank()) { onSelected(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        value,
                        color = if (isSelected) Color.White else Color(0xFFBFAFB2),
                        fontSize = if (isSelected) 18.sp else 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

fun formatReminder(days: List<String>, period: String, hour: Int, minute: Int): String {
    return "${displayReminderDays(days)} ${formatTime24(period, hour, minute)}"
}

fun displayReminderDays(days: List<String>): String {
    return when {
        "안 함" in days -> "안 함"
        "매일" in days -> "매일"
        "평일" in days -> "평일"
        "주말" in days -> "주말"
        else -> days.filter { it in listOf("일", "월", "화", "수", "목", "금", "토") }.ifEmpty { listOf("매일") }.joinToString(",")
    }
}

fun reminderOptionLabel(day: String): String {
    return when (day) {
        "일" -> "일요일마다"
        "월" -> "월요일마다"
        "화" -> "화요일마다"
        "수" -> "수요일마다"
        "목" -> "목요일마다"
        "금" -> "금요일마다"
        "토" -> "토요일마다"
        else -> day
    }
}


fun toggleReminderDay(current: List<String>, day: String): List<String> {
    if (day in listOf("안 함", "매일", "평일", "주말")) return listOf(day)
    val next = current.filter { it !in listOf("안 함", "매일", "평일", "주말") }.toMutableList()
    if (day in next) next.remove(day) else next.add(day)
    return next.ifEmpty { listOf("안 함") }
}


fun formatTime24(period: String, hour: Int, minute: Int): String {
    val hour24 = when {
        period == "오전" && hour == 12 -> 0
        period == "오후" && hour != 12 -> hour + 12
        else -> hour
    }
    return hour24.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

fun normalizeReminderText(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return "매일 22:00"
    val time = normalizeTimePart(text)
    val days = when {
        text.contains("안 함") -> "안 함"
        text.contains("매일") -> "매일"
        text.contains("평일") -> "평일"
        text.contains("주말") -> "주말"
        else -> {
            val picked = listOf("일", "월", "화", "수", "목", "금", "토").filter { text.contains(it) }
            picked.ifEmpty { listOf("매일") }.joinToString(",")
        }
    }
    return "$days $time"
}

fun normalizeTimePart(raw: String): String {
    Regex("(오전|오후)\\s*(\\d{1,2}):(\\d{1,2})").find(raw)?.let { match ->
        val period = match.groupValues[1]
        val hour = match.groupValues[2].toInt()
        val minute = match.groupValues[3].toInt()
        return formatTime24(period, hour, minute)
    }
    Regex("(\\d{1,2}):(\\d{1,2})").find(raw)?.let { match ->
        val hour = match.groupValues[1].toInt().coerceIn(0, 23)
        val minute = match.groupValues[2].toInt().coerceIn(0, 59)
        return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
    }
    Regex("\\b(\\d{3,4})\\b").find(raw)?.let { match ->
        val value = match.groupValues[1].padStart(4, '0')
        val hour = value.take(2).toInt().coerceIn(0, 23)
        val minute = value.takeLast(2).toInt().coerceIn(0, 59)
        return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
    }
    return "22:00"
}

@Composable
fun TestDatePicker(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PeachSoft.copy(alpha = 0.65f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("테스트 날짜", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onDateChange(date.minusDays(1)) }) { Text("이전") }
            Text(date.format(DateFormatter), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onDateChange(date.plusDays(1)) }) { Text("다음") }
        }
        TextButton(onClick = { onDateChange(LocalDate.now()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("오늘로")
        }
    }
}

@Composable
fun PhotoPreview(uri: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { imageView ->
            imageView.setImageURI(Uri.parse(uri))
        }
    )
}

@Composable
fun GhostTrashButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val color = Color(0x993B3030)
            val stroke = Stroke(width = 1.8f)
            drawLine(color, Offset(size.width * 0.28f, size.height * 0.28f), Offset(size.width * 0.72f, size.height * 0.28f), strokeWidth = 1.8f)
            drawLine(color, Offset(size.width * 0.42f, size.height * 0.18f), Offset(size.width * 0.58f, size.height * 0.18f), strokeWidth = 1.8f)
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.32f, size.height * 0.36f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.36f, size.height * 0.45f),
                style = stroke,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f, 2.5f)
            )
        }
    }
}

@Composable
fun HaruTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions()
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        delay(120)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(22.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, disabledContainerColor = MaterialTheme.colorScheme.outline)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun OutlinedSoftButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper, contentColor = Ink)
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
fun PillButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) Coral else Paper
    val fg = if (selected) Color.White else Ink
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun PieceCluster() {
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Peach))
        Box(Modifier.padding(start = 48.dp, bottom = 42.dp).size(44.dp).clip(RoundedCornerShape(15.dp)).background(Lavender))
        Box(Modifier.padding(end = 54.dp, top = 42.dp).size(38.dp).clip(RoundedCornerShape(13.dp)).background(Mint))
        Box(Modifier.padding(start = 22.dp, top = 62.dp).size(28.dp).clip(CircleShape).background(Sky))
    }
}

@Composable
fun MiniPieceCluster() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(Peach))
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(Lavender))
        Box(Modifier.size(12.dp).clip(CircleShape).background(Mint))
    }
}

suspend fun fetchAiQuestion(
    profile: Profile,
    entries: List<DiaryEntry>,
    recordDate: LocalDate,
    step: Int,
    previousAnswers: List<String>,
    questionLimit: Int
): Question? = withTimeoutOrNull(15000) {
    withContext(Dispatchers.IO) {
        runCatching {
            Log.i(AI_LOG_TAG, "AI request start: step=$step, records=${entries.size}")
            val payload = JSONObject()
                .put("appDay", entries.map { it.date }.toSet().size + 1)
                .put("recordCount", entries.size)
                .put("recordDate", recordDate.format(DateFormatter))
                .put("notificationTime", LocalTime.now().format(TimeFormatter))
                .put("currentStep", step)
                .put("questionLimit", questionLimit)
                .put("rotationSeed", "${recordDate.dayOfYear}-${entries.size}-$step")
                .put("weekday", recordDate.dayOfWeek.toString())
                .put("avoidCategories", JSONArray(inferRecentCategories(entries)))
                .put("questionStyle", "초반은 식사, 수면, 컨디션, 오늘 한 일처럼 가볍고 사실적인 질문을 우선한다. 직전 소재와 같은 카테고리는 가능하면 피한다.")
                .put("profile", JSONObject()
                    .put("age", profile.age)
                    .put("gender", profile.gender)
                    .put("topics", JSONArray(profile.topics))
                )
                .put("previousAnswers", JSONArray(previousAnswers))
                .put("recentEntries", JSONArray(entries.takeLast(3).map { it.text }))
            Log.i(AI_LOG_TAG, "AI payload=${payload.toString().take(220)}")

            val connection = (URL(QUESTION_FUNCTION_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val responseText = if (status in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            Log.i(AI_LOG_TAG, "AI response status=$status body=${responseText.take(220)}")
            connection.disconnect()

            if (responseText.isBlank()) return@runCatching null
            val wrapper = JSONObject(responseText)
            val raw = wrapper.optString("raw")
            Log.i(AI_LOG_TAG, "AI raw=${raw.take(220)}")
            parseAiQuestion(raw)
        }.onFailure { Log.w(AI_LOG_TAG, "AI request failed", it) }.getOrNull()
    }
}

fun parseAiQuestion(raw: String): Question? {
    val jsonText = raw
        .replace("```json", "")
        .replace("```", "")
        .trim()
    if (jsonText.isBlank()) return null

    return runCatching {
        val json = JSONObject(jsonText)
        val optionsJson = json.getJSONArray("options")
        val options = List(optionsJson.length()) { index ->
            val option = optionsJson.getJSONObject(index)
            AnswerOption(
                label = option.optString("label"),
                sentence = option.optString("sentence")
            )
        }.filter { it.label.isNotBlank() && it.sentence.isNotBlank() }

        if (options.isEmpty()) null else Question(
            title = json.getString("question"),
            options = options,
            category = json.optString("category").ifBlank { inferQuestionCategory(json.getString("question") + " " + options.joinToString(" ") { it.label }) }
        )
    }.getOrNull()
}
fun inferRecentCategories(entries: List<DiaryEntry>): List<String> {
    val recentText = entries.takeLast(3).joinToString(" ") { it.text }
    val categories = mutableListOf<String>()
    fun addIf(category: String, vararg keywords: String) {
        if (keywords.any { recentText.contains(it) }) categories.add(category)
    }
    addIf("식사", "점심", "저녁", "아침", "식사", "먹")
    addIf("수면", "잠", "잤", "설쳤", "늦게")
    addIf("컨디션", "피곤", "몸", "컨디션", "아팠")
    addIf("일/학교", "회사", "일", "업무", "학교", "공부")
    addIf("사람", "친구", "가족", "동료", "사람", "대화")
    addIf("이동", "이동", "버스", "지하철", "운전", "길")
    addIf("운동", "운동", "산책", "헬스", "뛰")
    addIf("소비", "샀", "구매", "돈", "결제")
    return categories.distinct().takeLast(4)
}

fun inferQuestionCategory(text: String): String {
    return when {
        listOf("식사", "밥", "점심", "저녁", "아침", "먹", "메뉴").any { text.contains(it) } -> "식사"
        listOf("잠", "수면", "잤", "설쳤", "늦게").any { text.contains(it) } -> "수면"
        listOf("몸", "컨디션", "피곤", "아팠", "상태").any { text.contains(it) } -> "컨디션"
        listOf("날씨", "비", "맑", "흐", "눈", "더웠", "추웠").any { text.contains(it) } -> "날씨"
        listOf("기분", "좋", "나빴", "감정").any { text.contains(it) } -> "기분"
        listOf("운동", "산책", "헬스", "뛰").any { text.contains(it) } -> "운동"
        listOf("회사", "업무", "학교", "공부", "일").any { text.contains(it) } -> "일/학교"
        else -> "오늘 한 일"
    }
}

fun shouldUseAiQuestion(question: Question, entries: List<DiaryEntry>, recordDate: LocalDate, step: Int): Boolean {
    if (step != 1) return true
    val category = question.category.ifBlank { inferQuestionCategory(question.title) }
    val todayCategories = inferRecentCategories(entries.filter { it.date == recordDate.format(DateFormatter) }).toSet()
    return category !in todayCategories
}
fun buildQuestions(profile: Profile, limit: Int, date: LocalDate = LocalDate.now(), entries: List<DiaryEntry> = emptyList()): List<Question> {
    val firstQuestions = listOf(
        Question(
            "오늘 식사는 어떻게 챙기셨나요?",
            listOf(
                AnswerOption("든든하게 먹었어요", "오늘은 식사를 든든하게 챙겨 먹었다."),
                AnswerOption("간단히 해결했어요", "오늘은 식사를 간단하게 해결했다."),
                AnswerOption("거의 못 챙겼어요", "오늘은 식사를 제대로 챙기지 못했다."),
                AnswerOption("평소와 비슷했어요", "오늘 식사는 평소와 비슷했다.")
            ),
            "식사"
        ),
        Question(
            "오늘 몸 상태는 어땠나요?",
            listOf(
                AnswerOption("괜찮았어요", "오늘은 몸 상태가 괜찮았다."),
                AnswerOption("조금 피곤했어요", "오늘은 몸이 조금 피곤했다."),
                AnswerOption("무거웠어요", "오늘은 몸이 무겁게 느껴졌다."),
                AnswerOption("평소 같았어요", "오늘 몸 상태는 평소와 비슷했다.")
            ),
            "컨디션"
        ),
        Question(
            "오늘 한 일 중 하나만 고르면 무엇인가요?",
            listOf(
                AnswerOption("일이나 공부", "오늘은 일이나 공부를 했다."),
                AnswerOption("집안일", "오늘은 집안일을 했다."),
                AnswerOption("이동", "오늘은 이동하는 시간이 있었다."),
                AnswerOption("휴식", "오늘은 쉬는 시간을 가졌다.")
            ),
            "오늘 한 일"
        ),
        Question(
            "오늘 날씨는 어떻게 느껴졌나요?",
            listOf(
                AnswerOption("맑았어요", "오늘은 날씨가 맑았다."),
                AnswerOption("흐렸어요", "오늘은 날씨가 흐렸다."),
                AnswerOption("비가 왔어요", "오늘은 비가 왔다."),
                AnswerOption("잘 모르겠어요", "오늘은 날씨를 크게 신경 쓰지 못했다.")
            ),
            "날씨"
        ),
        Question(
            "오늘 잠은 어땠나요?",
            listOf(
                AnswerOption("잘 잤어요", "오늘은 잠을 잘 잤다."),
                AnswerOption("조금 설쳤어요", "오늘은 잠을 조금 설쳤다."),
                AnswerOption("늦게 잤어요", "오늘은 늦게 잤다."),
                AnswerOption("평소 같았어요", "오늘 잠은 평소와 비슷했다.")
            ),
            "수면"
        )
    )

    val todayCategories = inferRecentCategories(entries.filter { it.date == date.format(DateFormatter) }).toSet()
    val seed = date.dayOfYear + entries.count { it.date == date.format(DateFormatter) }
    val rotated = firstQuestions.drop(seed % firstQuestions.size) + firstQuestions.take(seed % firstQuestions.size)
    val first = rotated.firstOrNull { it.category !in todayCategories } ?: rotated.first()

    return listOf(
        first,
        reasonQuestionFor(first.category),
        patternQuestionFor(first.category),
        nextFocusQuestionFor(first.category)
    ).take(limit)
}

fun reasonQuestionFor(category: String): Question {
    return when (category) {
        "식사" -> Question(
            "식사를 그렇게 챙긴 이유는 무엇에 가까웠나요?",
            listOf(
                AnswerOption("시간이 없어서", "시간이 없어서 식사를 그렇게 챙겼다."),
                AnswerOption("먹고 싶은 게 있어서", "먹고 싶은 음식이 있어서 그렇게 먹었다."),
                AnswerOption("입맛이 없어서", "입맛이 없어 식사를 가볍게 챙겼다."),
                AnswerOption("평소처럼", "평소처럼 식사를 챙겼다.")
            ),
            "식사"
        )
        "컨디션" -> Question(
            "몸 상태가 그렇게 느껴진 이유는 무엇 같나요?",
            listOf(
                AnswerOption("잠 때문", "잠의 영향으로 몸 상태가 그렇게 느껴졌다."),
                AnswerOption("일정 때문", "일정의 영향으로 몸 상태가 그렇게 느껴졌다."),
                AnswerOption("운동이나 활동 때문", "활동량의 영향으로 몸 상태가 그렇게 느껴졌다."),
                AnswerOption("잘 모르겠어요", "몸 상태가 왜 그랬는지는 잘 모르겠다.")
            ),
            "컨디션"
        )
        "날씨" -> Question(
            "그 날씨가 오늘 하루에 영향을 줬나요?",
            listOf(
                AnswerOption("조금 있었어요", "오늘 날씨가 하루에 조금 영향을 줬다."),
                AnswerOption("크게 있었어요", "오늘 날씨가 하루에 꽤 영향을 줬다."),
                AnswerOption("거의 없었어요", "오늘 날씨는 하루에 큰 영향을 주지 않았다."),
                AnswerOption("잘 모르겠어요", "날씨가 하루에 영향을 줬는지는 잘 모르겠다.")
            ),
            "날씨"
        )
        "수면" -> Question(
            "잠이 그렇게 된 이유는 무엇에 가까웠나요?",
            listOf(
                AnswerOption("늦게 누워서", "늦게 누워서 잠이 그렇게 됐다."),
                AnswerOption("생각이 많아서", "생각이 많아서 잠이 편하지 않았다."),
                AnswerOption("몸이 피곤해서", "몸이 피곤해서 잠의 영향이 있었다."),
                AnswerOption("평소처럼", "잠은 평소와 비슷했다.")
            ),
            "수면"
        )
        else -> Question(
            "그 일을 하게 된 이유는 무엇에 가까웠나요?",
            listOf(
                AnswerOption("해야 해서", "해야 할 일이어서 했다."),
                AnswerOption("미뤄둔 일이라서", "미뤄둔 일을 처리했다."),
                AnswerOption("하고 싶어서", "하고 싶어서 한 일이었다."),
                AnswerOption("자연스럽게", "특별한 이유 없이 자연스럽게 하게 됐다.")
            ),
            category
        )
    }
}

fun patternQuestionFor(category: String): Question {
    val target = when (category) {
        "식사" -> "식사를 이렇게 챙기는 날"
        "컨디션" -> "몸 상태가 이런 날"
        "날씨" -> "날씨가 하루에 영향을 주는 날"
        "수면" -> "잠이 이런 날"
        else -> "이런 일을 하는 날"
    }
    return Question(
        "요즘 ${target}이 자주 있나요?",
        listOf(
            AnswerOption("자주 있어요", "요즘 ${target}이 자주 있다."),
            AnswerOption("가끔 있어요", "가끔 ${target}이 있다."),
            AnswerOption("드물어요", "${target}은 드문 편이다."),
            AnswerOption("잘 모르겠어요", "${target}이 자주 있는지는 아직 잘 모르겠다.")
        ),
        category
    )
}

fun nextFocusQuestionFor(category: String): Question {
    return Question(
        "다음에 비슷한 날이면 무엇을 더 남겨볼까요?",
        listOf(
            AnswerOption("이유", "다음에는 이유를 조금 더 남겨보고 싶다."),
            AnswerOption("시간", "다음에는 시간을 조금 더 남겨보고 싶다."),
            AnswerOption("상황", "다음에는 상황을 조금 더 남겨보고 싶다."),
            AnswerOption("그냥 한 줄만", "다음에도 한 줄 정도만 가볍게 남기고 싶다.")
        ),
        category
    )
}

fun sentenceFromCustomAnswer(answer: String, question: Question): String {
    val category = question.category.ifBlank { inferQuestionCategory(question.title) }
    val phrase = normalizeKoreanDiaryPhrase(answer)
    if (looksLikeCompleteSentence(phrase)) return phrase.ensurePeriod()

    return when (category) {
        "식사" -> {
            if (listOf("먹", "마셨", "마시", "챙", "해결").any { phrase.contains(it) }) {
                "오늘은 $phrase."
            } else {
                "오늘은 ${phrase.withObjectParticle()} 먹었다."
            }
        }
        "컨디션" -> if (phrase.contains("몸") || phrase.contains("컨디션")) "오늘은 $phrase." else "오늘은 컨디션이 $phrase."
        "날씨" -> if (phrase.contains("날씨")) "오늘은 $phrase." else "오늘은 날씨가 $phrase."
        "기분" -> if (phrase.contains("기분")) "오늘은 $phrase." else "오늘은 기분이 $phrase."
        "수면" -> if (phrase.contains("잠")) "오늘은 $phrase." else "오늘은 잠이 $phrase."
        else -> "오늘은 $phrase."
    }.ensurePeriod()
}

fun normalizeKoreanDiaryPhrase(raw: String): String {
    val text = raw.trim().trimEnd('.', '!', '?')
    fun replaceEnding(suffix: String, replacement: String): String? {
        return if (text.endsWith(suffix)) text.dropLast(suffix.length) + replacement else null
    }
    return replaceEnding("이었어요", "이었다")
        ?: replaceEnding("였어요", "였다")
        ?: replaceEnding("했어요", "했다")
        ?: replaceEnding("됐어요", "됐다")
        ?: replaceEnding("었어요", "었다")
        ?: replaceEnding("았어요", "았다")
        ?: replaceEnding("예요", "이다")
        ?: replaceEnding("이에요", "이다")
        ?: replaceEnding("어요", "었다")
        ?: replaceEnding("아요", "았다")
        ?: if (text.endsWith("요")) text.dropLast(1) else text
}

fun String.withObjectParticle(): String {
    val value = trim()
    if (value.isBlank()) return value
    val last = value.last()
    val hasBatchim = last in '가'..'힣' && ((last.code - '가'.code) % 28 != 0)
    return value + if (hasBatchim) "을" else "를"
}

fun makeDiaryText(answers: List<String>): String {
    if (answers.isEmpty()) return "오늘은 조용히 지나간 하루였다."
    val cleaned = answers.map { it.trim() }.filter { it.isNotBlank() }
    val first = cleaned.first()
    val tail = cleaned.drop(1)
    return buildString {
        if (looksLikeCompleteSentence(first)) {
            append(first.ensurePeriod())
        } else {
            append("오늘은 ").append(first.trimEnd('.', '다')).append(" 하루였다.")
        }
        tail.forEach { sentence ->
            append(" ").append(sentence.ensurePeriod())
        }
    }
}

fun looksLikeCompleteSentence(value: String): Boolean {
    val text = value.trim().trimEnd('.', '!', '?')
    return text.endsWith("다") || text.endsWith("했다") || text.endsWith("먹었다") || text.endsWith("보냈다")
}


fun String.ensurePeriod(): String {
    val value = trim()
    return if (value.endsWith(".") || value.endsWith("!") || value.endsWith("?")) value else "$value."
}

fun newEntry(text: String, kind: String, date: LocalDate = LocalDate.now(), photoUri: String? = null): DiaryEntry {
    return DiaryEntry(date.format(DateFormatter), LocalTime.now().format(TimeFormatter), text, kind, photoUri)
}

fun loadProfile(context: Context): Profile? {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("profile", null) ?: return null
    val json = JSONObject(raw)
    val topicsJson = json.optJSONArray("topics") ?: JSONArray()
    val timesJson = json.optJSONArray("notifyTimes") ?: JSONArray()
    return Profile(
        name = json.optString("name"),
        gender = json.optString("gender"),
        age = json.optString("age"),
        notifyTimes = List(timesJson.length()) { timesJson.optString(it) }.ifEmpty { listOf(json.optString("notifyTime", "22:00")) }.map(::normalizeReminderText).distinct(),
        topics = List(topicsJson.length()) { topicsJson.optString(it) }
    )
}

fun loadTheme(context: Context): String {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("theme", "기본") ?: "기본"
}

fun saveTheme(context: Context, theme: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("theme", theme).apply()
}

fun clearHaruPieceTestData(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .remove("profile")
        .remove("entries")
        .apply()
}

fun saveProfile(context: Context, profile: Profile) {
    val json = JSONObject()
        .put("name", profile.name)
        .put("gender", profile.gender)
        .put("age", profile.age)
        .put("notifyTimes", JSONArray(profile.notifyTimes))
        .put("topics", JSONArray(profile.topics))
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("profile", json.toString()).apply()
}

fun loadEntries(context: Context): List<DiaryEntry> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("entries", "[]") ?: "[]"
    val array = JSONArray(raw)
    return List(array.length()) { index ->
        val json = array.getJSONObject(index)
        DiaryEntry(json.optString("date"), json.optString("time"), json.optString("text"), json.optString("kind", "normal"), json.optString("photoUri").takeIf { it.isNotBlank() })
    }
}

fun saveEntries(context: Context, entries: List<DiaryEntry>) {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(JSONObject().put("date", entry.date).put("time", entry.time).put("text", entry.text).put("kind", entry.kind).put("photoUri", entry.photoUri ?: ""))
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("entries", array.toString()).apply()
}





