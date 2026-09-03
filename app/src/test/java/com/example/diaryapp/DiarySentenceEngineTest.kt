package com.example.diaryapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiarySentenceEngineTest {
    private data class Case(
        val category: String,
        val type: String,
        val question: String,
        val answer: String,
        val expected: String
    )

    @Test
    fun customAnswersPreserveQuestionFocus() {
        val cases = listOf(
            Case("식사", "food", "오늘 식사는 어떤 편인가요?", "아주 좋았어", "오늘 식사는 아주 좋았다."),
            Case("식사", "food", "오늘 먹는 것 중 기억나는 게 있나요?", "제육볶음", "오늘 기억에 남는 음식은 제육볶음이었다."),
            Case("기분", "mood", "오늘 기분 변화는 있었나요?", "아니", "오늘은 기분 변화가 없었다."),
            Case("기분", "mood", "오늘 마음 상태는 어느 쪽에 가까운가요?", "마음이 무거워", "오늘의 마음은 무거웠다."),
            Case("일", "work", "오늘 해야 할 일은 어떻게 됐나요?", "다했지", "오늘은 해야 할 일을 모두 끝냈다."),
            Case("일", "work", "오늘 집중은 어떤 편이었나요?", "집중좋아", "오늘 집중은 잘됐다."),
            Case("사람", "people", "오늘 사람을 만나는 일은 어떤 편인가요?", "바빴어", "오늘은 사람을 만나느라 바빴다."),
            Case("사람", "people", "오늘 사람 때문에 기억나는 일이 있나요?", "아니", "오늘은 사람 때문에 특별히 기억나는 일이 없었다."),
            Case("소비", "spending", "오늘 가장 눈에 띄는 소비가 있었나요?", "햄버거", "오늘 가장 눈에 띈 소비는 햄버거였다."),
            Case("운동", "exercise", "오늘 몸을 얼마나 움직였나요?", "하루죙일", "오늘은 하루 종일 몸을 움직였다."),
            Case("운동", "exercise", "오늘 운동은 어떤가요?", "기분좋아", "오늘은 운동해서 기분이 좋았다."),
            Case("건강", "health", "오늘 몸에서 불편한 곳이 있나요?", "이곳저곳", "오늘은 몸 이곳저곳이 불편했다."),
            Case("건강", "health", "오늘 몸을 챙기는 일은 어땠나요?", "활기참", "오늘 몸 상태는 활기찼다."),
            Case("날씨", "weather", "오늘 날씨가 하루에 영향을 주나요?", "그런거 없어", "오늘은 날씨의 영향을 받지 않았다."),
            Case("날씨", "weather", "오늘 날씨가 하루에 영향을 주나요?", "많이줌", "오늘은 날씨의 영향을 많이 받았다."),
            Case("날씨", "weather", "오늘 계절감은 어떤 쪽에 가까운가요?", "환절기", "오늘은 환절기다운 날씨였다."),
            Case("가족", "family", "오늘 가족과 어떤 일이 있었나요?", "밥먹기", "오늘은 가족과 밥을 먹었다."),
            Case("가족", "family", "오늘 가족과 관련된 일정이 있었나요?", "가족모임", "오늘은 가족 모임이 있었다."),
            Case("가족", "family", "오늘 가족과의 분위기는 어떤 편인가요?", "좋았지", "오늘 가족과의 분위기는 좋았다."),
            Case("집", "home", "오늘 집안일은 어느 정도 했나요?", "빨래", "오늘은 빨래를 했다."),
            Case("집", "home", "오늘 집에서는 어떻게 보내고 있나요?", "편하게", "오늘은 집에서 편하게 보냈다."),
            Case("취미", "hobby", "오늘 취미로 한 일이 있나요?", "종이접기", "오늘은 종이 접기를 했다."),
            Case("취미", "hobby", "오늘 취미를 즐긴 방식은 어떤 쪽인가요?", "혼자즐김", "오늘은 혼자 취미를 즐겼다."),
            Case("취미", "hobby", "오늘 취미 시간은 평소와 비교해 어떤가요?", "더 많은 시간을 보냈어", "오늘은 평소보다 취미에 더 많은 시간을 보냈다."),
            Case("휴식", "rest", "오늘은 어떻게 쉬고 있나요?", "그냥 편하게", "오늘은 그냥 편하게 쉬었다."),
            Case("휴식", "rest", "오늘 휴식 시간은 어느 정도인가요?", "엄청 길었어", "오늘 휴식 시간은 엄청 길었다."),
            Case("휴식", "rest", "오늘 잠깐이라도 멈추는 시간이 있었나요?", "2시간", "오늘은 2시간 정도 쉬었다."),
            Case("휴식", "rest", "오늘은 평소보다 잘 쉬고 있나요?", "엄청잘쉬고있음", "오늘은 평소보다 잘 쉬었다."),
            Case("공부", "study", "오늘 공부나 배움은 어떤가요?", "어려워", "오늘 공부는 어려웠다."),
            Case("공부", "study", "오늘 배우는 데 쓴 시간은 어느 정도인가요?", "한 1시간", "오늘 공부한 시간은 약 1시간이었다."),
            Case("공부", "study", "오늘 공부는 어떻게 진행되고 있나요?", "막혔어", "오늘 공부는 막혀 있었다."),
            Case("공부", "study", "오늘 배운 내용은 어떤 쪽인가요?", "물리, 화하이었어", "오늘은 물리와 화학을 배웠다."),
            Case("이동", "movement", "오늘 이동 방식은 어떤 쪽이 많나요?", "자차", "오늘은 자차로 이동했다."),
            Case("이동", "movement", "오늘 이동 시간은 평소와 비교해 어떤가요?", "바빳어", "오늘은 이동하느라 바빴다."),
            Case("이동", "movement", "오늘 이동은 편한 편인가요?", "완전", "오늘 이동은 완전히 편했다."),
            Case("이동", "movement", "오늘 평소와 다른 이동이 있었나요?", "그런건 없었어", "오늘은 평소와 다른 이동이 없었다."),
            Case("약속", "appointment", "오늘 약속 일정은 계획대로 가고 있나요?", "응", "오늘 약속 일정은 계획대로 진행됐다."),
            Case("약속", "appointment", "오늘 예정에 없던 만남이 생겼나요?", "없음", "오늘은 예정에 없던 만남이 생기지 않았다."),
            Case("생각", "thought", "오늘 생각이 많은 편인가요?", "생각이 많네", "오늘은 생각이 많았다."),
            Case("생각", "thought", "오늘 생각은 평소와 비교해 어떤가요?", "크게 다르지 않아", "오늘 생각은 평소와 크게 다르지 않았다.")
        )

        val failures = cases.mapIndexedNotNull { index, case ->
            val question = Question(
                title = case.question,
                options = emptyList(),
                category = case.category,
                key = "test-$index",
                customAnswerType = case.type
            )
            val actual = DiarySentenceEngine.fromCustomAnswer(case.answer, question)
            if (actual == case.expected) null else "case ${index + 1}: ${case.question} / ${case.answer}\nexpected=${case.expected}\nactual=$actual"
        }
        assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
    }

    @Test
    fun typoDetectionAllowsNormalShortKoreanAnswers() {
        val question = Question("오늘 기분은 어떤가요?", emptyList(), category = "기분", customAnswerType = "mood")
        assertFalse(DiarySentenceEngine.looksSuspicious("최고", question))
        assertFalse(DiarySentenceEngine.looksSuspicious("외로움", question))
        assertFalse(DiarySentenceEngine.looksSuspicious("없었어", question))
        assertTrue(DiarySentenceEngine.looksSuspicious("자 ㅁ 작", question))
    }
}
