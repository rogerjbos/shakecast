package com.rogerbos.shakecast.data.opml

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class OpmlTest {
    private val parser = OpmlParser { KXmlParser() }

    @Test
    fun parsesNestedOutlinesAndDropsDuplicates() {
        val opml = """
            <?xml version="1.0"?>
            <opml version="2.0"><head><title>x</title></head><body>
              <outline text="Finance">
                <outline type="rss" text="Bankless" xmlUrl="https://a.example/feed" htmlUrl="https://a.example"/>
                <outline type="rss" title="No text attr" xmlUrl="https://b.example/feed"/>
              </outline>
              <outline type="rss" text="Dup" xmlUrl="https://a.example/feed"/>
              <outline text="folder without url"/>
            </body></opml>
        """.trimIndent()

        assertEquals(
            listOf(OpmlFeed("Bankless", "https://a.example/feed"), OpmlFeed("No text attr", "https://b.example/feed")),
            parser.parse(StringReader(opml))
        )
    }

    @Test
    fun writtenOpmlRoundTripsThroughTheParserWithEscaping() {
        val feeds = listOf(OpmlFeed("Tom & Jerry \"Show\"", "https://x.example/feed?a=1&b=2"), OpmlFeed("Plain", "https://y.example/rss"))
        val text = OpmlWriter.write(feeds)
        assertEquals(feeds, parser.parse(StringReader(text)))
    }
}
