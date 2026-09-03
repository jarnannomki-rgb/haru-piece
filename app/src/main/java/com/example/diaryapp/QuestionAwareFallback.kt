package com.example.diaryapp

internal object QuestionAwareFallback {
    fun fromCustomAnswer(rawAnswer: String, question: Question): String? = when {
        question.category == "식사" || question.customAnswerType == "food" -> food(rawAnswer, question.title)
        question.category == "기분" || question.customAnswerType == "mood" -> mood(rawAnswer, question.title)
        question.category == "일" || question.customAnswerType == "work" -> work(rawAnswer, question.title)
        question.category == "사람" || question.customAnswerType == "people" -> people(rawAnswer, question.title)
        question.category == "소비" || question.customAnswerType == "spending" -> spending(rawAnswer, question.title)
        question.category == "운동" || question.customAnswerType == "exercise" -> exercise(rawAnswer, question.title)
        question.category == "건강" || question.customAnswerType in setOf("health", "condition") -> health(rawAnswer, question.title)
        question.category == "날씨" || question.customAnswerType == "weather" -> weather(rawAnswer, question.title)
        question.category == "가족" || question.customAnswerType == "family" -> family(rawAnswer, question.title)
        question.category == "집" || question.customAnswerType == "home" -> home(rawAnswer, question.title)
        question.category == "취미" || question.customAnswerType == "hobby" -> hobby(rawAnswer, question.title)
        question.category == "휴식" || question.customAnswerType == "rest" -> rest(rawAnswer, question.title)
        question.category == "공부" || question.customAnswerType == "study" -> study(rawAnswer, question.title)
        question.category == "이동" || question.customAnswerType == "movement" -> movement(rawAnswer, question.title)
        question.category == "약속" || question.customAnswerType == "appointment" -> appointment(rawAnswer, question.title)
        question.category == "생각" || question.customAnswerType == "thought" -> thought(rawAnswer, question.title)
        else -> null
    }

    private fun food(raw: String, title: String): String {
        val answer = stripSubject(raw, "식사가", "식사는", "식사량이", "식사량은", "끼니가", "끼니는")
        val compact = answer.compact()
        return when {
            title.hasAny("기억나는", "기억나는 게") -> "오늘 기억에 남는 음식은 ${pastIdentity(answer)}"
            title.contains("식사량") -> "오늘 식사량은 ${amountPredicate(answer)}"
            title.contains("주로 어떻게 해결") -> when {
                compact.hasAny("혼밥", "혼자") -> "오늘은 혼자 식사했다"
                compact.contains("배달") -> "오늘은 배달로 식사했다"
                compact.hasAny("외식", "밖") -> "오늘은 밖에서 식사했다"
                else -> "오늘은 ${actionPredicate(answer, "식사했다")}"
            }
            title.contains("어떻게 챙") -> if (compact.hasAny("먹", "챙")) {
                "오늘은 식사를 ${predicate(answer)}"
            } else {
                "오늘은 ${actionPredicate(answer, "식사를 챙겼다")}"
            }
            else -> "오늘 식사는 ${predicate(answer)}"
        }
    }

    private fun mood(raw: String, title: String): String {
        val answer = stripSubject(raw, "기분이", "기분은", "마음이", "마음은", "감정이", "감정은")
        val compact = answer.compact()
        if (title.contains("변화") && compact in setOf("아니", "아니야", "없어", "없었어", "없음")) {
            return "오늘은 기분 변화가 없었다"
        }
        val state = predicate(answer)
        return when {
            title.contains("가장 오래 이어진") -> "오늘 가장 오래 이어진 기분은 $state"
            title.contains("마음 상태") -> "오늘의 마음은 $state"
            title.contains("감정") -> "오늘의 감정은 $state"
            else -> "오늘의 기분은 $state"
        }
    }

    private fun work(raw: String, title: String): String {
        val answer = stripSubject(raw, "일이", "일은", "업무가", "업무는")
        val compact = answer.compact()
        return when {
            title.contains("해야 할 일") -> when {
                compact.hasAny("다끝", "전부끝", "모두끝", "다했", "전부했", "모두했") -> "오늘은 해야 할 일을 모두 끝냈다"
                compact.contains("끝") -> "오늘은 해야 할 일을 끝냈다"
                compact.hasAny("남", "못끝") -> "오늘은 해야 할 일이 ${predicate(answer)}"
                compact.hasAny("진행", "하고있", "하는중") -> "오늘은 해야 할 일을 진행하고 있었다"
                else -> "오늘은 해야 할 일을 ${actionPredicate(answer, "처리했다")}"
            }
            title.contains("흐름") -> "오늘 일의 흐름은 ${predicate(answer)}"
            title.contains("집중") -> if (compact.hasAny("집중좋", "잘됐", "잘됨")) {
                "오늘 집중은 잘됐다"
            } else {
                "오늘 집중은 ${predicate(answer)}"
            }
            title.hasAny("눈에 띄는 일", "기억나는 일") -> "오늘 일에서 기억나는 것은 ${pastIdentity(answer)}"
            title.hasAny("어떤 편", "어땠", "비교") -> "오늘 일은 ${predicate(answer)}"
            else -> "오늘 일은 ${predicate(answer)}"
        }
    }

    private fun people(raw: String, title: String): String {
        val answer = stripSubject(raw, "사람들과의 시간이", "사람들과의 시간은", "사람들과의 분위기가", "사람들과의 분위기는")
        val compact = answer.compact()
        return when {
            title.hasAny("대화나 연락", "연락이나 대화") -> when {
                compact.hasAny("많이함", "많이했") -> "오늘은 대화나 연락을 많이 했다"
                compact.hasAny("조금함", "조금했") -> "오늘은 대화나 연락을 조금 했다"
                else -> "오늘 대화나 연락은 ${predicate(answer)}"
            }
            title.contains("사람을 만나는 일") -> if (compact.hasAny("바빴", "바빠", "바빳")) {
                "오늘은 사람을 만나느라 바빴다"
            } else {
                "오늘 사람을 만나는 일은 ${predicate(answer)}"
            }
            title.contains("분위기") -> "오늘 사람들과의 분위기는 ${predicate(answer)}"
            title.contains("기억나는 일") -> if (compact in noWords) {
                "오늘은 사람 때문에 특별히 기억나는 일이 없었다"
            } else {
                "오늘 사람 때문에 기억나는 일은 ${pastIdentity(answer)}"
            }
            title.contains("시간") -> "오늘 사람들과의 시간은 ${predicate(answer)}"
            else -> "오늘 사람들과의 일은 ${predicate(answer)}"
        }
    }

    private fun spending(raw: String, title: String): String {
        val answer = stripSubject(raw, "소비가", "소비는", "지출이", "지출은")
        val compact = answer.compact()
        return when {
            title.hasAny("눈에 띄는 소비", "기억나는 소비") -> "오늘 가장 눈에 띈 소비는 ${pastIdentity(answer)}"
            title.hasAny("돈을 쓴 곳", "지출한 곳") -> "오늘은 ${locationParticle(nominal(answer))} 돈을 썼다"
            title.contains("계획한 범위") -> when {
                compact.hasAny("아니", "벗어", "초과", "충동") -> "오늘 지출은 계획한 범위를 벗어났다"
                compact.hasAny("맞", "안", "범위") -> "오늘 지출은 계획한 범위 안이었다"
                else -> "오늘 지출은 ${predicate(answer)}"
            }
            title.hasAny("평소와 비교", "어떤 편", "소비를 평소") -> when {
                compact.hasAny("많이샀", "많이썼") -> "오늘은 평소보다 소비를 많이 했다"
                compact.hasAny("적게샀", "적게썼") -> "오늘은 평소보다 소비를 적게 했다"
                else -> "오늘 소비는 ${predicate(answer)}"
            }
            title.hasAny("무엇을 샀", "구매한 것") -> "오늘은 ${objectParticle(nominal(answer))} 샀다"
            else -> "오늘 소비는 ${predicate(answer)}"
        }
    }

    private fun exercise(raw: String, title: String): String {
        val answer = stripSubject(raw, "활동량이", "활동량은", "운동이", "운동은")
        val compact = answer.compact()
        return when {
            compact.contains("기분") && compact.hasAny("좋", "최고") -> "오늘은 운동해서 기분이 좋았다"
            title.hasAny("방식", "어떤 운동") -> "오늘은 ${actionClause(answer)}"
            title.contains("운동 계획") -> when {
                compact.hasAny("못", "안함", "실패") -> "오늘은 운동 계획을 지키지 못했다"
                compact.hasAny("잘", "완료", "지킴") -> "오늘은 운동 계획을 잘 지켰다"
                else -> "오늘 운동 계획은 ${predicate(answer)}"
            }
            title.hasAny("활동량", "얼마나 움직") -> when {
                compact.hasAny("하루죙일", "하루종일", "종일") -> "오늘은 하루 종일 몸을 움직였다"
                compact.hasAny("많이움직", "많이활동") -> "오늘은 몸을 많이 움직였다"
                else -> "오늘 활동량은 ${amountPredicate(answer)}"
            }
            else -> "오늘 운동은 ${predicate(answer)}"
        }
    }

    private fun health(raw: String, title: String): String {
        val answer = stripSubject(raw, "몸이", "몸은", "컨디션이", "컨디션은")
        val compact = answer.compact()
        return when {
            title.hasAny("불편한 곳", "아픈 곳", "통증") -> when {
                compact in setOf("이곳저곳", "여기저기", "온몸") -> "오늘은 몸 이곳저곳이 불편했다"
                compact.contains("없") -> "오늘은 몸에 불편한 곳이 없었다"
                else -> "오늘은 ${subjectParticle(nominal(answer))} 불편했다"
            }
            title.contains("몸을 챙기는 일") && compact.hasAny("활기참", "활기차", "활기") -> "오늘 몸 상태는 활기찼다"
            else -> "오늘 컨디션은 ${predicate(answer)}"
        }
    }

    private fun weather(raw: String, title: String): String {
        val answer = stripSubject(raw, "날씨가", "날씨는")
        val compact = answer.compact()
        return when {
            title.contains("영향") -> when {
                compact.contains("없") || compact.hasAny("아니", "그렇지않") -> "오늘은 날씨의 영향을 받지 않았다"
                compact.hasAny("많이줌", "많이받", "큰영향") -> "오늘은 날씨의 영향을 많이 받았다"
                compact.hasAny("조금줌", "조금받") -> "오늘은 날씨의 영향을 조금 받았다"
                compact.hasAny("행복", "기분좋", "즐거") -> if (compact.contains("행복")) {
                    "오늘은 날씨 덕분에 행복했다"
                } else {
                    "오늘은 날씨 덕분에 기분이 좋았다"
                }
                else -> "오늘은 날씨의 영향을 ${predicate(answer)}"
            }
            title.contains("계절감") -> "오늘은 ${nominal(answer)}다운 날씨였다"
            else -> "오늘 날씨는 ${predicate(answer)}"
        }
    }

    private fun family(raw: String, title: String): String {
        val answer = stripSubject(raw, "가족과의 시간이", "가족과의 시간은", "가족과의 분위기가", "가족과의 분위기는")
        val compact = answer.compact()
        return when {
            title.contains("어떤 일이") -> if (compact.hasAny("밥먹", "식사")) {
                "오늘은 가족과 밥을 먹었다"
            } else {
                "오늘은 가족과 ${actionClause(answer)}"
            }
            title.contains("분위기") -> "오늘 가족과의 분위기는 ${predicate(answer)}"
            title.contains("관련된 일정") -> when {
                compact.contains("없") -> "오늘은 가족과 관련된 일정이 없었다"
                compact in setOf("가족식사", "식사", "같이식사", "가족모임") -> "오늘은 가족 모임이 있었다"
                compact in setOf("산책", "가족산책") -> "오늘은 가족과 산책했다"
                compact in setOf("즐거움", "즐거웠어", "즐거워") -> "오늘은 가족과 즐거운 시간을 보냈다"
                else -> "오늘은 가족과 ${actionClause(answer)}"
            }
            title.contains("평소와 비교") -> "오늘 가족과의 시간은 ${predicate(answer)}"
            else -> "오늘 가족과 보낸 시간은 ${predicate(answer)}"
        }
    }

    private fun home(raw: String, title: String): String {
        val answer = stripSubject(raw, "집에서의 시간이", "집에서의 시간은", "집안일이", "집안일은")
        val compact = answer.compact()
        return when {
            title.contains("어떻게 보내") -> "오늘은 집에서 ${adverbial(answer, "보냈다")}"
            title.contains("집안일") -> if (compact in setOf("빨래", "세탁")) {
                "오늘은 빨래를 했다"
            } else {
                "오늘은 집안일을 ${actionPredicate(answer, "했다")}"
            }
            title.contains("가장 가까운 모습") -> "오늘 집에서의 모습은 ${pastIdentity(answer)}"
            title.contains("보낸 시간") -> "오늘 집에서 보낸 시간은 ${predicate(answer)}"
            title.contains("평소와 다른 일") -> if (compact.contains("없")) {
                "오늘은 집에서 평소와 다른 일이 없었다"
            } else {
                "오늘 집에서 평소와 달랐던 일은 ${pastIdentity(answer)}"
            }
            else -> "오늘은 집에서 ${predicate(answer)}"
        }
    }

    private fun hobby(raw: String, title: String): String {
        val answer = stripSubject(raw, "취미 시간이", "취미 시간은", "취미가", "취미는")
        val compact = answer.compact()
        return when {
            title.hasAny("취미로 한 일", "여유 시간에 무엇") -> "오늘은 ${actionClause(answer)}"
            title.contains("평소와 비교") -> when {
                compact.hasAny("더많은시간", "오래보냈", "더오래") -> "오늘은 평소보다 취미에 더 많은 시간을 보냈다"
                compact.hasAny("더적은시간", "짧게보냈", "덜했") -> "오늘은 평소보다 취미에 적은 시간을 보냈다"
                else -> "오늘 취미 시간은 ${predicate(answer)}"
            }
            title.contains("콘텐츠나 활동") -> if (compact.contains("없")) {
                "오늘은 관심이 간 콘텐츠나 활동이 없었다"
            } else {
                "오늘 관심이 간 활동은 ${pastIdentity(answer)}"
            }
            title.contains("즐긴 방식") -> when {
                compact in setOf("혼자즐김", "혼자", "혼자서") -> "오늘은 혼자 취미를 즐겼다"
                compact.hasAny("쉬", "보", "했", "즐") -> "오늘은 ${actionClause(answer)}"
                else -> "오늘은 ${nominal(answer)} 방식으로 취미를 즐겼다"
            }
            else -> "오늘 취미 시간은 ${predicate(answer)}"
        }
    }

    private fun rest(raw: String, title: String): String {
        val answer = stripSubject(raw, "휴식 시간이", "휴식 시간은")
        val compact = answer.compact()
        return when {
            title.contains("어떻게 쉬") -> "오늘은 ${adverbial(answer, "쉬었다")}"
            title.contains("휴식 시간") -> "오늘 휴식 시간은 ${durationPredicate(answer)}"
            title.contains("쉬는 방식") -> if (compact in setOf("집에서휴식", "집에서쉼", "집에서쉬기")) {
                "오늘은 집에서 쉬었다"
            } else {
                "오늘은 ${actionClause(answer, restMode = true)}"
            }
            title.contains("잘 쉬고") -> when {
                compact.hasAny("평소보다좋", "더잘쉼", "잘쉼", "엄청잘쉬") -> "오늘은 평소보다 잘 쉬었다"
                compact in setOf("편하게", "푹", "느긋하게") -> "오늘은 $compact 쉬었다"
                else -> "오늘은 ${predicate(answer)}"
            }
            title.contains("멈추는 시간") -> when {
                compact.contains("없") -> "오늘은 잠깐 멈춰 쉬는 시간이 없었다"
                Regex("\\d+\\s*(시간|분)").containsMatchIn(answer) -> "오늘은 $answer 정도 쉬었다"
                else -> "오늘은 잠깐 멈춰 쉬는 시간이 있었다"
            }
            else -> "오늘은 ${predicate(answer)}"
        }
    }

    private fun study(raw: String, title: String): String {
        val answer = stripSubject(raw, "공부가", "공부는", "집중도가", "집중도는")
        val compact = answer.compact()
        return when {
            title.contains("공부나 배움") -> if (compact.contains("없")) {
                "오늘은 공부하거나 새로 배운 내용이 없었다"
            } else {
                "오늘 공부는 ${predicate(answer)}"
            }
            title.contains("쓴 시간") -> "오늘 공부한 시간은 ${durationIdentity(answer)}"
            title.contains("진행되고") -> when {
                compact.hasAny("잘되고있", "순조롭", "잘진행") -> "오늘 공부는 잘 진행되고 있었다"
                compact.hasAny("막혔", "막혀") -> "오늘 공부는 막혀 있었다"
                else -> "오늘 공부는 ${predicate(answer)}"
            }
            title.contains("배운 내용") -> "오늘은 ${objectParticle(studyContent(answer))} 배웠다"
            title.contains("집중도") -> if (compact.hasAny("잘됐", "잘됨", "집중잘")) {
                "오늘 공부 집중도는 좋았다"
            } else {
                "오늘 공부 집중도는 ${predicate(answer)}"
            }
            else -> "오늘 공부는 ${predicate(answer)}"
        }
    }

    private fun movement(raw: String, title: String): String {
        val answer = stripSubject(raw, "이동 시간이", "이동 시간은", "이동이", "이동은")
        val compact = answer.compact()
        return when {
            title.contains("이동 방식") -> movementMethod(answer)
            title.contains("이동 시간") -> if (compact.hasAny("바빴", "바빠", "바빳")) {
                "오늘은 이동하느라 바빴다"
            } else {
                "오늘 이동 시간은 ${durationPredicate(answer)}"
            }
            title.contains("편한 편") -> if (compact in setOf("완전", "완전히", "매우", "엄청")) {
                "오늘 이동은 완전히 편했다"
            } else {
                "오늘 이동은 ${predicate(answer)}"
            }
            title.contains("평소와 다른 이동") -> if (compact.contains("없")) {
                "오늘은 평소와 다른 이동이 없었다"
            } else {
                "오늘 평소와 달랐던 이동은 ${pastIdentity(answer)}"
            }
            else -> "오늘 이동은 ${predicate(answer)}"
        }
    }

    private fun appointment(raw: String, title: String): String {
        val answer = stripSubject(raw, "약속이", "약속은", "일정이", "일정은")
        val compact = answer.compact()
        return when {
            title.contains("계획대로") -> when {
                compact in setOf("응", "네", "예", "맞아", "그래") -> "오늘 약속 일정은 계획대로 진행됐다"
                compact.hasAny("일정대로", "계획대로", "가고있", "진행") -> "오늘 약속 일정은 계획대로 진행되고 있었다"
                compact.hasAny("아니", "변경", "취소") -> "오늘 약속 일정은 계획과 달라졌다"
                else -> "오늘 약속 일정은 ${predicate(answer)}"
            }
            title.contains("예정에 없던 만남") -> if (compact.contains("없")) {
                "오늘은 예정에 없던 만남이 생기지 않았다"
            } else {
                "오늘은 예정에 없던 만남이 생겼다"
            }
            title.contains("사람을 만나는 일정") -> "오늘 사람을 만나는 일정은 ${predicate(answer)}"
            else -> "오늘 약속이나 만남은 ${predicate(answer)}"
        }
    }

    private fun thought(raw: String, title: String): String {
        val answer = stripSubject(raw, "생각이", "생각은")
        val compact = answer.compact()
        return when {
            title.contains("많은 편") -> if (compact.hasAny("별로없", "많지않", "거의없")) {
                "오늘은 생각이 많지 않았다"
            } else {
                "오늘은 생각이 ${amountPredicate(answer)}"
            }
            title.contains("자주 떠오르는") -> if (compact.contains("없")) {
                "오늘 머릿속에 자주 떠오른 것은 없었다"
            } else {
                "오늘 머릿속에 자주 떠오른 것은 ${pastIdentity(answer)}"
            }
            title.contains("정리하고 싶은") -> if (compact.contains("없")) {
                "오늘은 특별히 정리하고 싶은 생각이 없었다"
            } else {
                "오늘은 ${nominal(answer)}에 대한 생각을 정리하고 싶었다"
            }
            title.contains("새로운 생각") -> if (compact.contains("없")) {
                "오늘은 새롭게 떠오른 생각이 없었다"
            } else {
                "오늘 새롭게 떠오른 생각은 ${pastIdentity(answer)}"
            }
            title.contains("평소와 비교") -> "오늘 생각은 평소와 ${comparisonPredicate(answer)}"
            else -> "오늘은 생각이 ${predicate(answer)}"
        }
    }

    private fun stripSubject(raw: String, vararg subjects: String): String {
        var text = clean(raw)
            .removePrefix("오늘은 ")
            .removePrefix("오늘 ")
            .removePrefix("나는 ")
            .removePrefix("제가 ")
            .trim()
        subjects.sortedByDescending { it.length }.firstOrNull { text.startsWith(it) }?.let {
            text = text.removePrefix(it).trim()
        }
        return text
    }

    private fun predicate(raw: String): String {
        val text = clean(raw)
        val direct = mapOf(
            "최고" to "최고였다", "최고야" to "최고였다", "최고였어" to "최고였다",
            "쓸쓸함" to "쓸쓸했다", "외로움" to "외로웠다", "즐거움" to "즐거웠다",
            "행복함" to "행복했다", "평균적" to "평균적이었다", "평균적이야" to "평균적이었다",
            "평소와 같아" to "평소와 같았다", "나쁘지 않음" to "나쁘지 않았다",
            "다 끝냄" to "모두 끝냈다", "많이함" to "많이 했다", "그닥" to "그다지 좋지 않았다",
            "많다" to "많았다", "적다" to "적었다", "좋다" to "좋았다", "힘들다" to "힘들었다",
            "바쁘다" to "바빴다", "괜찮다" to "괜찮았다", "같다" to "같았다", "높다" to "높았다",
            "낮다" to "낮았다", "있다" to "있었다", "없다" to "없었다", "좋았지" to "좋았다",
            "편했지" to "편했다", "즐거웠어" to "즐거웠다", "빡셌어" to "빡셌다",
            "길었어" to "길었다", "어려웠어" to "어려웠다", "바빳어" to "바빴다",
            "많네" to "많았다", "없었네" to "없었다", "비슷해" to "비슷했다",
            "크게 다르지 않아" to "크게 다르지 않았다", "어려워" to "어려웠다", "쉬워" to "쉬웠다",
            "재미있어" to "재미있었다", "재밌어" to "재미있었다", "편해" to "편했다",
            "불편해" to "불편했다", "편안해" to "편안했다", "지루해" to "지루했다",
            "심심해" to "심심했다", "복잡해" to "복잡했다", "어색해" to "어색했다",
            "화목해" to "화목했다", "행복해" to "행복했다", "우울해" to "우울했다",
            "피곤해" to "피곤했다", "부족해" to "부족했다", "막혔어" to "막혀 있었다",
            "오래 걸렸어" to "오래 걸렸다", "취소됐어" to "취소됐다", "부담스러워" to "부담스러웠다",
            "더워" to "더웠다", "추워" to "추웠다", "흐려" to "흐렸다", "맑아" to "맑았다",
            "무거워" to "무거웠다", "보냈어" to "보냈다", "좋음" to "좋았다",
            "안좋음" to "좋지 않았다", "많음" to "많았다", "적음" to "적었다",
            "어려움" to "어려웠다", "쉬움" to "쉬웠다", "밝아" to "밝았다"
        )
        direct[text]?.let { return it }
        val endings = listOf(
            "이었어요" to "이었다", "이었어" to "이었다", "였어요" to "였다", "였어" to "였다",
            "했어요" to "했다", "했어" to "했다", "았어요" to "았다", "았어" to "았다",
            "었어요" to "었다", "었어" to "었다", "좋아" to "좋았다",
            "보냈어" to "보냈다", "샀어" to "샀다", "썼어" to "썼다", "바빴어" to "바빴다",
            "바빠" to "바빴다", "힘들어" to "힘들었다", "쓸쓸해" to "쓸쓸했다",
            "외로워" to "외로웠다", "즐거워" to "즐거웠다", "같아" to "같았다",
            "많아" to "많았다", "적어" to "적었다", "좋네" to "좋았다", "우중충하네" to "우중충했다",
            "다르지 않아" to "다르지 않았다"
        )
        endings.sortedByDescending { it.first.length }.firstOrNull { text.endsWith(it.first) }?.let { (ending, replacement) ->
            return text.dropLast(ending.length) + replacement
        }
        if (text.endsWith("함") && text.length > 1) return text.dropLast(1).trim() + "했다"
        if (isComplete(text)) return text
        return pastIdentity(text)
    }

    private fun amountPredicate(raw: String): String {
        val compact = clean(raw).compact()
        return when {
            compact.hasAny("엄청많", "아주많") -> "엄청 많았다"
            compact in setOf("많이", "많음") -> "많았다"
            compact in setOf("조금", "적게", "적음") -> "적었다"
            else -> predicate(raw)
        }
    }

    private fun actionPredicate(raw: String, fallback: String): String {
        val value = predicate(raw)
        return if (isComplete(value)) value else fallback
    }

    private fun actionClause(raw: String, restMode: Boolean = false): String {
        val text = clean(raw)
        val compact = text.compact()
        val direct = mapOf(
            "밥먹기" to "밥을 먹었다", "밥먹었어" to "밥을 먹었다", "식사" to "식사했다",
            "종이접기" to "종이 접기를 했다", "집에서쉬었지" to "집에서 쉬었다",
            "집에서쉬었어" to "집에서 쉬었다", "잠자기" to "잠을 잤다", "자기" to "잠을 잤다",
            "산책" to "산책했다", "운동" to "운동했다", "내개인취미활동" to "개인 취미 활동을 했다",
            "개인취미활동" to "개인 취미 활동을 했다", "노래부르기" to "노래를 불렀다"
        )
        direct[compact]?.let { return it }
        if (restMode && compact.hasAny("편하게", "가만히", "푹")) return adverbial(text, "쉬었다")
        val value = predicate(text)
        if (isComplete(value)) return value
        return "${objectParticle(nominal(text))} 했다"
    }

    private fun adverbial(raw: String, verb: String): String {
        val text = clean(raw)
        val compact = text.compact()
        val adverbs = mapOf(
            "편하게" to "편하게", "그냥편하게" to "그냥 편하게", "푹" to "푹",
            "느긋하게" to "느긋하게", "조용히" to "조용히"
        )
        adverbs[compact]?.let { return "$it $verb" }
        val value = predicate(text)
        return if (isComplete(value) && value != pastIdentity(text)) value else "$text $verb"
    }

    private fun durationPredicate(raw: String): String {
        val text = clean(raw)
        return when {
            Regex("\\d+\\s*(시간|분)").containsMatchIn(text) -> durationIdentity(text)
            text.compact().hasAny("엄청길", "아주길") -> "엄청 길었다"
            else -> predicate(text)
        }
    }

    private fun durationIdentity(raw: String): String {
        var text = clean(raw).replace(Regex("^(한|약)\\s*"), "약 ")
        if (!text.startsWith("약 ") && text.any { it.isDigit() }) text = "약 $text"
        return pastIdentity(text)
    }

    private fun studyContent(raw: String): String {
        val text = clean(raw).replace("화하", "화학").replace(Regex("(이었어|였어|이야|야)$"), "").trim()
        val parts = text.split(Regex("[,/]")).map { it.trim() }.filter { it.isNotBlank() }
        return if (parts.size == 2) "${parts[0]}와 ${parts[1]}" else text
    }

    private fun movementMethod(raw: String): String {
        val text = clean(raw)
        val compact = text.compact()
        return when (compact) {
            "자차", "자가용", "자동차", "차" -> "오늘은 자차로 이동했다"
            "도보", "걸어서", "걷기" -> "오늘은 걸어서 이동했다"
            else -> "오늘은 ${directionParticle(text)} 이동했다"
        }
    }

    private fun comparisonPredicate(raw: String): String {
        val compact = clean(raw).compact()
        return when {
            compact.hasAny("크게다르지않", "별로다르지않") -> "크게 다르지 않았다"
            compact.contains("비슷") -> "비슷했다"
            else -> predicate(raw)
        }
    }

    private fun pastIdentity(raw: String): String {
        val value = nominal(raw)
        if (value.isBlank()) return "특별한 것이 없었다"
        if (isComplete(value)) return value
        return value + if (hasBatchim(value.last())) "이었다" else "였다"
    }

    private fun nominal(raw: String): String = clean(raw)
        .removePrefix("오늘은 ")
        .removePrefix("오늘 ")
        .removeSuffix("이에요")
        .removeSuffix("예요")
        .removeSuffix("이요")
        .removeSuffix("요")
        .trim()

    private fun objectParticle(value: String): String {
        if (value.isBlank()) return "내용을"
        return value + if (hasBatchim(value.last())) "을" else "를"
    }

    private fun subjectParticle(value: String): String {
        if (value.isBlank()) return "내용이"
        return value + if (hasBatchim(value.last())) "이" else "가"
    }

    private fun locationParticle(value: String): String {
        if (value.isBlank()) return "해당 장소에"
        return if (value.endsWith("에") || value.endsWith("에서")) value else "${value}에"
    }

    private fun directionParticle(value: String): String {
        if (value.isBlank()) return "해당 방식으로"
        val last = value.last()
        val rieul = last in '가'..'힣' && ((last.code - '가'.code) % 28 == 8)
        return value + if (hasBatchim(last) && !rieul) "으로" else "로"
    }

    private fun clean(raw: String): String = raw.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
    private fun String.compact(): String = replace(" ", "")
    private fun String.hasAny(vararg words: String): Boolean = words.any { contains(it) }
    private fun isComplete(value: String): Boolean = value.trim().trimEnd('.', '!', '?').endsWith("다")
    private fun hasBatchim(char: Char): Boolean = char in '가'..'힣' && ((char.code - '가'.code) % 28 != 0)
    private val noWords = setOf("아니", "아니야", "없어", "없었어", "없음")
}
