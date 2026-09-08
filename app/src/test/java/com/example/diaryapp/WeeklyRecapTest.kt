package com.example.diaryapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeeklyRecapTest {
    @Test
    fun weekStartsOnMondayForAnyDay() {
        val monday = LocalDate.of(2026, 9, 7)
        assertEquals(monday, startOfWeek(monday))
        assertEquals(monday, startOfWeek(LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun followingMondayStartsNewWeek() {
        assertEquals(
            LocalDate.of(2026, 9, 14),
            startOfWeek(LocalDate.of(2026, 9, 14))
        )
    }
}
