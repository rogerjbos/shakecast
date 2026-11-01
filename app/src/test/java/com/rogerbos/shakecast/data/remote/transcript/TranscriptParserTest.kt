package com.rogerbos.shakecast.data.remote.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptParserTest {

    @Test
    fun parsesSrt() {
        val srt = """
            1
            00:00:00,000 --> 00:00:05,520
            Hello and welcome to the show.

            2
            00:00:05,520 --> 00:00:09,000
            Today we talk
            about markets.
        """.trimIndent()
        val segments = TranscriptParser.parseSrt(srt)
        assertEquals(2, segments.size)
        assertEquals(0.0, segments[0].start, 1e-9)
        assertEquals(5.52, segments[0].end, 1e-9)
        assertEquals("Today we talk\nabout markets.", segments[1].text)
    }

    @Test
    fun parsesVttWithHeaderNotesAndVoiceTags() {
        val vtt = """
            WEBVTT

            NOTE this is a comment

            1
            00:01.000 --> 00:04.000 align:start
            <v Host>Welcome to the show.</v>

            01:02:03.500 --> 01:02:05.000
            Bye.
        """.trimIndent()
        val segments = TranscriptParser.parseVtt(vtt)
        assertEquals(2, segments.size)
        assertEquals(1.0, segments[0].start, 1e-9)
        assertEquals("Welcome to the show.", segments[0].text)
        assertEquals(3723.5, segments[1].start, 1e-9)
    }

    @Test
    fun parsesPodcasting20Json() {
        val json = """{"version":"1.0.0","segments":[{"speaker":"A","startTime":0.5,"endTime":2.0,"body":"Hi"},{"startTime":2.0,"endTime":4.0,"body":"there"}]}"""
        val segments = TranscriptParser.parseJson(json)
        assertEquals(2, segments.size)
        assertEquals("there", segments[1].text)
        assertEquals(4.0, segments[1].end, 1e-9)
    }

    @Test
    fun parsesWhisperJson() {
        val json = """{"language":"en","text":"x","segments":[{"id":0,"start":0.0,"end":3.2,"text":" Hello"}]}"""
        val segments = TranscriptParser.parseJson(json)
        assertEquals(1, segments.size)
        assertEquals(" Hello", segments[0].text)
    }

    @Test
    fun detectsFormatFromContentTypeThenUrlThenBody() {
        assertEquals(TranscriptParser.Format.JSON, TranscriptParser.detectFormat("application/json", null, ""))
        assertEquals(TranscriptParser.Format.VTT, TranscriptParser.detectFormat(null, "https://x/y.vtt", ""))
        assertEquals(TranscriptParser.Format.SRT, TranscriptParser.detectFormat("application/x-subrip", null, ""))
        assertEquals(TranscriptParser.Format.VTT, TranscriptParser.detectFormat(null, null, "WEBVTT\n\n"))
        assertEquals(TranscriptParser.Format.SRT, TranscriptParser.detectFormat(null, null, "1\n00:00:00,000 --> 00:00:01,000\nhi"))
    }

    @Test
    fun coalescesWordLevelSegments() {
        val words = (0 until 40).map { i ->
            com.rogerbos.shakecast.domain.model.TranscriptSegment(i, i * 0.5, i * 0.5 + 0.5, if (i % 10 == 9) "word." else "word")
        }
        val merged = TranscriptCoalescer.coalesce(words)
        assertEquals(2, merged.size)
        assertEquals(0.0, merged[0].start, 1e-9)
        assertEquals(10.0, merged[0].end, 1e-9)
    }
}
