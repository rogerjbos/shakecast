package com.rogerbos.shakecast.data.remote.rss

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val items: List<ParsedItem>
)

data class ParsedItem(
    val guid: String,
    val title: String,
    val description: String,
    val pubDateMs: Long,
    val durationSec: Int?,
    val enclosureUrl: String,
    val enclosureType: String,
    val enclosureLength: Long?,
    val transcriptUrl: String?,
    val transcriptType: String?
)

object RssValues {
    /** Accepts "1:02:03", "62:03", "3723" and "3723.5". */
    fun parseDuration(raw: String?): Int? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val parts = s.split(':')
        return when (parts.size) {
            1 -> s.toDoubleOrNull()?.toInt()
            2 -> {
                val m = parts[0].toIntOrNull() ?: return null
                val sec = parts[1].toDoubleOrNull()?.toInt() ?: return null
                m * 60 + sec
            }
            3 -> {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                val sec = parts[2].toDoubleOrNull()?.toInt() ?: return null
                h * 3600 + m * 60 + sec
            }
            else -> null
        }
    }

    private val dateFormats: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm Z", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.US),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss zzz", Locale.US),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME
    )

    fun parsePubDateMs(raw: String?, fallback: Long = 0L): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return fallback
        for (f in dateFormats) {
            try {
                return ZonedDateTime.parse(s, f).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return fallback
    }

    /** Prefers JSON, then VTT, then SRT, matching the detector's needs. */
    fun transcriptRank(type: String?): Int {
        val t = type?.lowercase().orEmpty()
        return when {
            t.contains("json") -> 0
            t.contains("vtt") -> 1
            t.contains("srt") || t.contains("subrip") -> 2
            t.isEmpty() -> 3
            else -> 4
        }
    }
}
