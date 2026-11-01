package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.download.DownloadScheduler
import com.rogerbos.shakecast.data.download.EpisodeFileStore
import com.rogerbos.shakecast.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UnsubscribePodcast @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val fileStore: EpisodeFileStore,
    private val downloadScheduler: DownloadScheduler,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend operator fun invoke(podcastId: Long) = withContext(io) {
        for (episode in episodeDao.allDownloaded().filter { it.podcastId == podcastId }) {
            downloadScheduler.cancel(episode.id)
        }
        fileStore.deleteAllForPodcast(podcastId)
        podcastDao.delete(podcastId)
    }
}
