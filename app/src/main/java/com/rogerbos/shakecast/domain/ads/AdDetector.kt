package com.rogerbos.shakecast.domain.ads

import com.rogerbos.shakecast.domain.model.AdBlock
import com.rogerbos.shakecast.domain.model.AdDetection
import com.rogerbos.shakecast.domain.model.TimeRange
import com.rogerbos.shakecast.domain.model.TranscriptSegment

/**
 * Kotlin port of podcast3r/skip_ads.py. Output must match the Python
 * skip-lists bit for bit, so the ordering of passes is preserved exactly.
 */
object AdDetector {

    private class MutableBlock(var start: Double, var end: Double, var reason: String) {
        fun toAdBlock() = AdBlock(start, end, reason)
    }

    fun detect(
        segments: List<TranscriptSegment>,
        totalDuration: Double = segments.lastOrNull()?.end ?: 0.0,
        pad: Double = 2.0,
        gap: Double = 15.0,
        maxFill: Double = 90.0
    ): AdDetection {
        val keywordBlocks = detectAds(segments, pad, gap, totalDuration)
        val preroll = detectPreroll(segments)
        val postroll = detectPostroll(segments, totalDuration)

        val all = mutableListOf<MutableBlock>()
        preroll?.let { all += it }
        all += keywordBlocks
        postroll?.let { all += it }
        all.sortBy { it.start }

        val merged = fillAdGaps(mergeOverlapping(all, gap), maxFill)
        val blocks = merged.map { it.toAdBlock() }
        val content = computeContentRanges(blocks, totalDuration)
        val adTime = blocks.sumOf { it.duration }
        return AdDetection(totalDuration, blocks, content, adTime)
    }

    private fun matchPatterns(text: String): String? {
        AdPatterns.SPOKEN_URL.find(text)?.let { return "spoken-url: \"${it.value.trim()}\"" }
        AdPatterns.SPELLED_URL.find(text)?.let { return "spelled-url: \"${it.value.trim()}\"" }
        AdPatterns.URL.find(text)?.let { return "url: \"${it.value}\"" }
        AdPatterns.TOLLFREE.find(text)?.let { return "tollfree: \"${it.value}\"" }
        return null
    }

    private fun hasUrl(text: String): Boolean =
        AdPatterns.URL.containsMatchIn(text) ||
            AdPatterns.SPOKEN_URL.containsMatchIn(text) ||
            AdPatterns.SPELLED_URL.containsMatchIn(text)

    private fun detectAds(
        segments: List<TranscriptSegment>,
        pad: Double,
        gap: Double,
        totalDuration: Double
    ): List<MutableBlock> {
        val n = segments.size
        val flags = arrayOfNulls<String>(n)

        for (i in 0 until n) {
            val seg = segments[i]
            val lower = seg.text.lowercase()
            for (kw in AdPhrases.STRONG_KEYWORDS) {
                if (lower.contains(kw)) {
                    flags[i] = "\"$kw\""
                    break
                }
            }
            if (flags[i] != null) continue

            val reason = matchPatterns(seg.text)
            if (reason != null) {
                flags[i] = reason
                continue
            }

            if (hasUrl(seg.text)) {
                for (kw in AdPhrases.CONTEXTUAL_KEYWORDS) {
                    if (lower.contains(kw)) {
                        flags[i] = "\"$kw\" + url"
                        break
                    }
                }
            }
        }

        val flaggedIndices = flags.indices.filter { flags[it] != null }
        for (i in flaggedIndices) {
            for (neighbour in intArrayOf(i - 1, i + 1)) {
                if (neighbour < 0 || neighbour >= n || flags[neighbour] != null) continue
                val lower = segments[neighbour].text.lowercase()
                for (kw in AdPhrases.CONTEXTUAL_KEYWORDS) {
                    if (lower.contains(kw)) {
                        flags[neighbour] = "neighbor: \"$kw\""
                        break
                    }
                }
            }
        }

        val raw = mutableListOf<MutableBlock>()
        for (i in 0 until n) {
            val reason = flags[i] ?: continue
            val seg = segments[i]
            val start = maxOf(0.0, seg.start - pad)
            var end = seg.end + pad
            if (totalDuration != 0.0) end = minOf(end, totalDuration)
            raw += MutableBlock(start, end, reason)
        }
        return mergeOverlapping(raw, gap)
    }

    private fun mergeOverlapping(blocks: List<MutableBlock>, gap: Double): MutableList<MutableBlock> {
        if (blocks.isEmpty()) return mutableListOf()
        val merged = mutableListOf(blocks[0])
        for (b in blocks.drop(1)) {
            val last = merged.last()
            if (b.start <= last.end + gap) {
                last.end = maxOf(last.end, b.end)
                last.reason = last.reason + "; " + b.reason
            } else {
                merged += b
            }
        }
        return merged
    }

    private fun fillAdGaps(blocks: MutableList<MutableBlock>, maxFill: Double): MutableList<MutableBlock> {
        if (blocks.size < 2) return blocks
        val filled = mutableListOf(blocks[0])
        for (b in blocks.drop(1)) {
            val last = filled.last()
            val gap = b.start - last.end
            if (gap > 0 && gap <= maxFill) {
                last.end = b.end
                last.reason += "; gap-fill; " + b.reason
            } else {
                filled += b
            }
        }
        return filled
    }

    private fun detectPreroll(segments: List<TranscriptSegment>, maxSearchSeconds: Double = 300.0): MutableBlock? {
        for (seg in segments) {
            if (seg.start > maxSearchSeconds) break
            val lower = seg.text.lowercase()
            for (marker in AdPhrases.INTRO_MARKERS) {
                if (lower.contains(marker)) {
                    return if (seg.start > 15.0) {
                        MutableBlock(0.0, seg.start, "pre-roll (intro: \"$marker\")")
                    } else {
                        null
                    }
                }
            }
        }
        return null
    }

    private fun detectPostroll(
        segments: List<TranscriptSegment>,
        totalDuration: Double,
        maxSearchSeconds: Double = 300.0
    ): MutableBlock? {
        val cutoff = totalDuration - maxSearchSeconds
        for (seg in segments.asReversed()) {
            if (seg.start < cutoff) break
            val lower = seg.text.lowercase()
            for (marker in AdPhrases.OUTRO_MARKERS) {
                if (lower.contains(marker)) {
                    val postrollStart = seg.end
                    return if (totalDuration - postrollStart > 15.0) {
                        MutableBlock(postrollStart, totalDuration, "post-roll (outro: \"$marker\")")
                    } else {
                        null
                    }
                }
            }
        }
        return null
    }

    private fun computeContentRanges(blocks: List<AdBlock>, totalDuration: Double): List<TimeRange> {
        if (blocks.isEmpty()) return listOf(TimeRange(0.0, totalDuration))
        val ranges = mutableListOf<TimeRange>()
        var cursor = 0.0
        for (b in blocks) {
            if (b.start > cursor) ranges += TimeRange(cursor, b.start)
            cursor = b.end
        }
        if (cursor < totalDuration) ranges += TimeRange(cursor, totalDuration)
        return ranges
    }
}
