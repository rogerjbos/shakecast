package com.rogerbos.shakecast.data.remote.transcript

import com.rogerbos.shakecast.domain.model.TranscriptSegment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses SRT, WebVTT, Podcasting 2.0 JSON and Whisper JSON transcripts into
 * timed segments.
 */
object TranscriptParser {

    enum class Format { SRT, VTT, JSON }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val timestampRegex = Regex("^(?:(\\d+):)?(\\d+):(\\d+)[,.](\\d+)$")

    fun detectFormat(contentType: String?, urlHint: String?, body: String): Format {
        val ct = contentType?.lowercase().orEmpty()
        val url = urlHint?.lowercase().orEmpty()
        return when {
            ct.contains("json") || url.endsWith(".json") -> Format.JSON
            ct.contains("vtt") || url.endsWith(".vtt") -> Format.VTT
            ct.contains("srt") || ct.contains("subrip") || url.endsWith(".srt") -> Format.SRT
            body.trimStart().startsWith("{") || body.trimStart().startsWith("[") -> Format.JSON
            body.trimStart().startsWith("WEBVTT") -> Format.VTT
            else -> Format.SRT
        }
    }

    fun parse(body: String, contentType: String?, urlHint: String?): List<TranscriptSegment> =
        when (detectFormat(contentType, urlHint, body)) {
            Format.JSON -> parseJson(body)
            Format.VTT -> parseVtt(body)
            Format.SRT -> parseSrt(body)
        }

    /** Port of podcast3r/src/lib/srt.ts. */
    fun parseSrt(text: String): List<TranscriptSegment> {
        val out = mutableListOf<TranscriptSegment>()
        val blocks = text.trim().replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        for (block in blocks) {
            val lines = block.trim().split('\n')
            if (lines.size < 3) continue
            if (lines[0].trim().toIntOrNull() == null) continue
            val cue = parseCueLine(lines[1]) ?: continue
            val content = lines.drop(2).joinToString("\n").trim()
            out += TranscriptSegment(out.size, cue.first, cue.second, content)
        }
        return out
    }

    fun parseVtt(text: String): List<TranscriptSegment> {
        val out = mutableListOf<TranscriptSegment>()
        val normalized = text.replace("\r\n", "\n")
        val blocks = normalized.split(Regex("\n\\s*\n"))
        for (rawBlock in blocks) {
            val block = rawBlock.trim()
            if (block.isEmpty() || block.startsWith("WEBVTT") || block.startsWith("NOTE") || block.startsWith("STYLE") || block.startsWith("REGION")) continue
            val lines = block.split('\n')
            val cueIndex = lines.indexOfFirst { it.contains("-->") }
            if (cueIndex < 0) continue
            val cue = parseCueLine(lines[cueIndex]) ?: continue
            val content = lines.drop(cueIndex + 1)
                .joinToString("\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            out += TranscriptSegment(out.size, cue.first, cue.second, content)
        }
        return out
    }

    fun parseJson(text: String): List<TranscriptSegment> {
        val root = json.parseToJsonElement(text)
        val segmentsArray = when (root) {
            is JsonObject -> root["segments"]?.jsonArray ?: return emptyList()
            else -> root.jsonArray
        }
        val out = mutableListOf<TranscriptSegment>()
        for (element in segmentsArray) {
            val obj = element.jsonObject
            val start = obj.number("startTime") ?: obj.number("start") ?: continue
            val end = obj.number("endTime") ?: obj.number("end") ?: continue
            val body = obj["body"]?.jsonPrimitive?.contentOrNull ?: obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
            out += TranscriptSegment(out.size, start, end, body)
        }
        return out
    }

    private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private fun parseCueLine(line: String): Pair<Double, Double>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null
        val start = parseTimestamp(parts[0].trim()) ?: return null
        val end = parseTimestamp(parts[1].trim().split(Regex("\\s+"))[0]) ?: return null
        return start to end
    }

    private fun parseTimestamp(s: String): Double? {
        val m = timestampRegex.matchEntire(s) ?: return null
        val hours = m.groupValues[1].toIntOrNull() ?: 0
        val minutes = m.groupValues[2].toInt()
        val seconds = m.groupValues[3].toInt()
        val fraction = m.groupValues[4]
        val millis = fraction.padEnd(3, '0').take(3).toInt()
        return hours * 3600.0 + minutes * 60.0 + seconds + millis / 1000.0
    }
}
