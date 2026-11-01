package com.rogerbos.shakecast.data.remote.rss

import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

sealed interface FeedFetchResult {
    data object NotModified : FeedFetchResult
    data class Fetched(
        val feed: ParsedFeed,
        val etag: String?,
        val lastModified: String?,
        val finalUrl: String
    ) : FeedFetchResult
}

class RssFeedFetcher @Inject constructor(
    private val client: OkHttpClient,
    private val parser: RssFeedParser
) {
    fun fetch(url: String, etag: String?, lastModified: String?): Result<FeedFetchResult> = runCatching {
        val request = Request.Builder().url(url).apply {
            etag?.let { header("If-None-Match", it) }
            lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 304) return@use FeedFetchResult.NotModified
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IllegalStateException("Empty body for $url")
            val feed = parser.parse(body.byteStream())
            FeedFetchResult.Fetched(
                feed = feed,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                finalUrl = response.request.url.toString()
            )
        }
    }
}
