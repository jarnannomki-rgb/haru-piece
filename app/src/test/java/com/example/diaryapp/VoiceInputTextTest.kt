package com.example.diaryapp

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputTextTest {
    @Test
    fun recognizedTextIsAppendedToExistingText() {
        assertEquals(
            "오늘은 조금 피곤했다 그래도 산책했다",
            appendRecognizedText("오늘은 조금 피곤했다", "  그래도 산책했다  ")
        )
    }

    @Test
    fun blankRecognitionKeepsExistingTextUntouched() {
        assertEquals("기존 문장  ", appendRecognizedText("기존 문장  ", "   "))
    }

    @Test
    fun recognizedTextIsInsertedAtCurrentCursor() {
        val current = TextFieldValue(
            text = "오늘 좋았다.",
            selection = TextRange(2)
        )

        val updated = insertRecognizedText(current, "산책이")

        assertEquals("오늘 산책이 좋았다.", updated.text)
        assertEquals(TextRange(6), updated.selection)
    }

    @Test
    fun blankRecognitionKeepsFieldValueUntouched() {
        val current = TextFieldValue("오늘 좋았다.", TextRange(3))
        assertEquals(current, insertRecognizedText(current, "  "))
    }
}