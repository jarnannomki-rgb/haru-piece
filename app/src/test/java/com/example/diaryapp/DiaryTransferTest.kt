package com.example.diaryapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DiaryTransferTest {
    @Test
    fun profileRoundTripPreservesPersonalization() {
        val original = Profile(
            name = "홍동",
            gender = "남성",
            age = "41",
            notifyTimes = listOf("평일 21:30"),
            topics = listOf("운동", "휴식"),
            topicDetails = mapOf("운동" to listOf("걷기", "헬스")),
            topicPromptDismissedDay = 7
        )

        assertEquals(original, profileFromJson(JSONObject(profileToJson(original).toString())))
    }

    @Test
    fun profileRoundTripPreservesDisabledNotifications() {
        val original = Profile("홍동", "남성", "41", emptyList(), emptyList())

        assertEquals(emptyList<String>(), profileFromJson(JSONObject(profileToJson(original).toString())).notifyTimes)
    }

    @Test
    fun entryRoundTripPreservesIdAndPhoto() {
        val original = DiaryEntry(
            date = "2026.09.08",
            time = "22:15",
            text = "오늘은 잘 쉬었다.",
            kind = "normal",
            photoUri = "C:/photos/a.jpg",
            id = "entry-1"
        )

        assertEquals(original, entryFromJson(JSONObject(entryToJson(original).toString())))
    }

    @Test
    fun legacyEntryGetsStableId() {
        val legacy = JSONObject()
            .put("date", "2026.09.08")
            .put("time", "22:15")
            .put("text", "기록")
            .put("kind", "normal")

        val first = entryFromJson(JSONObject(legacy.toString()), 3)
        val second = entryFromJson(JSONObject(legacy.toString()), 3)

        assertEquals(first.id, second.id)
        assertNull(first.photoUri)
    }

    @Test
    fun backupPathValidationRejectsTraversal() {
        assertTrue(isSafeZipEntry("photos/entry-1.jpg"))
        assertFalse(isSafeZipEntry("../entry-1.jpg"))
        assertFalse(isSafeZipEntry("photos/../../entry-1.jpg"))
        assertFalse(isSafeZipEntry("C:/entry-1.jpg"))
        assertFalse(isSafeZipEntry("/entry-1.jpg"))
    }

    @Test
    fun backupEntryLimitRejectsOversizedPayload() {
        val target = ByteArrayOutputStream()
        val exact = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        assertEquals(4L, exact.copyLimitedTo(target, 4))
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
                .copyLimitedTo(ByteArrayOutputStream(), 4)
        }
    }
}
