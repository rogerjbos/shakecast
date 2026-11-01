package com.rogerbos.shakecast.data.repository

import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.db.PodcastEntity
import com.rogerbos.shakecast.data.remote.itunes.ItunesSearchApi
import com.rogerbos.shakecast.di.IoDispatcher
import com.rogerbos.shakecast.domain.model.Podcast
import com.rogerbos.shakecast.domain.model.PodcastSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val itunesApi: ItunesSearchApi,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun search(term: String, limit: Int = 25): Result<List<PodcastSearchResult>> = withContext(io) {
        runCatching { itunesApi.search(term = term, limit = limit).results.mapNotNull { it.toDomain() } }
    }

    fun observeAll(): Flow<List<Podcast>> = podcastDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Podcast?> = podcastDao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: Long): Podcast? = podcastDao.getById(id)?.toDomain()

    suspend fun findByFeedUrl(feedUrl: String): Podcast? = podcastDao.getByFeedUrl(feedUrl)?.toDomain()

    /** Returns the podcast id, inserting a new row when the feed is not subscribed yet. */
    suspend fun subscribe(result: PodcastSearchResult): Long {
        podcastDao.getByFeedUrl(result.feedUrl)?.let { return it.id }
        val inserted = podcastDao.insert(
            PodcastEntity(
                id = 0,
                feedUrl = result.feedUrl,
                itunesId = result.itunesId,
                title = result.title,
                author = result.author,
                description = "",
                artworkUrl = result.artworkUrl,
                subscribedAt = System.currentTimeMillis(),
                lastRefreshedAt = null,
                etag = null,
                lastModified = null,
                autoSkipAds = true,
                autoDownload = true
            )
        )
        return if (inserted > 0) inserted else checkNotNull(podcastDao.getByFeedUrl(result.feedUrl)).id
    }

    suspend fun setAutoSkipAds(id: Long, enabled: Boolean) = podcastDao.setAutoSkipAds(id, enabled)

    suspend fun setAutoDownload(id: Long, enabled: Boolean) = podcastDao.setAutoDownload(id, enabled)
}
