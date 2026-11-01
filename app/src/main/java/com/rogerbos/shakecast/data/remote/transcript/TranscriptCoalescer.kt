package com.rogerbos.shakecast.data.remote.transcript

import com.rogerbos.shakecast.domain.model.TranscriptSegment

/**
 * Publisher transcripts are often word- or caption-sized. The ad detector was
 * tuned on Whisper segments of roughly 5-10 seconds, so short segments are
 * merged into sentence-sized chunks before detection. This is a deliberate
 * deviation from the Python pipeline, which only ever saw Whisper output.
 */
object TranscriptCoalescer {
    private const val TARGET_SECONDS = 8.0
    private const val SHORT_AVERAGE_SECONDS = 4.0
    private val terminalPunctuation = Regex("[.!?]\\s*$")

    fun needsCoalescing(segments: List<TranscriptSegment>): Boolean {
        if (segments.size < 2) return false
        val average = segments.sumOf { it.end - it.start } / segments.size
        return average < SHORT_AVERAGE_SECONDS
    }

    fun coalesce(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        if (!needsCoalescing(segments)) return segments
        val out = mutableListOf<TranscriptSegment>()
        var start = 0.0
        var end = 0.0
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                out += TranscriptSegment(out.size, start, end, text.toString().trim())
                text.clear()
            }
        }
        for (seg in segments) {
            if (text.isEmpty()) start = seg.start
            if (text.isNotEmpty()) text.append(' ')
            text.append(seg.text.trim())
            end = seg.end
            val longEnough = end - start >= TARGET_SECONDS
            if (longEnough && terminalPunctuation.containsMatchIn(text)) flush()
            else if (end - start >= TARGET_SECONDS * 2) flush()
        }
        flush()
        return out
    }
}
