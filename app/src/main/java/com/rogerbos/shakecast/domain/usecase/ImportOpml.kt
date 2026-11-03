package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.opml.OpmlFeed
import com.rogerbos.shakecast.data.opml.OpmlParser
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.di.IoDispatcher
import com.rogerbos.shakecast.domain.model.PodcastSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Reader
import javax.inject.Inject

data class OpmlImportReport(val added: Int, val alreadySubscribed: Int, val failed: Int)

class ImportOpml @Inject constructor(
    private val parser: OpmlParser,
    private val podcastRepository: PodcastRepository,
    private val subscribeToPodcast: SubscribeToPodcast,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun parse(reader: Reader): Result<List<OpmlFeed>> = runCatching { parser.parse(reader) }

    /** Subscribes to each feed in turn; [onProgress] receives the count done so far. */
    suspend fun import(feeds: List<OpmlFeed>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): OpmlImportReport = withContext(io) {
        var added = 0
        var skipped = 0
        var failed = 0
        feeds.forEachIndexed { index, feed ->
            if (podcastRepository.findByFeedUrl(feed.xmlUrl) != null) {
                skipped++
            } else {
                val result = PodcastSearchResult(null, feed.title, "", feed.xmlUrl, "", emptyList())
                subscribeToPodcast(result).fold(
                    onSuccess = { added++ },
                    onFailure = { Timber.w(it, "OPML import failed for %s", feed.xmlUrl); failed++ }
                )
            }
            onProgress(index + 1, feeds.size)
        }
        OpmlImportReport(added, skipped, failed)
    }
}
