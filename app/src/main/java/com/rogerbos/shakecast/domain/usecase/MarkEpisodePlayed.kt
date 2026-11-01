package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.download.EpisodeFileStore
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MarkEpisodePlayed @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val fileStore: EpisodeFileStore,
    private val settingsRepository: SettingsRepository,
    private val refreshFeeds: RefreshFeeds,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend operator fun invoke(episodeId: Long) = withContext(io) {
        val episode = episodeDao.getById(episodeId)?.episode ?: return@withContext
        episodeDao.markPlayed(episodeId, System.currentTimeMillis())
        if (settingsRepository.current().autoDeleteWhenPlayed) {
            fileStore.delete(episode.localFilePath)
            episodeDao.clearDownload(episodeId)
        }
        refreshFeeds.scheduleDownloadsAndPrune(episode.podcastId)
    }

    suspend fun deleteDownload(episodeId: Long) = withContext(io) {
        val episode = episodeDao.getById(episodeId)?.episode ?: return@withContext
        fileStore.delete(episode.localFilePath)
        episodeDao.clearDownload(episodeId)
    }
}
