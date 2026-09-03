package com.example.diaryapp

object DiarySentenceEngine {
    private data class SentenceFrame(
        val prefix: String,
        val suffix: String,
        val sampleSlot: String,
        val support: Int
    )

    fun fromOption(option: AnswerOption, question: Question): String {
        val original = cleanInput(option.sentence)
        val sanitized = sanitizeOptionSentence(original, option)
        if (sanitized != original) {
            val phrase = stripLeadingSelf(normalizePoliteEnding(cleanInput(option.label)))
            val frame = inferSentenceFrame(question.options)
            if (frame != null && hasMeaningfulSubject(frame.prefix)) {
                sentenceFromOptionFrame(option.label, phrase, question)?.let { return polish(it) }
            }
        }
        return polish(sanitized)
    }

    fun fromCustomAnswer(rawAnswer: String, question: Question): String {
        val input = cleanInput(rawAnswer)
        if (input.isBlank()) return "오늘은 아무것도 남기지 않은 하루였다."

        val phrase = stripLeadingSelf(normalizePoliteEnding(input))
        negativeSentence(phrase, question)?.let { return polish(it) }
        QuestionAwareFallback.fromCustomAnswer(input, question)?.let { return polish(it) }
        sentenceFromOptionFrame(input, phrase, question)?.let { return polish(it) }

        val context = detectContext(phrase, question)
        if (looksComplete(phrase)) return polish(completeSentenceForContext(context, phrase))

        val sentence = when (context) {
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
            "exercise" -> exerciseSentence(phrase)
            "hobby" -> hobbySentence(phrase)
            "rest" -> restSentence(phrase)
            "study" -> studySentence(phrase)
            "appointment" -> appointmentSentence(phrase)
            "family" -> familySentence(phrase)
            "people" -> peopleSentence(phrase)
            "home" -> homeSentence(phrase)
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
            .replace("오늘은 종이접을 했다", "오늘은 종이 접기를 했다")
            .replace("오늘은 맛있게 먹었다 하루였다", "오늘은 맛있게 먹었다")
            .replace("오늘은 기분이 좋았다 하루였다", "오늘은 기분이 좋았다")
            .replace("오늘은 날씨가 좋았다 하루였다", "오늘은 날씨가 좋았다")
            .replace("가족였다", "가족이었다")
            .replace("계획였다", "계획이었다")
            .replace("고민였다", "고민이었다")
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

    private fun sentenceFromOptionFrame(rawInput: String, phrase: String, question: Question): String? {
        val frame = inferSentenceFrame(question.options) ?: return null
        val context = detectContext(phrase, question)
        val copulaSlot = frame.suffix.hasAny("이었다", "였다") && phrase.endsWith("이다")
        val slot = when {
            frame.suffix.isNotBlank() && looksComplete(phrase) && !copulaSlot -> phrase
            frame.suffix.isNotBlank() -> normalizeNominalInput(rawInput)
            else -> clauseForFrame(phrase, context)
        }.trim()
        if (slot.isBlank()) return null

        val suffix = if (frame.suffix.isNotBlank() && looksComplete(phrase) && !copulaSlot) {
            ""
        } else {
            adjustLeadingParticle(frame.suffix, slot)
        }
        return "${frame.prefix}$slot$suffix"
    }

    private fun inferSentenceFrame(options: List<AnswerOption>): SentenceFrame? {
        if (options.isEmpty()) return null
        val frames = options.mapNotNull { option ->
            val sentence = sanitizeOptionSentence(cleanInput(option.sentence), option)
            if (isMalformedOptionSentence(sentence)) return@mapNotNull null
            val candidates = listOfNotNull(option.label, option.value)
                .flatMap { answer ->
                    val cleaned = cleanInput(answer)
                    listOf(
                        cleaned,
                        normalizeNominalInput(cleaned),
                        stripLeadingSelf(normalizePoliteEnding(cleaned)),
                        normalizeStateWord(stripLeadingSelf(normalizePoliteEnding(cleaned)))
                    )
                }
                .filter { it.length >= 2 }
                .distinct()
                .sortedByDescending { it.length }
            val slot = candidates.firstOrNull { sentence.contains(it) } ?: return@mapNotNull null
            val index = sentence.indexOf(slot)
            SentenceFrame(
                prefix = sentence.substring(0, index),
                suffix = sentence.substring(index + slot.length),
                sampleSlot = slot,
                support = 1
            )
        }
        if (frames.isEmpty()) return null

        val best = frames
            .groupBy { it.prefix to it.suffix }
            .map { (_, matches) -> matches.first().copy(support = matches.size) }
            .maxByOrNull { frameScore(it) }
            ?: return null
        return best
    }

    private fun frameScore(frame: SentenceFrame): Int {
        val subjectLength = frame.prefix
            .replace("오늘은", "")
            .replace("오늘", "")
            .replace("나는", "")
            .trim()
            .length
        return (subjectLength * 100) + (frame.support * 10) + frame.suffix.length
    }

    private fun hasMeaningfulSubject(prefix: String): Boolean {
        return prefix
            .replace("오늘은", "")
            .replace("오늘", "")
            .replace("나는", "")
            .trim()
            .isNotBlank()
    }

    private fun sanitizeOptionSentence(rawSentence: String, option: AnswerOption): String {
        var sentence = rawSentence
        listOfNotNull(option.label, option.value).forEach { answer ->
            val cleaned = cleanInput(answer)
            val declarative = normalizeStateWord(stripLeadingSelf(normalizePoliteEnding(cleaned)))
            val noun = normalizeNominalInput(answer)
            if (noun.length >= 2) {
                sentence = sentence
                    .replace("${noun}이다를", noun.withObjectParticle())
                    .replace("${noun}이다을", noun.withObjectParticle())
                    .replace("${noun}이다가", noun.withSubjectParticle())
                    .replace("${noun}이다는", noun + if (hasFinalConsonant(noun.last())) "은" else "는")
                    .replace("${noun}였다", noun + if (hasFinalConsonant(noun.last())) "이었다" else "였다")
            }
            if (!declarative.endsWith("이다")) {
                listOf(noun, declarative).filter { it.isNotBlank() }.forEach { form ->
                    sentence = sentence
                        .replace("${form}를 먹었다", declarative)
                        .replace("${form}을 먹었다", declarative)
                }
            }
        }
        return sentence.replace("오늘은 오늘은", "오늘은")
    }

    private fun isMalformedOptionSentence(sentence: String): Boolean {
        if (sentence.contains("오늘은 오늘은")) return true
        return Regex("(?:했다|었다|았다|였다|이다|해|어|아)(?:을|를|이|가|은|는)(?:\\s|$)")
            .containsMatchIn(sentence)
    }

    private fun clauseForFrame(phrase: String, context: String): String {
        if (looksComplete(phrase)) return phrase
        return when (context) {
            "mood", "condition", "weather" -> normalizeStateWord(phrase)
            "exercise" -> if (phrase.hasAny("많", "적", "비슷", "평소", "보통")) {
                normalizeStateWord(phrase)
            } else {
                activitySentence(phrase).removePrefix("오늘은 ")
            }
            "food" -> if (phrase.hasAny("많", "적", "비슷", "평소", "보통", "든든", "부족", "간단")) {
                normalizeStateWord(phrase)
            } else {
                "${normalizeNominalInput(phrase).withObjectParticle()} 먹었다"
            }
            "reason" -> if (phrase.replace(" ", "") in noReasonWords) {
                "특별한 이유는 없었다"
            } else {
                "${phrase.withSubjectParticle()} 이유였다"
            }
            "thought" -> "${normalizeNominalInput(phrase).withTopicParticle()} 대해 생각했다"
            "sleep" -> sleepSentence(phrase).removePrefix("오늘은 ")
            "drink" -> drinkSentence(phrase).removePrefix("오늘은 ")
            "movement" -> movementSentence(phrase).removePrefix("오늘은 ")
            "spending" -> spendingSentence(phrase).removePrefix("오늘은 ")
            "work" -> workSentence(phrase).removePrefix("오늘은 ")
            "hobby" -> hobbySentence(phrase).removePrefix("오늘은 ")
            "rest" -> restSentence(phrase).removePrefix("오늘은 ")
            "study" -> studySentence(phrase).removePrefix("오늘은 ")
            "appointment" -> appointmentSentence(phrase).removePrefix("오늘은 ")
            "family" -> familySentence(phrase).removePrefix("오늘은 ")
            "people" -> peopleSentence(phrase).removePrefix("오늘은 ")
            "home" -> homeSentence(phrase).removePrefix("오늘은 ")
            else -> activitySentence(phrase).removePrefix("오늘은 ")
        }
    }

    private fun normalizeNominalInput(raw: String): String {
        return cleanInput(raw)
            .removePrefix("오늘은 ")
            .removePrefix("오늘 ")
            .removeSuffix("이에요")
            .removeSuffix("예요")
            .removeSuffix("이요")
            .removeSuffix("요")
            .trim()
    }

    private fun adjustLeadingParticle(suffix: String, slot: String): String {
        if (suffix.isBlank() || slot.isBlank()) return suffix
        val hasBatchim = hasFinalConsonant(slot.last())
        return when {
            suffix.startsWith("이었다") || suffix.startsWith("였다") -> {
                (if (hasBatchim) "이었다" else "였다") + suffix.drop(if (suffix.startsWith("이었다")) 3 else 2)
            }
            suffix.startsWith("을") || suffix.startsWith("를") -> (if (hasBatchim) "을" else "를") + suffix.drop(1)
            suffix.startsWith("이") || suffix.startsWith("가") -> (if (hasBatchim) "이" else "가") + suffix.drop(1)
            suffix.startsWith("은") || suffix.startsWith("는") -> (if (hasBatchim) "은" else "는") + suffix.drop(1)
            suffix.startsWith("과") || suffix.startsWith("와") -> (if (hasBatchim) "과" else "와") + suffix.drop(1)
            suffix.startsWith("으로") || suffix.startsWith("로") -> {
                val rieulBatchim = slot.last() in '가'..'힣' && ((slot.last().code - '가'.code) % 28 == 8)
                (if (hasBatchim && !rieulBatchim) "으로" else "로") + suffix.drop(if (suffix.startsWith("으로")) 2 else 1)
            }
            else -> suffix
        }
    }

    fun looksSuspicious(rawAnswer: String, question: Question): Boolean {
        return looksSuspicious(rawAnswer)
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
            title.hasAny("날씨", "하늘", "계절") || type.contains("weather") || category == "날씨" -> "weather"
            title.hasAny("기분", "마음") || type.contains("mood") || category == "기분" -> "mood"
            title.hasAny("컨디션", "건강", "몸", "피곤") || type.contains("condition") || type.contains("health") || category == "건강" -> "condition"
            title.hasAny("이동", "출근", "퇴근", "운전", "길") || type.contains("movement") || category == "이동" -> "movement"
            title.hasAny("소비", "돈", "샀", "지출") || type.contains("spending") || category == "소비" -> "spending"
            title.hasAny("운동", "움직", "활동량") || type.contains("exercise") || category == "운동" -> "exercise"
            title.hasAny("취미") || type.contains("hobby") || category == "취미" -> "hobby"
            title.hasAny("휴식", "쉬", "여유") || type.contains("rest") || category == "휴식" -> "rest"
            title.hasAny("공부", "학습") || type.contains("study") || category == "공부" -> "study"
            title.hasAny("가족") || type.contains("family") || category == "가족" -> "family"
            title.hasAny("약속", "일정") || type.contains("appointment") || category == "약속" -> "appointment"
            title.hasAny("사람", "대화", "연락") || type.contains("people") || category == "사람" -> "people"
            title.hasAny("집", "청소", "정리", "집안") || type.contains("home") || category == "집" -> "home"
            title.hasAny("일", "업무", "회사") || type.contains("work") || category == "일" -> "work"
            else -> "activity"
        }
    }

    private fun completeSentenceForContext(context: String, phrase: String): String {
        val state = normalizeStateWord(phrase)
        return when (context) {
            "weather" -> if (state.hasAny("비", "눈")) "오늘은 $state" else "오늘은 날씨가 $state"
            "mood" -> "오늘의 기분은 $state"
            "condition" -> "오늘은 컨디션이 $state"
            "exercise" -> "오늘의 활동량은 $state"
            "food" -> if (state.hasAny("먹", "챙", "해결")) "오늘은 $state" else "오늘 식사는 $state"
            else -> withTodayPrefixIfNeeded(phrase)
        }
    }

    private fun negativeSentence(phrase: String, question: Question): String? {
        val compact = phrase.replace(" ", "")
        if (compact !in negativeWords) return null
        if (question.title.contains("예정에 없던 만남")) {
            return "오늘은 예정에 없던 만남이 생기지 않았다"
        }
        return when (detectContext(phrase, question)) {
            "food" -> "오늘은 식사를 따로 남기지 않았다"
            "drink" -> "오늘은 따로 마신 것을 남기지 않았다"
            "sleep" -> "오늘은 잠에 대해 특별히 남길 내용이 없었다"
            "weather" -> "오늘은 날씨에 대해 특별히 남길 내용이 없었다"
            "mood" -> "오늘은 기분 변화가 특별히 없었다"
            "condition" -> "오늘은 컨디션에 대해 특별히 남길 내용이 없었다"
            "movement" -> "오늘은 이동에 대해 특별히 남길 내용이 없었다"
            "spending" -> "오늘은 따로 소비한 일이 없었다"
            "work" -> "오늘은 일과 관련해 특별히 남길 일이 없었다"
            "exercise" -> "오늘은 운동을 따로 하지 않았다"
            "hobby" -> "오늘은 취미로 한 일이 없었다"
            "rest" -> "오늘은 따로 쉰 시간이 없었다"
            "study" -> "오늘은 공부를 따로 하지 않았다"
            "appointment" -> "오늘은 약속이 없었다"
            "family" -> "오늘은 가족과 관련된 일정이 없었다"
            "people" -> "오늘은 사람들과 특별히 남길 일이 없었다"
            "home" -> "오늘은 집과 관련해 특별히 남길 일이 없었다"
            "thought" -> "오늘은 정리하고 싶은 생각이 특별히 없었다"
            "reason" -> "특별한 이유는 없었다"
            else -> "오늘은 특별히 남길 일이 없었다"
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
            else -> "오늘은 ${phrase.withTopicParticle()} 대해 생각했다"
        }
    }

    private fun foodSentence(phrase: String, question: Question): String {
        val state = normalizeStateWord(phrase)
        val title = question.title
        return when {
            looksComplete(state) -> completeSentenceForContext("food", state)
            title.hasAny("식사량", "양") && state.hasAny("많", "적", "보통", "평소", "비슷") -> "오늘 식사량은 $state"
            state.hasAny("많", "적", "보통", "든든", "부족", "간단", "비슷") -> "오늘 식사는 $state"
            state.hasAny("맛있게", "챙겨", "해결") -> "오늘은 $state"
            else -> "오늘은 ${phrase.withObjectParticle()} 먹었다"
        }
    }

    private fun drinkSentence(phrase: String): String {
        return when {
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            phrase.hasAny("마셨", "마심", "마시") -> "오늘은 $phrase"
            else -> "오늘은 ${phrase.withObjectParticle()} 마셨다"
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
            else -> "오늘은 날씨가 $state"
        }
    }

    private fun stateSentence(subject: String, phrase: String): String {
        val state = normalizeStateWord(phrase)
        return if (subject == "기분") "오늘의 기분은 $state" else "오늘은 ${subject}이 $state"
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
        return if (mapped != null) "오늘은 $mapped" else "오늘은 이동은 ${normalizeStateWord(phrase)}"
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
        return if (mapped != null) "오늘은 $mapped" else "오늘 일은 ${normalizeStateWord(phrase)}"
    }

    private fun exerciseSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val state = normalizeStateWord(phrase)
        val mapped = when (compact) {
            "운동" -> "운동을 했다"
            "헬스" -> "헬스를 했다"
            "걷기", "산책" -> "산책을 했다"
            "러닝" -> "러닝을 했다"
            else -> null
        }
        return when {
            mapped != null -> "오늘은 $mapped"
            state.hasAny("비슷", "많", "적", "평소") -> "오늘의 활동량은 $state"
            else -> "오늘의 활동량은 $state"
        }
    }

    private fun hobbySentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "종이접기", "종이접" -> "종이 접기를 했다"
            "게임" -> "게임을 했다"
            "독서" -> "책을 읽었다"
            "영화" -> "영화를 봤다"
            "드라마" -> "드라마를 봤다"
            else -> null
        }
        return if (mapped != null) "오늘은 $mapped" else activitySentence(phrase)
    }

    private fun restSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        return when {
            compact.hasAny("하루종일", "종일") -> "오늘은 하루 종일 쉬었다"
            compact.hasAny("잠", "낮잠") -> "오늘은 잠을 자며 쉬었다"
            compact.hasAny("영상", "유튜브") -> "오늘은 영상을 보며 쉬었다"
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            else -> "오늘은 ${phrase.withObjectParticle()} 하며 쉬었다"
        }
    }

    private fun studySentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "영어" -> "영어 공부를 했다"
            "한국사" -> "한국사 공부를 했다"
            "자격증" -> "자격증 공부를 했다"
            else -> null
        }
        return if (mapped != null) "오늘은 $mapped" else "오늘 공부는 ${normalizeStateWord(phrase)}"
    }

    private fun appointmentSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        return when {
            compact.hasAny("친구") -> "오늘은 친구와 약속이 있었다"
            compact.hasAny("가족") -> "오늘은 가족과 약속이 있었다"
            compact.hasAny("회사", "업무") -> "오늘은 업무 관련 약속이 있었다"
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            else -> "오늘은 ${phrase.withTopicParticle()} 약속이 있었다"
        }
    }

    private fun familySentence(phrase: String): String {
        val state = normalizeStateWord(phrase)
        return when {
            looksComplete(state) -> "오늘 가족과의 시간은 $state"
            else -> "오늘 가족과의 시간은 $state"
        }
    }

    private fun peopleSentence(phrase: String): String {
        return when {
            looksComplete(phrase) -> withTodayPrefixIfNeeded(phrase)
            else -> "오늘 사람들과의 일은 ${normalizeStateWord(phrase)}"
        }
    }

    private fun homeSentence(phrase: String): String {
        val compact = phrase.replace(" ", "")
        val mapped = when (compact) {
            "청소" -> "청소를 했다"
            "정리" -> "정리를 했다"
            "요리" -> "요리를 했다"
            "빨래" -> "빨래를 했다"
            else -> null
        }
        return if (mapped != null) "오늘은 $mapped" else "오늘 집에서는 ${normalizeStateWord(phrase)}"
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
            "종이접기", "종이접" -> "종이 접기를 했다"
            else -> null
        }
        if (mapped != null) return "오늘은 $mapped"
        if (looksComplete(phrase)) return withTodayPrefixIfNeeded(phrase)
        if (phrase.endsWith("하기") && phrase.length > 2) return "오늘은 ${phrase.withObjectParticle()} 했다"
        return "오늘은 ${phrase.withObjectParticle()} 했다"
    }

    private fun cleanInput(raw: String): String = raw.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")

    private fun stripLeadingSelf(text: String): String = text
        .removePrefix("오늘은 ")
        .removePrefix("오늘 ")
        .removePrefix("나는 ")
        .removePrefix("제가 ")
        .trim()

    private fun normalizePoliteEnding(text: String): String {
        val normalized = text.replace(Regex("\\s+모\\s+"), " ").replace("뭐 ", "").trim()
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
            "없었어" to "없었다",
            "없었어요" to "없었다",
            "있어요" to "있었다",
            "있어" to "있었다",
            "행복" to "행복했다",
            "늘 행복" to "늘 행복했다",
            "비슷" to "평소와 비슷했다",
            "비슷비슷해" to "평소와 비슷했다",
            "평소와 비슷" to "평소와 비슷했다",
            "평소와 비슷비슷해" to "평소와 비슷했다",
            "그럭저럭" to "그럭저럭이었다"
        )
        direct[normalized]?.let { return it }

        fun replaceEnding(suffix: String, replacement: String): String? =
            if (normalized.endsWith(suffix)) normalized.dropLast(suffix.length) + replacement else null

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
            ?: if (normalized.endsWith("요")) normalized.dropLast(1) else normalized
    }

    private fun normalizeStateWord(phrase: String): String {
        val text = phrase.trim().replace(Regex("\\s+모\\s+"), " ").replace("뭐 ", "").trim()
        if (text.contains("비슷")) return "평소와 비슷했다"
        val direct = when (text) {
            "좋", "좋음", "좋았다" -> "좋았다"
            "안 좋", "안좋", "안 좋았다", "안좋았다", "별로" -> "좋지 않았다"
            "피곤", "피곤함", "피곤했다" -> "피곤했다"
            "힘듦", "힘들", "힘들었다" -> "힘들었다"
            "괜찮", "괜찮았다" -> "괜찮았다"
            "많", "많았다" -> "많았다"
            "적", "적었다" -> "적었다"
            "보통", "평소", "평소처럼", "비슷", "비슷비슷", "비슷비슷했다", "평소와 비슷했다" -> "평소와 비슷했다"
            "행복", "행복했다" -> "행복했다"
            "늘 행복", "늘 행복했다" -> "늘 행복했다"
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
            .replace(Regex("행복$"), "행복했다")
            .replace(Regex("비슷비슷해$"), "평소와 비슷했다")
            .replace(Regex("비슷해$"), "평소와 비슷했다")
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

    private fun String.withTopicParticle(): String {
        val value = trim()
        if (value.isBlank()) return value
        return value + "에"
    }

    private fun String.hasAny(vararg words: String): Boolean = words.any { contains(it) }

    private fun String.ensurePeriod(): String = if (trim().endsWith(".")) trim() else "${trim()}."

    private fun hasFinalConsonant(char: Char): Boolean = char in '가'..'힣' && ((char.code - '가'.code) % 28 != 0)

    private val sleepWords = setOf("잠", "자기", "잠자기", "수면", "낮잠", "자는 것", "자는거")
    private val noReasonWords = setOf("그냥", "없음", "없어", "없어요", "딱히", "특별히없음", "잘모름", "모름")
    private val negativeWords = setOf("없다", "없었다", "없어", "없었어", "없어요", "없었어요", "없음", "안함", "안했다", "못함", "못했다")
}
