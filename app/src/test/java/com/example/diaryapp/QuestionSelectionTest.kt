package com.example.diaryapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSelectionTest {
    @Test
    fun noInterestUsesOnlySafeTopics() {
        val forbidden = setOf("일", "공부", "가족", "운동", "취미", "약속", "사람", "생각")
        val selected = (0 until 2_000).map { seed -> chooseStartCategory(emptyList(), seed) }

        assertFalse(selected.any { it in forbidden })
        assertTrue(selected.containsAll(listOf("식사", "기분", "건강", "날씨", "소비", "휴식", "이동", "집")))
    }

    @Test
    fun selectedInterestIsUsedSeventyPercentOfTheTime() {
        val selectedCount = (0 until 100).count { seed ->
            chooseStartCategory(listOf("운동"), seed) == "운동"
        }

        assertEquals(70, selectedCount)
    }

    @Test
    fun unknownTopicsAreIgnored() {
        val forbidden = setOf("일", "공부", "가족", "운동", "취미", "약속", "사람", "생각")
        val selected = (0 until 100).map { seed -> chooseStartCategory(listOf("알 수 없음"), seed) }

        assertFalse(selected.any { it in forbidden })
    }
}
