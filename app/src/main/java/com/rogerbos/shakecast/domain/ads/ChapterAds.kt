package com.rogerbos.shakecast.domain.ads

import com.rogerbos.shakecast.domain.model.AdBlock
import com.rogerbos.shakecast.domain.model.Chapter

/** Turns chapters whose titles announce a sponsor read into ad blocks. */
object ChapterAds {
    const val REASON = "chapter"

    /** Bare "ad"/"ads" only count at the start of a title; elsewhere they usually mean a discussion about ads. */
    private val leadingAd = Regex("(?i)^\\W*(ad|ads|advert\\w*|advertisement|sponsor\\w*|promo\\w*|commercial)(\\W|$)")
    private val adPhrase = Regex(
        "(?i)(^|\\W)(ad (break|read|spot|roll)|advertisement|sponsor\\w*|promotion\\w*|a word from|brought to you by|" +
            "paid partnership|commercial break|(mid|pre|post)-?roll)(\\W|$)"
    )

    fun isAdTitle(title: String): Boolean = leadingAd.containsMatchIn(title) || adPhrase.containsMatchIn(title)

    /** @param totalDurationSec bounds a final ad chapter that has no explicit end. */
    fun blocks(chapters: List<Chapter>, totalDurationSec: Double?): List<AdBlock> {
        val sorted = chapters.sortedBy { it.start }
        return sorted.mapIndexedNotNull { i, chapter ->
            if (!isAdTitle(chapter.title)) return@mapIndexedNotNull null
            val end = chapter.end ?: sorted.getOrNull(i + 1)?.start ?: totalDurationSec ?: return@mapIndexedNotNull null
            if (end - chapter.start < 1.0) null else AdBlock(chapter.start, end, REASON)
        }
    }
}
