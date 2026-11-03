package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.repository.QueueRepository
import com.rogerbos.shakecast.data.repository.toDomain
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.domain.model.Episode
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Chooses what plays after an episode ends, or what to resume from the lock screen. */
class PickNextEpisode @Inject constructor(
    private val queueRepository: QueueRepository,
    private val episodeRepository: EpisodeRepository,
    private val episodeDao: EpisodeDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json
) {
    /** Queue head first; otherwise the newest unplayed download when auto-play is on. */
    suspend fun afterEnded(endedId: Long): Episode? {
        queueRepository.head()?.takeIf { it.id != endedId && !it.isPlayed }?.let { return it }
        if (!settingsRepository.current().autoPlayNext) return null
        return episodeDao.newestUnplayedDownloaded(excludeId = endedId)?.toDomain(json)
    }

    /** The last episode in progress, or whatever would play next. */
    suspend fun forResumption(): Episode? {
        episodeRepository.lastPlayed()?.takeIf { !it.isPlayed }?.let { return it }
        return queueRepository.head() ?: episodeDao.newestUnplayedDownloaded(excludeId = -1)?.toDomain(json)
    }
}
