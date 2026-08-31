package com.example.diaryapp

object DiarySentenceEngine {
    fun fromCustomAnswer(rawAnswer: String, question: Question): String {
        val input = cleanInput(rawAnswer)
        if (input.isBlank()) return "오늘은 조용히 지나간 하루였다."

        val phrase = normalizePoliteEnding(input)
        if (looksComplete(phrase)) return polish(withTodayPrefixIfNeeded(phrase))

        val category = detectCategory(phrase, question)
        val sentence = when (category) {
            "food" -> foodSentence(phrase)
            "drink" -> drinkSentence(phrase)
            "sleep" -> sleepSentence(phrase)
            "weather" -> weatherSentence(phrase)
            "mood" -> stateSentence("기분", phrase)
            "condition" -> stateSentence("컨디션", phrase)
            "movement" -> movementSentence(phrase)
            "spending" -> spendingSentence(phrase)
            "work" -> workSentence(phrase)
            else -> activitySentence(phrase)
        }
        return polish(sentence)
    }

    fun combine(answers: List<String>): String {
        val cleaned = answers.map { polish(it) }.filter { it.isNotBlank() }
        return if (cleaned.isEmpty()) "오늘은 조용히 지나간 하루였다." else cleaned.joinToString(" ") { it.ensurePeriod() }
    }

    fun polish(raw: String): String {
        var text = raw.trim().trimEnd('.', '!', '?')
            .replace(Regex("\\s+"), " ")
            .replace(Regex("오늘은\\s+오늘은"), "오늘은")
            .replace("오늘은 나는", "나는")
            .replace("오늘은 자를 했다", "오늘은 잠을 잤다")
            .replace("오늘은 자기 했다", "오늘은 잠을 잤다")
            .replace("오늘은 자기를 했다", "오늘은 잠을 잤다")
            .replace("오늘은 잠자기를 했다", "오늘은 잠을 잤다")
            .replace("오늘은 잠자기를 먹었다", "오늘은 잠을 잤다")
            .replace("오늘은 날씨가 좋아 하루였다", "오늘은 날씨가 좋았다")
            .replace("오늘은 기분이 좋아 하루였다", "오늘은 기분이 좋았다")
            .replace("오늘은 컨디션이 힘들어 하루였다", "오늘은 컨디션이 좋지 않았다")
            .replace("좋아 하루였다", "좋은 하루였다")
            .replace("힘들어 하루였다", "힘든 하루였다")
            .replace("피곤해 하루였다", "피곤한 하루였다")

        text = text.replace(Regex("오늘은\\s+(.+?)\\s+하루였다$")) { match ->
            val middle = match.groupValues[1]
            if (looksComplete(middle)) "오늘은 $middle" else match.value
        }
        return if (text.isBlank()) "오늘은 조용히 지나간 하루였다." else text.ensurePeriod()
    }
    fun looksSuspicious(rawAnswer: String): Boolean {
        val compact = rawAnswer.replace(" ", "").trim()
        if (compact.isBlank()) return false
        if (Regex("[ㄱ-ㅎㅏ-ㅣ]").containsMatchIn(compact)) return true
        return compact.length >= 2 && !Regex("[가-힣A-Za-z0-9]").containsMatchIn(compact)
    }

    private fun detectCategory(phrase: String, question: Question): String {
        val compact = phrase.replace(" ", "")
        return when {
            compact in sleepWords || listOf("잠", "수면", "잤", "낮잠", "졸").any { compact.contains(it) } -> "sleep"
            listOf("커피", "라떼", "아메리카노", "맥주", "소주", "물", "음료", "차").any { compact.contains(it) } -> "drink"
            listOf("김치찌개", "된장찌개", "라면", "밥", "국밥", "샐러드", "치킨", "피자", "햄버거", "식사", "점심", "저녁", "아침", "먹").any { compact.contains(it) } -> "food"
            listOf("비", "눈", "맑", "흐", "더", "추", "날씨", "바람").any { compact.contains(it) } -> "weather"
            listOf("기분", "우울", "짜증", "화남", "좋", "나쁨", "슬픔", "행복").any { compact.contains(it) } -> "mood"
            listOf("피곤", "아픔", "아파", "몸", "컨디션", "무거", "괜찮").any { compact.contains(it) } -> "condition"
            listOf("출근", "퇴근", "이동", "운전", "버스", "지하철", "택시", "기차", "걷", "산책").any { compact.contains(it) } -> "movement"
            listOf("구매", "결제", "샀", "쇼핑", "돈", "소비").any { compact.contains(it) } -> "spending"
            listOf("회사", "업무", "회의", "야근", "공부", "작업", "일").any { compact.contains(it) } -> "work"
            question.customAnswerType.contains("food") || question.category == "식사" -> "food"
            question.customAnswerType.contains("sleep") || question.category == "수면" -> "sleep"
            question.customAnswerType.contains("weather") || question.category == "날씨" -> "weather"
            question.customAnswerType.contains("mood") || question.category == "기분" -> "mood"
            question.customAnswerType.contains("condition") || question.category == "컨디션" -> "condition"
            question.customAnswerType.contains("movement") || question.category.contains("이동") -> "movement"
            question.customAnswerType.contains("spending") || question.category == "소비" -> "spending"
            question.customAnswerType.contains("work") || question.category == "일/학교" -> "work"
            else -> "activity"
        }
    }

    private fun foodSentence(phrase: String): String {
        val normalized = normalizeStateWord(phrase)
        return when {
            looksComplete(normalized) -> withTodayPrefixIfNeeded(normalized)
            listOf("맛있", "든든", "간단", "대충", "거의 못", "못 챙").any { normalized.contains(it) } -> "오늘은 식사를 $normalized"
            listOf("먹", "챙", "해결").any { normalized.contains(it) } -> "오늘은 $normalized"
            else -> "오늘은 ${phrase.withObjectParticle()} 먹었다"
        }
    }

    private fun drinkSentence(phrase: String): String {
        return when {
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            listOf("마셨", "마심", "마시").any { phrase.contains(it) } -> "오늘은 $phrase"
            else -> "오늘은 ${phrase.withObjectParticle()} 마셨다"
        }
    }

    private fun sleepSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        return when {
            compact in sleepWords -> "오늘은 잠을 잤다"
            compact == "낮잠" || compact == "낮잠자기" -> "오늘은 낮잠을 잤다"
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            phrase.contains("설쳤") || phrase.contains("설침") -> "오늘은 잠을 설쳤다"
            phrase.contains("늦게") -> "오늘은 늦게 잤다"
            phrase.contains("잘") -> "오늘은 잠을 잘 잤다"
            phrase.contains("잠") -> "오늘은 $phrase"
            else -> "오늘은 잠이 ${normalizeStateWord(phrase)}"
        }
    }

    private fun weatherSentence(phrase: String): String {
        val state = normalizeStateWord(phrase)
        return when {
            state == "비가 왔다" || state == "눈이 왔다" -> "오늘은 $state"
            phrase.contains("날씨") -> withTodayPrefixIfNeeded(state)
            else -> "오늘은 날씨가 $state"
        }
    }

    private fun stateSentence(subject: String, phrase: String): String {
        val state = normalizeStateWord(phrase)
        return when {
            looksComplete(state) -> withTodayPrefixIfNeeded(state)
            phrase.contains(subject) -> "오늘은 $state"
            else -> "오늘은 ${subject}이 $state"
        }
    }

    private fun movementSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "출근" -> "출근했다"
            "퇴근" -> "퇴근했다"
            "운전" -> "운전을 했다"
            "이동" -> "이동하는 시간이 있었다"
            "산책", "걷기" -> "산책을 했다"
            else -> null
        }
        return if (mapped != null) "오늘은 $mapped" else activitySentence(phrase)
    }

    private fun spendingSentence(phrase: String): String {
        return when {
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            phrase.contains("샀") || phrase.contains("구매") || phrase.contains("결제") -> "오늘은 $phrase"
            else -> "오늘은 ${phrase.withObjectParticle()} 샀다"
        }
    }

    private fun workSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "회의" -> "회의를 했다"
            "야근" -> "야근을 했다"
            "공부" -> "공부를 했다"
            "작업" -> "작업을 했다"
            "회사일", "업무" -> "회사 일을 했다"
            else -> null
        }
        return if (mapped != null) "오늘은 $mapped" else activitySentence(phrase)
    }

    private fun activitySentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "잠", "자기", "잠자기", "잠을잠" -> "잠을 잤다"
            "낮잠" -> "낮잠을 잤다"
            "휴식", "쉬기" -> "쉬는 시간을 가졌다"
            "청소" -> "청소를 했다"
            "빨래" -> "빨래를 했다"
            "요리" -> "요리를 했다"
            "운동" -> "운동을 했다"
            "헬스" -> "헬스를 했다"
            "골프" -> "골프를 쳤다"
            "게임" -> "게임을 했다"
            "독서" -> "책을 읽었다"
            "병원" -> "병원에 다녀왔다"
            else -> null
        }
        if (mapped != null) return "오늘은 $mapped"
        if (looksComplete(phrase)) return withTodayPrefixIfNeeded(phrase)
        if (phrase.endsWith("하기") && phrase.length > 2) return "오늘은 ${phrase.dropLast(2).withObjectParticle()} 했다"
        if (phrase.endsWith("기") && phrase.length > 1) return "오늘은 ${phrase.dropLast(1).withObjectParticle()} 했다"
        return "오늘은 ${phrase.withObjectParticle()} 했다"
    }

    private fun cleanInput(raw: String): String = raw.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")

    private fun normalizePoliteEnding(text: String): String {
        fun replaceEnding(suffix: String, replacement: String): String? = if (text.endsWith(suffix)) text.dropLast(suffix.length) + replacement else null
        return replaceEnding("이었어요", "이었다")
            ?: replaceEnding("였어요", "였다")
            ?: replaceEnding("했어요", "했다")
            ?: replaceEnding("됐어요", "됐다")
            ?: replaceEnding("먹었어요", "먹었다")
            ?: replaceEnding("마셨어요", "마셨다")
            ?: replaceEnding("잤어요", "잤다")
            ?: replaceEnding("었어요", "었다")
            ?: replaceEnding("았어요", "았다")
            ?: replaceEnding("예요", "이다")
            ?: replaceEnding("이에요", "이다")
            ?: replaceEnding("어요", "었다")
            ?: replaceEnding("아요", "았다")
            ?: if (text.endsWith("요")) text.dropLast(1) else text
    }

    private fun normalizeStateWord(phrase: String): String = when (phrase.trim()) {
        "좋아", "좋음", "좋았음", "좋았다" -> "좋았다"
        "안 좋아", "안좋아", "나빠", "나쁨", "별로" -> "좋지 않았다"
        "괜찮아", "괜찮음", "괜찮았다" -> "괜찮았다"
        "피곤해", "피곤함", "피곤했다" -> "피곤했다"
        "힘들어", "힘듦", "힘들었다" -> "힘들었다"
        "무거워", "무거움", "무거웠다" -> "무거웠다"
        "맑아", "맑음", "맑았다" -> "맑았다"
        "흐려", "흐림", "흐렸다" -> "흐렸다"
        "더워", "더움", "더웠다" -> "더웠다"
        "추워", "추움", "추웠다" -> "추웠다"
        "비", "비옴", "비가 왔음", "비가 왔다" -> "비가 왔다"
        "눈", "눈옴", "눈이 왔음", "눈이 왔다" -> "눈이 왔다"
        else -> phrase
    }

    private fun looksComplete(value: String): Boolean {
        val text = value.trim().trimEnd('.', '!', '?')
        return text.endsWith("다") || text.endsWith("했다") || text.endsWith("먹었다") || text.endsWith("마셨다") || text.endsWith("잤다") || text.endsWith("왔다") || text.endsWith("갔다")
    }

    private fun withTodayPrefixIfNeeded(sentence: String): String {
        val text = sentence.trim()
        return if (text.startsWith("오늘") || text.startsWith("나는")) text else "오늘은 $text"
    }
    private fun String.withObjectParticle(): String {
        val value = trim()
        if (value.isBlank()) return value
        val last = value.last()
        val hasBatchim = last in '가'..'힣' && ((last.code - '가'.code) % 28 != 0)
        return value + if (hasBatchim) "을" else "를"
    }
    private val sleepWords = setOf("잠", "자기", "잠자기", "잠을잠", "잠자는것", "잠자는거")
}



