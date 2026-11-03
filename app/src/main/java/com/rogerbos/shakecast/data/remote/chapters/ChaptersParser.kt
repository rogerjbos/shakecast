package com.rogerbos.shakecast.data.remote.chapters

import com.rogerbos.shakecast.domain.model.Chapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses the Podcasting 2.0 chapters JSON format (`{"chapters":[{"startTime":..,"title":..}]}`). */
object ChaptersParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): List<Chapter> {
        val rootObj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val array = rootObj["chapters"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
        val chapters = array.mapIndexedNotNull { i, el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val start = obj["startTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapIndexedNotNull null
            if (obj["toc"]?.jsonPrimitive?.booleanOrNull == false) return@mapIndexedNotNull null
            Chapter(
                start = start,
                end = obj["endTime"]?.jsonPrimitive?.doubleOrNull,
                title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null } ?: "Chapter ${i + 1}",
                url = obj["url"]?.jsonPrimitive?.contentOrNull
            )
        }
        return chapters.sortedBy { it.start }.distinctBy { it.start }
    }
}
