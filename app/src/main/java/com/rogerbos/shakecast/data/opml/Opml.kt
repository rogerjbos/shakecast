package com.rogerbos.shakecast.data.opml

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Reader
import javax.inject.Inject

data class OpmlFeed(val title: String, val xmlUrl: String)

/** Reads the subscription outlines of an OPML document, nested folders included. */
class OpmlParser(private val newParser: () -> XmlPullParser) {

    @Inject
    constructor() : this({
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    })

    fun parse(reader: Reader): List<OpmlFeed> {
        val parser = newParser()
        parser.setInput(reader)
        val feeds = LinkedHashMap<String, OpmlFeed>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                val url = parser.getAttributeValue(null, "xmlUrl")?.trim()
                if (!url.isNullOrEmpty() && url !in feeds) {
                    val title = parser.getAttributeValue(null, "text")?.trim().orEmpty()
                        .ifEmpty { parser.getAttributeValue(null, "title")?.trim().orEmpty() }
                        .ifEmpty { url }
                    feeds[url] = OpmlFeed(title, url)
                }
            }
            event = parser.next()
        }
        return feeds.values.toList()
    }
}

/** Writes a flat OPML 2.0 document that any podcast app can import. */
object OpmlWriter {
    fun write(feeds: List<OpmlFeed>, ownerTitle: String = "Shakecast subscriptions"): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n")
        append("  <head><title>").append(escape(ownerTitle)).append("</title></head>\n")
        append("  <body>\n")
        for (feed in feeds) {
            append("    <outline type=\"rss\" text=\"").append(escape(feed.title))
            append("\" title=\"").append(escape(feed.title))
            append("\" xmlUrl=\"").append(escape(feed.xmlUrl)).append("\"/>\n")
        }
        append("  </body>\n")
        append("</opml>\n")
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
