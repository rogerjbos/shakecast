package com.rogerbos.shakecast.playback

import com.rogerbos.shakecast.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterNavigatorTest {
    private val chapters = listOf(Chapter(0.0, null, "A"), Chapter(100.0, null, "B"), Chapter(200.0, null, "C"))

    @Test
    fun nextSkipsToTheFollowingChapterStart() {
        assertEquals(100_000L, ChapterNavigator.nextStartMs(chapters, 10_000))
        assertEquals(200_000L, ChapterNavigator.nextStartMs(chapters, 99_500))
        assertNull(ChapterNavigator.nextStartMs(chapters, 250_000))
    }

    @Test
    fun previousRestartsTheChapterOrGoesBackOneWhenJustStarted() {
        assertEquals(100_000L, ChapterNavigator.previousStartMs(chapters, 150_000))
        assertEquals(0L, ChapterNavigator.previousStartMs(chapters, 101_000))
        assertEquals(0L, ChapterNavigator.previousStartMs(chapters, 1_000))
    }

    @Test
    fun currentChapterIsTheLastOneStarted() {
        assertEquals("B", ChapterNavigator.current(chapters, 150_000)?.title)
        assertEquals("A", ChapterNavigator.current(chapters, 0)?.title)
    }
}
