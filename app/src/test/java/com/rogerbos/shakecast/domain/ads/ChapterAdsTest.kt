package com.rogerbos.shakecast.domain.ads

import com.rogerbos.shakecast.domain.model.AdBlock
import com.rogerbos.shakecast.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterAdsTest {

    @Test
    fun recognisesSponsorTitles() {
        listOf("Sponsor: Acme", "Ad break", "Ads", "A word from our sponsors", "Promo", "Brought to you by Acme", "ADVERTISEMENT")
            .forEach { assertTrue(it, ChapterAds.isAdTitle(it)) }
        listOf("Mid-roll", "Thanks to our sponsor", "Sponsored by Acme", "Ad: Acme Widgets")
            .forEach { assertTrue(it, ChapterAds.isAdTitle(it)) }
        listOf(
            "Introduction", "Adam on markets", "Nomad life", "Broadband rollout", "Madison Square",
            "Strip the Ads: AI Apps Will Make It the Baseline", "What Is Podcasting Selling? Value vs. the Ad Load", "Strip Out the Ads — Close"
        ).forEach { assertFalse(it, ChapterAds.isAdTitle(it)) }
    }

    @Test
    fun adChaptersBecomeBlocksBoundedByTheNextChapter() {
        val chapters = listOf(
            Chapter(0.0, null, "Intro"),
            Chapter(100.0, null, "Sponsor read"),
            Chapter(160.0, null, "Interview"),
            Chapter(3500.0, null, "Ad: closing")
        )
        assertEquals(
            listOf(AdBlock(100.0, 160.0, "chapter"), AdBlock(3500.0, 3600.0, "chapter")),
            ChapterAds.blocks(chapters, totalDurationSec = 3600.0)
        )
        assertEquals(listOf(AdBlock(100.0, 160.0, "chapter")), ChapterAds.blocks(chapters, totalDurationSec = null))
    }
}
