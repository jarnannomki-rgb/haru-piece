package com.example.diaryapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoProcessingTest {
    @Test
    fun imageSampleSizeKeepsDecodedPhotoNearTargetBounds() {
        assertEquals(1, calculateImageSampleSize(1600, 1200, 1600))
        assertEquals(2, calculateImageSampleSize(3000, 2000, 1600))
        assertEquals(8, calculateImageSampleSize(12000, 9000, 1600))
    }

    @Test
    fun invalidBoundsUseSafeDefaultSampleSize() {
        assertEquals(1, calculateImageSampleSize(0, 0, 1600))
        assertEquals(1, calculateImageSampleSize(-1, -1, 1600))
    }
}
