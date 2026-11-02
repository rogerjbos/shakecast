package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.download.EpisodeFileStore
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.data.sync.SyncScheduler
import com.rogerbos.shakecast.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MarkEpisodePlayed @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val fileStore: EpisodeFileStore,
    private val settingsRepository: SettingsRepository,
    private val refreshFeeds: RefreshFeeds,
    private val syncScheduler: SyncScheduler,
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
        syncScheduler.enqueueSoon()
    }

    /**
     * Applies a state from another device. Returns false when the local row was
     * already at least as new. Deletes the file and backfills when it became played.
     */
    suspend fun applyRemote(episodeId: Long, positionMs: Long, isPlayed: Boolean, playedAtMs: Long?, updatedAtMs: Long): Boolean =
        withContext(io) {
            val episode = episodeDao.getById(episodeId)?.episode ?: return@withContext false
            val updated = episodeDao.applyRemoteState(episodeId, positionMs, isPlayed, playedAtMs, updatedAtMs)
            if (updated == 0) return@withContext false
            if (isPlayed && !episode.isPlayed) {
                if (settingsRepository.current().autoDeleteWhenPlayed) {
                    fileStore.delete(episode.localFilePath)
                    episodeDao.clearDownload(episodeId)
                }
                refreshFeeds.scheduleDownloadsAndPrune(episode.podcastId)
            } else if (!isPlayed && episode.isPlayed) {
                refreshFeeds.scheduleDownloadsAndPrune(episode.podcastId)
            }
            true
        }

    suspend fun deleteDownload(episodeId: Long) = withContext(io) {
        val episode = episodeDao.getById(episodeId)?.episode ?: return@withContext
        fileStore.delete(episode.localFilePath)
        episodeDao.clearDownload(episodeId)
    }
}
