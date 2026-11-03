package com.rogerbos.shakecast.data.remote.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

class RssFeedParserTest {

    private fun newParser() = RssFeedParser {
        KXmlParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) }
    }

    private fun fixture(name: String) =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) { "missing $name" }

    @Test
    fun parsesChannelAndItems() {
        val feed = newParser().parse(fixture("sample_feed.xml"))

        assertEquals("Sample Markets Show", feed.title)
        assertEquals("Sample Host", feed.author)
        assertEquals("<p>Weekly <b>markets</b> talk.</p>", feed.description)
        assertEquals("https://example.com/art.jpg", feed.artworkUrl)
        assertEquals(2, feed.items.size)

        val ep2 = feed.items[0]
        assertEquals("ep-2", ep2.guid)
        assertEquals("Episode 2: Rates", ep2.title)
        assertEquals(3723, ep2.durationSec)
        assertEquals("https://cdn.example.com/ep2.mp3", ep2.enclosureUrl)
        assertEquals(12345L, ep2.enclosureLength)
        assertEquals("https://example.com/ep2.json", ep2.transcriptUrl)
        assertEquals("application/json", ep2.transcriptType)
        assertEquals("https://example.com/ep2_chapters.json", ep2.chaptersUrl)
        assertEquals(1730800800000L, ep2.pubDateMs)

        val ep1 = feed.items[1]
        assertEquals("https://cdn.example.com/ep1.mp3", ep1.guid)
        assertEquals("https://cdn.example.com/ep1.mp3", ep1.enclosureUrl)
        assertEquals("First episode", ep1.description)
        assertEquals(3723, ep1.durationSec)
        assertNull(ep1.transcriptUrl)
        assertNull(ep1.chaptersUrl)
        assertEquals(1730107800000L, ep1.pubDateMs)
    }

    @Test
    fun parsesDurations() {
        assertEquals(3723, RssValues.parseDuration("1:02:03"))
        assertEquals(3723, RssValues.parseDuration("62:03"))
        assertEquals(3723, RssValues.parseDuration("3723"))
        assertEquals(3723, RssValues.parseDuration("3723.9"))
        assertNull(RssValues.parseDuration(""))
        assertNull(RssValues.parseDuration("abc"))
    }

    @Test
    fun parsesDateVariants() {
        assertEquals(1730800800000L, RssValues.parsePubDateMs("Tue, 05 Nov 2024 10:00:00 +0000"))
        assertEquals(1730800800000L, RssValues.parsePubDateMs("Tue, 5 Nov 2024 10:00:00 GMT"))
        assertEquals(1730800800000L, RssValues.parsePubDateMs("2024-11-05T10:00:00Z"))
        assertEquals(0L, RssValues.parsePubDateMs("not a date"))
    }
}
