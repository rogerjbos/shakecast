package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.db.EpisodeEntity
import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.db.PodcastEntity
import com.rogerbos.shakecast.data.download.DownloadScheduler
import com.rogerbos.shakecast.data.download.EpisodeFileStore
import com.rogerbos.shakecast.data.remote.rss.FeedFetchResult
import com.rogerbos.shakecast.data.remote.rss.ParsedFeed
import com.rogerbos.shakecast.data.remote.rss.RssFeedFetcher
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.di.IoDispatcher
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.model.DownloadState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class RefreshFeeds @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val fetcher: RssFeedFetcher,
    private val downloadScheduler: DownloadScheduler,
    private val settingsRepository: SettingsRepository,
    private val fileStore: EpisodeFileStore,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun refreshAll(): Result<Unit> = withContext(io) {
        val podcasts = podcastDao.getAll()
        var failures = 0
        for (podcast in podcasts) {
            refresh(podcast).onFailure { failures++ }
        }
        if (podcasts.isNotEmpty() && failures == podcasts.size) {
            Result.failure(IllegalStateException("All $failures feed refreshes failed"))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun refresh(podcastId: Long): Result<Unit> = withContext(io) {
        val podcast = podcastDao.getById(podcastId) ?: return@withContext Result.failure(IllegalArgumentException("No podcast $podcastId"))
        refresh(podcast)
    }

    private suspend fun refresh(podcast: PodcastEntity): Result<Unit> {
        val fetched = fetcher.fetch(podcast.feedUrl, podcast.etag, podcast.lastModified)
            .onFailure { Timber.w(it, "Refresh failed for %s", podcast.title) }
            .getOrElse { return Result.failure(it) }
        val now = System.currentTimeMillis()
        when (fetched) {
            FeedFetchResult.NotModified -> podcastDao.updateRefreshMeta(podcast.id, now, podcast.etag, podcast.lastModified)
            is FeedFetchResult.Fetched -> {
                applyFeed(podcast, fetched.feed)
                podcastDao.updateRefreshMeta(podcast.id, now, fetched.etag, fetched.lastModified)
            }
        }
        scheduleDownloadsAndPrune(podcast.id)
        return Result.success(Unit)
    }

    private suspend fun applyFeed(podcast: PodcastEntity, feed: ParsedFeed) {
        podcastDao.updateMetadata(
            id = podcast.id,
            title = podcast.title.ifBlank { feed.title },
            author = podcast.author.ifBlank { feed.author },
            description = feed.description.ifBlank { podcast.description },
            artworkUrl = podcast.artworkUrl.ifBlank { feed.artworkUrl }
        )
        val entities = feed.items.map { item ->
            EpisodeEntity(
                id = 0,
                podcastId = podcast.id,
                guid = item.guid,
                title = item.title,
                description = item.description,
                pubDateMs = item.pubDateMs,
                durationSec = item.durationSec,
                enclosureUrl = item.enclosureUrl,
                enclosureType = item.enclosureType,
                enclosureLength = item.enclosureLength,
                transcriptUrl = item.transcriptUrl,
                transcriptType = item.transcriptType,
                downloadState = DownloadState.NONE,
                localFilePath = null,
                downloadedAt = null,
                positionMs = 0,
                isPlayed = false,
                playedAt = null,
                lastPlayedAt = null,
                adSource = AdSource.NONE,
                adBlocksJson = null,
                adResolvedAt = null
            )
        }
        episodeDao.upsertAll(entities)
    }

    suspend fun scheduleDownloadsAndPrune(podcastId: Long) {
        val podcast = podcastDao.getById(podcastId) ?: return
        val settings = settingsRepository.current()
        val keep = episodeDao.latestUnplayedForPodcast(podcastId, settings.episodesToKeepPerShow)
        if (podcast.autoDownload) {
            val toDownload = keep.filter { it.downloadState == DownloadState.NONE || it.downloadState == DownloadState.FAILED }
            downloadScheduler.enqueueAll(toDownload.map { it.id })
        }
        val keepIds = keep.map { it.id }.ifEmpty { listOf(-1L) }
        for (stale in episodeDao.downloadedPlayedNotIn(podcastId, keepIds)) {
            fileStore.delete(stale.localFilePath)
            episodeDao.clearDownload(stale.id)
        }
    }
}
