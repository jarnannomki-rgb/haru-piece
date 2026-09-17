package com.example.diaryapp

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun ageMustBeBetweenOneAndOneHundredTwenty() {
        assertTrue(isValidProfileAge("1"))
        assertTrue(isValidProfileAge("120"))
        assertFalse(isValidProfileAge(""))
        assertFalse(isValidProfileAge("0"))
        assertFalse(isValidProfileAge("121"))
        assertFalse(isValidProfileAge("224"))
        assertFalse(isValidProfileAge("abc"))
    }

    @Test
    fun transferredPhotoUsesCurrentDeviceFilesDirectory() {
        val photosDir = Files.createTempDirectory("haru-photos").toFile()
        val restoredPhoto = File(photosDir, "piece.jpg").apply { writeText("photo") }

        val oldPath = File(photosDir.parentFile, "old-device/entry_photos/piece.jpg").absolutePath
        val result = resolveTransferredPhotoPath(oldPath, photosDir)

        assertEquals(restoredPhoto.absolutePath, result)
        photosDir.deleteRecursively()
    }
}
