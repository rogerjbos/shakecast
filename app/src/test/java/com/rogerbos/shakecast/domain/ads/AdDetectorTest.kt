package com.rogerbos.shakecast.domain.ads

import com.rogerbos.shakecast.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDetectorTest {

    private fun seg(id: Int, start: Double, end: Double, text: String) = TranscriptSegment(id, start, end, text)

    @Test
    fun strongKeywordIsPaddedAndMerged() {
        val segments = listOf(
            seg(0, 0.0, 5.0, "Welcome to the show, let's dive in."),
            seg(1, 100.0, 105.0, "This episode is brought to you by Acme."),
            seg(2, 106.0, 110.0, "Use code SHOW for ten percent off."),
            seg(3, 300.0, 305.0, "Back to the markets."),
            seg(4, 600.0, 605.0, "Goodbye.")
        )
        val result = AdDetector.detect(segments)
        assertEquals(1, result.adBlocks.size)
        assertEquals(98.0, result.adBlocks[0].start, 1e-9)
        assertEquals(112.0, result.adBlocks[0].end, 1e-9)
        assertTrue(result.adBlocks[0].reason.contains("brought to you by"))
        assertEquals(2, result.contentRanges.size)
    }

    @Test
    fun contextualKeywordNeedsUrl() {
        val clean = listOf(seg(0, 0.0, 5.0, "You should check out the museum downtown."), seg(1, 100.0, 105.0, "Hello."))
        assertEquals(0, AdDetector.detect(clean).adBlocks.size)

        val ad = listOf(seg(0, 0.0, 5.0, "Check out acme.com for details."), seg(1, 100.0, 105.0, "Hello."))
        assertEquals(1, AdDetector.detect(ad).adBlocks.size)
    }

    @Test
    fun prerollBeforeLateIntro() {
        val segments = listOf(
            seg(0, 0.0, 10.0, "Some programmatic advertisement."),
            seg(1, 10.0, 20.0, "More of it."),
            seg(2, 40.0, 45.0, "Welcome to the show."),
            seg(3, 500.0, 505.0, "Content.")
        )
        val result = AdDetector.detect(segments)
        assertEquals(1, result.adBlocks.size)
        assertEquals(0.0, result.adBlocks[0].start, 1e-9)
        assertEquals(40.0, result.adBlocks[0].end, 1e-9)
    }

    @Test
    fun postrollAfterOutro() {
        val segments = listOf(
            seg(0, 0.0, 10.0, "Content."),
            seg(1, 900.0, 905.0, "Thanks for listening, see you next time."),
            seg(2, 950.0, 1000.0, "Trailing advertisement.")
        )
        val result = AdDetector.detect(segments)
        assertEquals(1, result.adBlocks.size)
        assertEquals(905.0, result.adBlocks[0].start, 1e-9)
        assertEquals(1000.0, result.adBlocks[0].end, 1e-9)
    }

    @Test
    fun gapFillJoinsNearbyBlocks() {
        val segments = listOf(
            seg(0, 0.0, 5.0, "Hi."),
            seg(1, 100.0, 105.0, "This is brought to you by Acme."),
            seg(2, 110.0, 160.0, "Long pitch copy with no trigger words at all."),
            seg(3, 160.0, 165.0, "Click the link in the show notes."),
            seg(4, 400.0, 405.0, "Bye.")
        )
        val result = AdDetector.detect(segments)
        assertEquals(1, result.adBlocks.size)
        assertEquals(98.0, result.adBlocks[0].start, 1e-9)
        assertEquals(167.0, result.adBlocks[0].end, 1e-9)
        assertTrue(result.adBlocks[0].reason.contains("gap-fill"))
    }

    @Test
    fun emptyTranscript() {
        val result = AdDetector.detect(emptyList())
        assertEquals(0, result.adBlocks.size)
        assertEquals(0.0, result.adTimeSaved, 1e-9)
    }
}
