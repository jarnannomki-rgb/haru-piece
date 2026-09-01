package com.example.diaryapp

object DiarySentenceEngine {
    fun fromCustomAnswer(rawAnswer: String, question: Question): String {
        val input = cleanInput(rawAnswer)
        if (input.isBlank()) return "오늘은 아무것도 남기지 않은 하루였다."

        val phrase = normalizePoliteEnding(input)
        if (looksComplete(phrase)) return polish(withTodayPrefixIfNeeded(phrase))

        val sentence = when (detectContext(phrase, question)) {
            "reason" -> reasonSentence(phrase)
            "thought" -> thoughtSentence(phrase)
            "food" -> foodSentence(phrase, question)
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
        return if (cleaned.isEmpty()) "오늘은 아무것도 남기지 않은 하루였다." else cleaned.joinToString(" ") { it.ensurePeriod() }
    }

    fun polish(raw: String): String {
        var text = raw.trim().trimEnd('.', '!', '?')
            .replace(Regex("\\s+"), " ")
            .replace("오늘은 오늘은", "오늘은")
            .replace("오늘은그냥", "오늘은 그냥")
            .replace("오늘은 그냥을 했다", "특별한 이유는 없었다")
            .replace("오늘은 내일을 했다", "오늘은 내일에 대해 생각했다")
            .replace("오늘은 자기를 했다", "오늘은 잠을 잤다")
            .replace("오늘은 잠자기를 했다", "오늘은 잠을 잤다")
            .replace("오늘은 잠을 했다", "오늘은 잠을 잤다")
            .replace("오늘은 수면을 했다", "오늘은 잠을 잤다")
            .replace("오늘은 맛있게 먹었다 하루였다", "오늘은 맛있게 먹었다")
            .replace("오늘은 기분이 좋았다 하루였다", "오늘은 기분이 좋았다")
            .replace("오늘은 날씨가 좋았다 하루였다", "오늘은 날씨가 좋았다")
            .replace("가족였다", "가족이었다")
            .replace("계획였다", "계획이었다")
            .replace("고민였다", "고민이었다")
            .replace("아이디어였다", "아이디어였다")
            .replace("일였다", "일이었다")

        text = text.replace(Regex("오늘은\\s+(.+?)\\s+하루였다$")) { match ->
            val middle = match.groupValues[1]
            if (looksComplete(middle)) "오늘은 $middle" else match.value
        }
        return if (text.isBlank()) "오늘은 아무것도 남기지 않은 하루였다." else text.ensurePeriod()
    }

    fun looksSuspicious(rawAnswer: String): Boolean {
        val compact = rawAnswer.replace(" ", "").trim()
        if (compact.isBlank()) return false
        if (Regex("[ㄱ-ㅎㅏ-ㅣ]").containsMatchIn(compact)) return true
        return compact.length >= 2 && !Regex("[가-힣A-Za-z0-9]").containsMatchIn(compact)
    }

    private fun detectContext(phrase: String, question: Question): String {
        val title = question.title
        val category = question.category
        val type = question.customAnswerType
        val compact = phrase.replace(" ", "")

        return when {
            title.hasAny("이유", "왜", "때문", "계기") || type.contains("reason") -> "reason"
            title.hasAny("생각", "고민", "정리", "마음에 남") || type.contains("thought") || category == "생각" -> "thought"
            compact in sleepWords || compact.hasAny("잠", "수면", "낮잠", "졸림") -> "sleep"
            compact.hasAny("커피", "라떼", "아메리카노", "음료", "술", "맥주") -> "drink"
            compact.hasAny("김치찌개", "된장찌개", "제육", "밥", "라면", "치킨", "점심", "저녁", "아침", "식사") -> "food"
            title.hasAny("식사", "끼니", "점심", "저녁", "아침", "먹") || type.contains("food") || category == "식사" -> "food"
            title.hasAny("날씨", "하늘", "비", "눈") || type.contains("weather") || category == "날씨" -> "weather"
            title.hasAny("기분", "마음") || type.contains("mood") || category == "기분" -> "mood"
            title.hasAny("컨디션", "건강", "몸", "피곤") || type.contains("condition") || category == "건강" -> "condition"
            title.hasAny("이동", "출근", "퇴근", "운전", "길") || type.contains("movement") || category == "이동" -> "movement"
            title.hasAny("소비", "돈", "샀", "지출") || type.contains("spending") || category == "소비" -> "spending"
            title.hasAny("일", "업무", "회사") || type.contains("work") || category == "일" -> "work"
            else -> "activity"
        }
    }

    private fun reasonSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        return when {
            compact in noReasonWords -> "특별한 이유는 없었다"
            compact.hasAny("그냥", "딱히") -> "특별한 이유는 없었다"
            compact.hasAny("모름", "몰라", "잘모르") -> "이유는 잘 모르겠다"
            phrase.endsWith("서") || phrase.endsWith("어서") || phrase.endsWith("아서") -> "${phrase}였다"
            phrase.endsWith("때문") -> "${phrase}이었다"
            phrase.endsWith("때문에") -> phrase
            compact.hasAny("피곤", "지침") -> "피곤해서였다"
            compact.hasAny("시간") -> "시간이 부족해서였다"
            else -> "이유는 ${phrase.withSubjectParticle()} 있었다"
        }
    }

    private fun thoughtSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        return when {
            compact in noReasonWords -> "오늘은 정리하고 싶은 생각이 특별히 없었다"
            compact == "내일" -> "오늘은 내일에 대해 생각했다"
            compact == "일" || compact == "회사" -> "오늘은 일에 대해 생각했다"
            compact.hasAny("걱정") -> "오늘은 걱정되는 생각이 있었다"
            compact.hasAny("계획") -> "오늘은 계획에 대해 생각했다"
            compact.hasAny("고민") -> "오늘은 고민이 있었다"
            compact.hasAny("아이디어") -> "오늘은 아이디어가 떠올랐다"
            phrase.endsWith("생각") -> "오늘은 ${phrase.withSubjectParticle()} 있었다"
            else -> "오늘은 ${phrase.withAboutParticle()} 대해 생각했다"
        }
    }

    private fun drinkSentence(phrase: String): String {
        return when {
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            phrase.hasAny("마셨", "마심", "마시") -> "오늘은 $phrase"
            else -> "오늘은 ${phrase.withObjectParticle()} 마셨다"
        }
    }

    private fun foodSentence(phrase: String, question: Question): String {
        val state = normalizeStateWord(phrase)
        val title = question.title
        return when {
            looksComplete(state) -> withTodayPrefixIfNeeded(state)
            title.hasAny("식사량", "양") && state.hasAny("많", "적", "보통", "평소") -> "오늘 식사량은 $state"
            state.hasAny("많", "적", "보통", "든든", "부족", "간단") -> "오늘 식사는 $state"
            state.hasAny("맛있게", "챙겨", "해결") -> "오늘은 $state"
            else -> "오늘은 ${phrase.withObjectParticle()} 먹었다"
        }
    }

    private fun sleepSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val state = normalizeStateWord(phrase)
        return when {
            compact in sleepWords -> "오늘은 잠을 잤다"
            compact == "잠자기" || compact == "자기" -> "오늘은 잠을 잤다"
            looksComplete(state) -> withTodayPrefixIfNeeded(state)
            state.hasAny("많", "적", "설침", "설쳤", "늦") -> "오늘은 잠을 $state"
            else -> "오늘은 잠을 잤다"
        }
    }

    private fun weatherSentence(phrase: String): String {
        val state = normalizeStateWord(phrase)
        return when {
            state.hasAny("비", "눈") -> "오늘은 $state"
            looksComplete(state) -> "오늘은 날씨가 $state"
            else -> "오늘은 날씨가 $state"
        }
    }

    private fun stateSentence(subject: String, phrase: String): String {
        val state = normalizeStateWord(phrase)
        return when {
            looksComplete(state) -> "오늘은 ${subject}이 $state"
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
            phrase.hasAny("샀", "구매", "결제", "썼") -> "오늘은 $phrase"
            else -> "오늘은 ${phrase.withObjectParticle()} 샀다"
        }
    }

    private fun workSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "회의" -> "회의를 했다"
            "야근" -> "야근을 했다"
            "업무" -> "업무를 했다"
            "작업" -> "작업을 했다"
            "회사일", "일" -> "회사 일을 했다"
            else -> null
        }
        return if (mapped != null) "오늘은 $mapped" else activitySentence(phrase)
    }

    private fun activitySentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "잠", "자기", "잠자기", "수면", "낮잠" -> "잠을 잤다"
            "휴식", "쉬기" -> "쉬는 시간을 가졌다"
            "청소" -> "청소를 했다"
            "정리" -> "정리를 했다"
            "요리" -> "요리를 했다"
            "운동" -> "운동을 했다"
            "헬스" -> "헬스를 했다"
            "산책" -> "산책을 했다"
            "독서" -> "책을 읽었다"
            "공부" -> "공부를 했다"
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
        val direct = mapOf(
            "좋아" to "좋았다",
            "좋아요" to "좋았다",
            "괜찮아요" to "괜찮았다",
            "괜찮아" to "괜찮았다",
            "맛있어요" to "맛있었다",
            "맛있어" to "맛있었다",
            "많아요" to "많았다",
            "많아" to "많았다",
            "적어요" to "적었다",
            "적어" to "적었다",
            "없어요" to "없었다",
            "없어" to "없었다",
            "있어요" to "있었다",
            "있어" to "있었다",
            "그럭저럭" to "그럭저럭이었다"
        )
        direct[text]?.let { return it }

        fun replaceEnding(suffix: String, replacement: String): String? =
            if (text.endsWith(suffix)) text.dropLast(suffix.length) + replacement else null

        return replaceEnding("했어요", "했다")
            ?: replaceEnding("했어", "했다")
            ?: replaceEnding("먹었어요", "먹었다")
            ?: replaceEnding("먹었어", "먹었다")
            ?: replaceEnding("였어요", "였다")
            ?: replaceEnding("였어", "였다")
            ?: replaceEnding("이었어요", "이었다")
            ?: replaceEnding("이었어", "이었다")
            ?: replaceEnding("았어요", "았다")
            ?: replaceEnding("았어", "았다")
            ?: replaceEnding("었어요", "었다")
            ?: replaceEnding("었어", "었다")
            ?: replaceEnding("예요", "이다")
            ?: replaceEnding("이에요", "이다")
            ?: if (text.endsWith("요")) text.dropLast(1) else text
    }

    private fun normalizeStateWord(phrase: String): String {
        val text = phrase.trim()
        val direct = when (text) {
            "좋", "좋음", "좋았다" -> "좋았다"
            "안 좋", "안좋", "안 좋았다", "안좋았다", "별로" -> "좋지 않았다"
            "피곤", "피곤함", "피곤했다" -> "피곤했다"
            "힘듦", "힘들", "힘들었다" -> "힘들었다"
            "괜찮", "괜찮았다" -> "괜찮았다"
            "많", "많았다" -> "많았다"
            "적", "적었다" -> "적었다"
            "보통", "평소", "평소처럼" -> "평소와 비슷했다"
            "맛있게", "맛있게 먹었다" -> "맛있게 먹었다"
            "비", "비옴", "비가 왔다" -> "비가 왔다"
            "눈", "눈옴", "눈이 왔다" -> "눈이 왔다"
            "맑음", "맑았다" -> "맑았다"
            "흐림", "흐렸다" -> "흐렸다"
            else -> null
        }
        if (direct != null) return direct

        return text
            .replace(Regex("좋아$"), "좋았다")
            .replace(Regex("많아$"), "많았다")
            .replace(Regex("적어$"), "적었다")
            .replace(Regex("피곤해$"), "피곤했다")
            .replace(Regex("힘들어$"), "힘들었다")
            .replace(Regex("괜찮아$"), "괜찮았다")
            .replace(Regex("맑아$"), "맑았다")
            .replace(Regex("흐려$"), "흐렸다")
            .replace(Regex("더워$"), "더웠다")
            .replace(Regex("추워$"), "추웠다")
    }

    private fun looksComplete(value: String): Boolean {
        val text = value.trim().trimEnd('.', '!', '?')
        return text.endsWith("다") || text.endsWith("였다") || text.endsWith("이었다") || text.endsWith("있었다") || text.endsWith("없었다")
    }

    private fun withTodayPrefixIfNeeded(sentence: String): String {
        val text = sentence.trim()
        return if (text.startsWith("오늘") || text.startsWith("이번")) text else "오늘은 $text"
    }

    private fun String.withObjectParticle(): String {
        val value = trim()
        if (value.isBlank()) return value
        return value + if (hasFinalConsonant(value.last())) "을" else "를"
    }

    private fun String.withSubjectParticle(): String {
        val value = trim()
        if (value.isBlank()) return value
        return value + if (hasFinalConsonant(value.last())) "이" else "가"
    }

    private fun String.withAboutParticle(): String {
        val value = trim()
        if (value.isBlank()) return value
        return value + if (hasFinalConsonant(value.last())) "에" else "에"
    }

    private fun String.hasAny(vararg words: String): Boolean = words.any { contains(it) }

    private fun hasFinalConsonant(char: Char): Boolean = char in '가'..'힣' && ((char.code - '가'.code) % 28 != 0)

    private val sleepWords = setOf("잠", "자기", "잠자기", "수면", "낮잠", "자는 것", "자는거")
    private val noReasonWords = setOf("그냥", "없음", "없어", "없어요", "딱히", "특별히없음", "잘모름", "모름")
}
