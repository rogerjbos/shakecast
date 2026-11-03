package com.rogerbos.shakecast.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualSkipRecorderTest {

    @Test
    fun twoShakesInARowBecomeOneSpan() {
        val r = ManualSkipRecorder()
        assertNull(r.onSkipForward(1, fromMs = 100_000, toMs = 130_000, nowMs = 0))
        assertNull(r.onSkipForward(1, fromMs = 130_400, toMs = 160_400, nowMs = 2_000))
        assertNull(r.flush(nowMs = 5_000))
        assertEquals(SkipSpan(1, 100_000, 160_400), r.flush(nowMs = 12_001))
    }

    @Test
    fun backSkipAfterOvershootTrimsTheSpan() {
        val r = ManualSkipRecorder()
        r.onSkipForward(1, 100_000, 130_000, nowMs = 0)
        r.onSkipForward(1, 130_000, 160_000, nowMs = 1_000)
        r.onSkipBack(1, 160_500, 145_500, nowMs = 2_000)
        assertEquals(SkipSpan(1, 100_000, 145_500), r.flush(nowMs = 20_000))
    }

    @Test
    fun backSkipPastTheStartCancelsTheSpan() {
        val r = ManualSkipRecorder()
        r.onSkipForward(1, 100_000, 130_000, nowMs = 0)
        r.onSkipBack(1, 130_000, 95_000, nowMs = 1_000)
        assertNull(r.flush(nowMs = 20_000))
    }

    @Test
    fun shortSpansAreDropped() {
        val r = ManualSkipRecorder(minSpanMs = 15_000)
        r.onSkipForward(1, 100_000, 110_000, nowMs = 0)
        assertNull(r.flush(nowMs = 20_000))
    }

    @Test
    fun aSkipElsewhereFinalizesThePreviousSpan() {
        val r = ManualSkipRecorder()
        r.onSkipForward(1, 100_000, 130_000, nowMs = 0)
        val finalized = r.onSkipForward(1, 500_000, 530_000, nowMs = 3_000)
        assertEquals(SkipSpan(1, 100_000, 130_000), finalized)
        assertEquals(SkipSpan(1, 500_000, 530_000), r.flush(nowMs = 0, force = true))
    }

    @Test
    fun aDifferentEpisodeStartsAFreshSpan() {
        val r = ManualSkipRecorder()
        r.onSkipForward(1, 100_000, 130_000, nowMs = 0)
        val finalized = r.onSkipForward(2, 130_000, 160_000, nowMs = 1_000)
        assertEquals(SkipSpan(1, 100_000, 130_000), finalized)
    }
}
