package com.rogerbos.shakecast.data.remote.rss

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import javax.inject.Inject

/**
 * Namespace-aware RSS parser for podcast feeds. Field mapping follows
 * podcast3r/podcast_scraper.ts lines 168-255, plus the Podcasting 2.0
 * transcript tag.
 */
class RssFeedParser(private val newParser: () -> XmlPullParser) {

    @Inject
    constructor() : this({
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    })

    private companion object {
        const val ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd"
        const val PODCAST_NS_PREFIX = "https://podcastindex.org/namespace"
        const val MEDIA_NS_PART = "search.yahoo.com/mrss"
        const val MAX_ITEMS = 200
    }

    fun parse(stream: InputStream): ParsedFeed {
        val parser = newParser()
        parser.setInput(stream, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "channel") {
                return readChannel(parser)
            }
            event = parser.next()
        }
        throw IllegalArgumentException("No <channel> element found")
    }

    private fun readChannel(parser: XmlPullParser): ParsedFeed {
        var title = ""
        var author = ""
        var description = ""
        var summary = ""
        var artwork = ""
        var imageUrl = ""
        val items = mutableListOf<ParsedItem>()

        while (parser.next() != XmlPullParser.END_TAG || parser.name != "channel") {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val name = parser.name
            when {
                name == "item" -> {
                    if (items.size < MAX_ITEMS) readItem(parser)?.let { items += it } else skip(parser)
                }
                name == "title" && isPlain(parser) -> title = readText(parser)
                name == "description" && isPlain(parser) -> description = readText(parser)
                name == "author" && isItunes(parser) -> author = readText(parser)
                name == "summary" && isItunes(parser) -> summary = readText(parser)
                name == "image" && isItunes(parser) -> {
                    artwork = parser.getAttributeValue(null, "href").orEmpty()
                    skip(parser)
                }
                name == "image" && isPlain(parser) -> imageUrl = readImageUrl(parser)
                else -> skip(parser)
            }
        }
        return ParsedFeed(
            title = title.trim(),
            author = author.trim(),
            description = (description.ifBlank { summary }).trim(),
            artworkUrl = artwork.ifBlank { imageUrl },
            items = items
        )
    }

    private fun readItem(parser: XmlPullParser): ParsedItem? {
        var guid = ""
        var title = ""
        var description = ""
        var summary = ""
        var pubDate: String? = null
        var duration: String? = null
        var enclosureUrl = ""
        var enclosureType = ""
        var enclosureLength: Long? = null
        var mediaUrl = ""
        var mediaType = ""
        var transcriptUrl: String? = null
        var transcriptType: String? = null
        var transcriptRank = Int.MAX_VALUE
        var chaptersUrl: String? = null

        while (parser.next() != XmlPullParser.END_TAG || parser.name != "item") {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val name = parser.name
            when {
                name == "guid" && isPlain(parser) -> guid = readText(parser)
                name == "title" && isPlain(parser) -> title = readText(parser)
                name == "description" && isPlain(parser) -> description = readText(parser)
                name == "summary" && isItunes(parser) -> summary = readText(parser)
                name == "pubDate" && isPlain(parser) -> pubDate = readText(parser)
                name == "duration" && isItunes(parser) -> duration = readText(parser)
                name == "enclosure" && isPlain(parser) -> {
                    val url = parser.getAttributeValue(null, "url").orEmpty()
                    val type = parser.getAttributeValue(null, "type").orEmpty()
                    if (enclosureUrl.isEmpty() && (type.startsWith("audio") || url.lowercase().endsWith(".mp3") || type.isEmpty())) {
                        enclosureUrl = url
                        enclosureType = type
                        enclosureLength = parser.getAttributeValue(null, "length")?.toLongOrNull()
                    }
                    skip(parser)
                }
                name == "content" && isMedia(parser) -> {
                    if (mediaUrl.isEmpty()) {
                        mediaUrl = parser.getAttributeValue(null, "url").orEmpty()
                        mediaType = parser.getAttributeValue(null, "type").orEmpty()
                    }
                    skip(parser)
                }
                name == "chapters" && isPodcast(parser) -> {
                    val url = parser.getAttributeValue(null, "url")
                    val type = parser.getAttributeValue(null, "type").orEmpty().lowercase()
                    if (!url.isNullOrBlank() && (type.isEmpty() || type.contains("json"))) chaptersUrl = url
                    skip(parser)
                }
                name == "transcript" && isPodcast(parser) -> {
                    val url = parser.getAttributeValue(null, "url")
                    val type = parser.getAttributeValue(null, "type")
                    val rank = RssValues.transcriptRank(type)
                    if (!url.isNullOrBlank() && rank < transcriptRank) {
                        transcriptUrl = url
                        transcriptType = type
                        transcriptRank = rank
                    }
                    skip(parser)
                }
                else -> skip(parser)
            }
        }

        val audioUrl = enclosureUrl.ifEmpty { mediaUrl }
        if (audioUrl.isEmpty()) return null
        val audioType = enclosureType.ifEmpty { mediaType }
        val resolvedGuid = guid.trim().ifEmpty { audioUrl }.ifEmpty { title }
        return ParsedItem(
            guid = resolvedGuid,
            title = title.trim(),
            description = (description.ifBlank { summary }).trim(),
            pubDateMs = RssValues.parsePubDateMs(pubDate),
            durationSec = RssValues.parseDuration(duration),
            enclosureUrl = audioUrl,
            enclosureType = audioType,
            enclosureLength = enclosureLength,
            transcriptUrl = transcriptUrl,
            transcriptType = transcriptType,
            chaptersUrl = chaptersUrl
        )
    }

    private fun readImageUrl(parser: XmlPullParser): String {
        var url = ""
        while (parser.next() != XmlPullParser.END_TAG || parser.name != "image") {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "url") url = readText(parser) else skip(parser)
        }
        return url.trim()
    }

    private fun isPlain(parser: XmlPullParser): Boolean = parser.namespace.isNullOrEmpty()

    private fun isItunes(parser: XmlPullParser): Boolean =
        parser.namespace == ITUNES_NS || parser.prefix == "itunes"

    private fun isPodcast(parser: XmlPullParser): Boolean =
        parser.namespace.orEmpty().startsWith(PODCAST_NS_PREFIX) || parser.prefix == "podcast"

    private fun isMedia(parser: XmlPullParser): Boolean =
        parser.namespace.orEmpty().contains(MEDIA_NS_PART) || parser.prefix == "media"

    /** Reads text and CDATA until the matching end tag, ignoring nested elements. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (depth == 1) sb.append(parser.text)
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private fun skip(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
