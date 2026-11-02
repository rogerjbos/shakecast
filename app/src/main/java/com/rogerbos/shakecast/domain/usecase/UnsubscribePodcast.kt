package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.db.SubscriptionTombstoneEntity
import com.rogerbos.shakecast.data.db.SyncDao
import com.rogerbos.shakecast.data.download.DownloadScheduler
import com.rogerbos.shakecast.data.download.EpisodeFileStore
import com.rogerbos.shakecast.data.sync.SyncScheduler
import com.rogerbos.shakecast.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UnsubscribePodcast @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val fileStore: EpisodeFileStore,
    private val downloadScheduler: DownloadScheduler,
    private val syncDao: SyncDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** @param recordTombstone false when the unsubscribe came from another device via sync. */
    suspend operator fun invoke(podcastId: Long, recordTombstone: Boolean = true) = withContext(io) {
        val podcast = podcastDao.getById(podcastId) ?: return@withContext
        for (episode in episodeDao.allDownloaded().filter { it.podcastId == podcastId }) {
            downloadScheduler.cancel(episode.id)
        }
        fileStore.deleteAllForPodcast(podcastId)
        podcastDao.delete(podcastId)
        if (recordTombstone) {
            syncDao.insertTombstone(SubscriptionTombstoneEntity(podcast.feedUrl, System.currentTimeMillis()))
            syncScheduler.enqueueSoon()
        }
    }
}
