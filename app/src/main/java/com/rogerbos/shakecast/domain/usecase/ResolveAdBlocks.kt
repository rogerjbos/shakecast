package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.remote.skiplist.SkipListClient
import com.rogerbos.shakecast.data.remote.skiplist.SkipListKey
import com.rogerbos.shakecast.data.remote.transcript.TranscriptCoalescer
import com.rogerbos.shakecast.data.remote.transcript.TranscriptFetcher
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.di.IoDispatcher
import com.rogerbos.shakecast.domain.ads.AdBlockAlignment
import com.rogerbos.shakecast.domain.ads.AdDetector
import com.rogerbos.shakecast.domain.model.AdSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Finds ad blocks for an episode: a skip-list from the Mac first, then the
 * feed's transcript run through the ported detector. Either source is only
 * trusted when its duration matches the local file, otherwise dynamic ad
 * insertion has shifted the timestamps.
 */
class ResolveAdBlocks @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val episodeRepository: EpisodeRepository,
    private val settingsRepository: SettingsRepository,
    private val skipListClient: SkipListClient,
    private val transcriptFetcher: TranscriptFetcher,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun resolve(episodeId: Long, force: Boolean = false, actualDurationSecOverride: Double? = null): AdSource = withContext(io) {
        val row = episodeDao.getById(episodeId) ?: return@withContext AdSource.NONE
        val episode = row.episode
        val resolvedRecently = episode.adResolvedAt?.let { System.currentTimeMillis() - it < RETRY_INTERVAL_MS } == true
        if (!force && episode.adSource != AdSource.NONE && resolvedRecently) return@withContext episode.adSource
        if (!force && episode.adSource == AdSource.NONE && resolvedRecently) return@withContext AdSource.NONE

        val actual = actualDurationSecOverride ?: episode.durationSec?.toDouble() ?: 0.0
        val settings = settingsRepository.current()
        var misaligned = false

        if (settings.skipListBaseUrl.isNotBlank()) {
            val fileName = SkipListKey.fileName(row.podcastTitle, episode.title)
            skipListClient.fetch(settings.skipListBaseUrl, fileName)
                .onFailure { Timber.w(it, "Skip-list fetch failed for %s", fileName) }
                .getOrNull()?.let { dto ->
                    if (AdBlockAlignment.isAligned(dto.totalDuration, actual)) {
                        episodeRepository.setAdBlocks(episodeId, AdSource.SKIPLIST, dto.toAdBlocks())
                        Timber.i("Skip-list applied to episode %d (%d blocks)", episodeId, dto.adBlocks.size)
                        return@withContext AdSource.SKIPLIST
                    }
                    Timber.i("Skip-list misaligned for %d: %.1f vs %.1f", episodeId, dto.totalDuration, actual)
                    misaligned = true
                }
        }

        val transcriptUrl = episode.transcriptUrl
        if (!transcriptUrl.isNullOrBlank()) {
            transcriptFetcher.fetch(transcriptUrl, episode.transcriptType)
                .onFailure { Timber.w(it, "Transcript fetch failed for %s", transcriptUrl) }
                .getOrNull()?.takeIf { it.isNotEmpty() }?.let { raw ->
                    val segments = TranscriptCoalescer.coalesce(raw)
                    val transcriptEnd = segments.last().end
                    if (AdBlockAlignment.isTranscriptAligned(transcriptEnd, actual)) {
                        val detection = AdDetector.detect(segments, totalDuration = transcriptEnd)
                        episodeRepository.setAdBlocks(episodeId, AdSource.TRANSCRIPT, detection.adBlocks)
                        Timber.i("Transcript detection for %d: %d blocks", episodeId, detection.adBlocks.size)
                        return@withContext AdSource.TRANSCRIPT
                    }
                    Timber.i("Transcript misaligned for %d: %.1f vs %.1f", episodeId, transcriptEnd, actual)
                    misaligned = true
                }
        }

        val source = if (misaligned) AdSource.MISALIGNED else AdSource.NONE
        episodeRepository.setAdBlocks(episodeId, source, emptyList())
        source
    }

    companion object {
        private const val RETRY_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
