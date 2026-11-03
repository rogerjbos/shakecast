package com.rogerbos.shakecast.data.remote.chapters

import com.rogerbos.shakecast.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaptersParserTest {

    @Test
    fun parsesPodcasting20Chapters() {
        val text = """
            {"version":"1.2.0","chapters":[
              {"startTime":0,"title":"Intro"},
              {"startTime":95.5,"title":"Sponsor: Acme","endTime":155.5,"url":"https://acme.example"},
              {"startTime":60,"title":"hidden","toc":false},
              {"startTime":155.5,"title":"The interview"},
              {"startTime":3000}
            ]}
        """.trimIndent()

        val chapters = ChaptersParser.parse(text)

        assertEquals(
            listOf(
                Chapter(0.0, null, "Intro"),
                Chapter(95.5, 155.5, "Sponsor: Acme", "https://acme.example"),
                Chapter(155.5, null, "The interview"),
                Chapter(3000.0, null, "Chapter 5")
            ),
            chapters
        )
    }

    @Test
    fun garbageYieldsNoChapters() {
        assertTrue(ChaptersParser.parse("not json").isEmpty())
        assertTrue(ChaptersParser.parse("""{"chapters":"nope"}""").isEmpty())
        assertTrue(ChaptersParser.parse("[]").isEmpty())
    }
}
