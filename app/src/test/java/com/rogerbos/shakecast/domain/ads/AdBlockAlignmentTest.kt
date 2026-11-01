package com.rogerbos.shakecast.domain.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockAlignmentTest {
    @Test
    fun withinTolerance() {
        assertTrue(AdBlockAlignment.isAligned(3600.0, 3601.5))
        assertTrue(AdBlockAlignment.isAligned(3600.0, 3598.0))
    }

    @Test
    fun outsideTolerance() {
        assertFalse(AdBlockAlignment.isAligned(3600.0, 3660.0))
        assertFalse(AdBlockAlignment.isAligned(0.0, 3600.0))
        assertFalse(AdBlockAlignment.isAligned(3600.0, 0.0))
    }
}

class TranscriptAlignmentTest {
    @Test
    fun trailingMusicIsAccepted() {
        assertTrue(AdBlockAlignment.isTranscriptAligned(1838.7, 1862.0))
        assertTrue(AdBlockAlignment.isTranscriptAligned(1860.0, 1861.0))
    }

    @Test
    fun longPrerollOrOverrunIsRejected() {
        assertFalse(AdBlockAlignment.isTranscriptAligned(1800.0, 1900.0))
        assertFalse(AdBlockAlignment.isTranscriptAligned(1900.0, 1800.0))
        assertFalse(AdBlockAlignment.isTranscriptAligned(0.0, 1800.0))
    }
}
