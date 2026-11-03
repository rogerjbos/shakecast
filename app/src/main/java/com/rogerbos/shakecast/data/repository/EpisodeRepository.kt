package com.rogerbos.shakecast.data.repository

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.domain.model.AdBlock
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.model.Chapter
import com.rogerbos.shakecast.domain.model.Episode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeRepository @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val json: Json
) {
    fun observeLatest(limit: Int = 100): Flow<List<Episode>> =
        episodeDao.observeLatest(limit).map { list -> list.map { it.toDomain(json) } }

    fun observeFiltered(podcastId: Long?, query: String, limit: Int = 300): Flow<List<Episode>> =
        episodeDao.observeFiltered(podcastId, query.trim(), limit).map { list -> list.map { it.toDomain(json) } }

    fun observeForPodcast(podcastId: Long): Flow<List<Episode>> =
        episodeDao.observeForPodcast(podcastId).map { list -> list.map { it.toDomain(json) } }

    fun observeDownloaded(): Flow<List<Episode>> =
        episodeDao.observeDownloaded().map { list -> list.map { it.toDomain(json) } }

    fun observe(id: Long): Flow<Episode?> = episodeDao.observeById(id).map { it?.toDomain(json) }

    suspend fun get(id: Long): Episode? = episodeDao.getById(id)?.toDomain(json)

    suspend fun lastPlayed(): Episode? = episodeDao.lastPlayed()?.toDomain(json)

    suspend fun updatePosition(id: Long, positionMs: Long) =
        episodeDao.updatePosition(id, positionMs, System.currentTimeMillis())

    suspend fun markUnplayed(id: Long) = episodeDao.markUnplayed(id, System.currentTimeMillis())

    suspend fun setChapters(id: Long, chapters: List<Chapter>) = episodeDao.setChapters(id, json.encodeToString(chapters))

    suspend fun setAdBlocks(id: Long, source: AdSource, blocks: List<AdBlock>) =
        episodeDao.setAdBlocks(id, source, if (blocks.isEmpty()) null else json.encodeToString(blocks), System.currentTimeMillis())
}
