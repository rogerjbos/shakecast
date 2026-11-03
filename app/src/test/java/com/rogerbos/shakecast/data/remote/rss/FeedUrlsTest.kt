package com.rogerbos.shakecast.data.remote.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUrlsTest {
    @Test
    fun recognisesAddressesButNotSearchTerms() {
        listOf("https://feeds.example.com/show.xml", "http://user:pw@members.example.com/feed", "feeds.example.com/rss", "example.co.uk/podcast/feed.xml")
            .forEach { assertTrue(it, FeedUrls.looksLikeUrl(it)) }
        listOf("Bankless", "the daily", "example.com", "money 2.0", "https://", "")
            .forEach { assertFalse(it, FeedUrls.looksLikeUrl(it)) }
    }

    @Test
    fun normalizeAddsHttpsOnlyWhenMissing() {
        assertEquals("https://feeds.example.com/rss", FeedUrls.normalize("feeds.example.com/rss"))
        assertEquals("http://a.example/feed", FeedUrls.normalize(" http://a.example/feed "))
    }
}
