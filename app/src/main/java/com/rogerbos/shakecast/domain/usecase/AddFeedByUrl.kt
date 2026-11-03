package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.remote.rss.FeedFetchResult
import com.rogerbos.shakecast.data.remote.rss.FeedUrls
import com.rogerbos.shakecast.data.remote.rss.RssFeedFetcher
import com.rogerbos.shakecast.di.IoDispatcher
import com.rogerbos.shakecast.domain.model.PodcastSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Subscribes to a feed by address, for shows outside Apple's directory and private member feeds. */
class AddFeedByUrl @Inject constructor(
    private val fetcher: RssFeedFetcher,
    private val subscribeToPodcast: SubscribeToPodcast,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend operator fun invoke(rawUrl: String): Result<PodcastSearchResult> = withContext(io) {
        val url = FeedUrls.normalize(rawUrl)
        val fetched = fetcher.fetch(url, etag = null, lastModified = null).getOrElse { return@withContext Result.failure(it) }
        val feed = (fetched as? FeedFetchResult.Fetched)?.feed
            ?: return@withContext Result.failure(IllegalStateException("No feed at $url"))
        if (feed.items.isEmpty() && feed.title.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Not a podcast feed: $url"))
        }
        val result = PodcastSearchResult(
            itunesId = null,
            title = feed.title.ifBlank { url },
            author = feed.author,
            feedUrl = url,
            artworkUrl = feed.artworkUrl,
            genres = emptyList()
        )
        subscribeToPodcast(result).map { result }
    }
}
