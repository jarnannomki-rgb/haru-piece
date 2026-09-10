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

    @Test
    fun largeLandscapeIsScaledWithinLimit() {
        assertEquals(1280 to 853, calculateScaledImageDimensions(6000, 4000, 1280))
    }

    @Test
    fun smallPhotoKeepsOriginalDimensions() {
        assertEquals(800 to 600, calculateScaledImageDimensions(800, 600, 1280))
    }
}
