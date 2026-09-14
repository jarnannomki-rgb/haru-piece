package com.example.diaryapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar
import java.util.GregorianCalendar

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
    @Test
    fun weeklyNotificationUsesSundayAtEightThirtyPm() {
        val mondayMorning = GregorianCalendar(2026, Calendar.SEPTEMBER, 14, 9, 0)
        val expected = GregorianCalendar(2026, Calendar.SEPTEMBER, 20, 20, 30)

        assertEquals(expected.timeInMillis, nextWeeklyRecapMillis(mondayMorning))
    }

    @Test
    fun weeklyNotificationMovesToNextWeekAfterSundayTime() {
        val sundayNight = GregorianCalendar(2026, Calendar.SEPTEMBER, 20, 21, 0)
        val expected = GregorianCalendar(2026, Calendar.SEPTEMBER, 27, 20, 30)

        assertEquals(expected.timeInMillis, nextWeeklyRecapMillis(sundayNight))
    }
}
