package com.rogerbos.shakecast.playback

import com.rogerbos.shakecast.playback.VolumeChordDetector.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeChordDetectorTest {
    @Test
    fun upThenDownSkipsForward() {
        var fwd = 0; var back = 0
        val d = VolumeChordDetector(onSkipForward = { fwd++ }, onSkipBack = { back++ })
        assertFalse(d.onPress(Key.UP, 1000))
        assertTrue(d.onPress(Key.DOWN, 1300))
        assertEquals(1, fwd); assertEquals(0, back)
    }

    @Test
    fun downThenUpSkipsBack() {
        var fwd = 0; var back = 0
        val d = VolumeChordDetector(onSkipForward = { fwd++ }, onSkipBack = { back++ })
        d.onPress(Key.DOWN, 1000)
        assertTrue(d.onPress(Key.UP, 1400))
        assertEquals(0, fwd); assertEquals(1, back)
    }

    @Test
    fun slowPairAndSameKeyDoNothing() {
        var fwd = 0; var back = 0
        val d = VolumeChordDetector(onSkipForward = { fwd++ }, onSkipBack = { back++ })
        d.onPress(Key.UP, 1000)
        assertFalse(d.onPress(Key.DOWN, 2000))
        assertFalse(d.onPress(Key.DOWN, 2100))
        assertFalse(d.onPress(Key.DOWN, 2200))
        assertEquals(0, fwd + back)
        assertTrue(d.onPress(Key.UP, 2300))
        assertEquals(1, back)
    }
}
