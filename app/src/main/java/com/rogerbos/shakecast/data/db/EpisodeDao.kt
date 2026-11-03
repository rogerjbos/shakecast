package com.rogerbos.shakecast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.model.DownloadState
import kotlinx.coroutines.flow.Flow

private const val WITH_PODCAST =
    "SELECT e.*, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl, p.feedUrl AS podcastFeedUrl FROM episodes e JOIN podcasts p ON p.id = e.podcastId"

@Dao
interface EpisodeDao {
    @Query("$WITH_PODCAST ORDER BY e.pubDateMs DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EpisodeWithPodcast>>

    @Query("$WITH_PODCAST WHERE e.podcastId = :podcastId ORDER BY e.pubDateMs DESC")
    fun observeForPodcast(podcastId: Long): Flow<List<EpisodeWithPodcast>>

    @Query("$WITH_PODCAST WHERE e.downloadState = 'DOWNLOADED' ORDER BY e.pubDateMs DESC")
    fun observeDownloaded(): Flow<List<EpisodeWithPodcast>>

    @Query("$WITH_PODCAST WHERE e.id = :id")
    fun observeById(id: Long): Flow<EpisodeWithPodcast?>

    @Query("$WITH_PODCAST WHERE e.id = :id")
    suspend fun getById(id: Long): EpisodeWithPodcast?

    @Query("$WITH_PODCAST WHERE e.lastPlayedAt IS NOT NULL ORDER BY e.lastPlayedAt DESC LIMIT 1")
    suspend fun lastPlayed(): EpisodeWithPodcast?

    @Query(
        "$WITH_PODCAST WHERE e.isPlayed = 0 AND e.downloadState = 'DOWNLOADED' AND e.id != :excludeId " +
            "ORDER BY e.pubDateMs DESC LIMIT 1"
    )
    suspend fun newestUnplayedDownloaded(excludeId: Long): EpisodeWithPodcast?

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND guid = :guid")
    suspend fun getByGuid(podcastId: Long, guid: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(episodes: List<EpisodeEntity>): List<Long>

    @Query(
        "UPDATE episodes SET title = :title, description = :description, pubDateMs = :pubDateMs, " +
            "durationSec = COALESCE(CASE WHEN downloadState = 'DOWNLOADED' THEN durationSec ELSE NULL END, :durationSec), " +
            "enclosureUrl = :enclosureUrl, enclosureType = :enclosureType, enclosureLength = :enclosureLength, " +
            "transcriptUrl = :transcriptUrl, transcriptType = :transcriptType, " +
            "chaptersUrl = :chaptersUrl, chaptersJson = CASE WHEN chaptersUrl IS :chaptersUrl THEN chaptersJson ELSE NULL END " +
            "WHERE podcastId = :podcastId AND guid = :guid"
    )
    suspend fun updateMetadata(
        podcastId: Long,
        guid: String,
        title: String,
        description: String,
        pubDateMs: Long,
        durationSec: Int?,
        enclosureUrl: String,
        enclosureType: String,
        enclosureLength: Long?,
        transcriptUrl: String?,
        transcriptType: String?,
        chaptersUrl: String?
    )

    @Transaction
    suspend fun upsertAll(episodes: List<EpisodeEntity>) {
        insertIgnore(episodes)
        for (e in episodes) {
            updateMetadata(
                e.podcastId, e.guid, e.title, e.description, e.pubDateMs, e.durationSec,
                e.enclosureUrl, e.enclosureType, e.enclosureLength, e.transcriptUrl, e.transcriptType, e.chaptersUrl
            )
        }
    }

    @Query("UPDATE episodes SET chaptersJson = :json WHERE id = :id")
    suspend fun setChapters(id: Long, json: String)

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND isPlayed = 0 ORDER BY pubDateMs DESC LIMIT :limit")
    suspend fun latestUnplayedForPodcast(podcastId: Long, limit: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND downloadState = 'DOWNLOADED' AND isPlayed = 1 AND id NOT IN (:keepIds)")
    suspend fun downloadedPlayedNotIn(podcastId: Long, keepIds: List<Long>): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE downloadState = 'DOWNLOADED'")
    suspend fun allDownloaded(): List<EpisodeEntity>

    @Query("UPDATE episodes SET positionMs = :positionMs, lastPlayedAt = :at, stateUpdatedAtMs = :at, syncDirty = 1 WHERE id = :id")
    suspend fun updatePosition(id: Long, positionMs: Long, at: Long)

    @Query("UPDATE episodes SET isPlayed = 1, playedAt = :at, positionMs = 0, stateUpdatedAtMs = :at, syncDirty = 1 WHERE id = :id")
    suspend fun markPlayed(id: Long, at: Long)

    @Query("UPDATE episodes SET isPlayed = 0, playedAt = NULL, stateUpdatedAtMs = :at, syncDirty = 1 WHERE id = :id")
    suspend fun markUnplayed(id: Long, at: Long)

    /** Applies a newer state from another device without marking the row dirty. */
    @Query(
        "UPDATE episodes SET positionMs = :positionMs, isPlayed = :isPlayed, playedAt = :playedAt, " +
            "lastPlayedAt = CASE WHEN :positionMs > 0 OR :isPlayed THEN :updatedAtMs ELSE lastPlayedAt END, " +
            "stateUpdatedAtMs = :updatedAtMs, syncDirty = 0 WHERE id = :id AND stateUpdatedAtMs < :updatedAtMs"
    )
    suspend fun applyRemoteState(id: Long, positionMs: Long, isPlayed: Boolean, playedAt: Long?, updatedAtMs: Long): Int

    @Query(
        "SELECT e.id AS id, p.feedUrl AS feedUrl, e.guid AS guid, e.positionMs AS positionMs, e.isPlayed AS isPlayed, " +
            "e.playedAt AS playedAt, e.lastPlayedAt AS lastPlayedAt, e.stateUpdatedAtMs AS stateUpdatedAtMs " +
            "FROM episodes e JOIN podcasts p ON p.id = e.podcastId " +
            "WHERE e.syncDirty = 1 AND (e.positionMs > 0 OR e.isPlayed = 1 OR e.stateUpdatedAtMs > 0)"
    )
    suspend fun dirtyStates(): List<DirtyEpisodeState>

    @Query("UPDATE episodes SET syncDirty = 0 WHERE id = :id AND stateUpdatedAtMs <= :upToMs")
    suspend fun clearDirty(id: Long, upToMs: Long)

    @Query("UPDATE episodes SET downloadState = :state WHERE id = :id")
    suspend fun setDownloadState(id: Long, state: DownloadState)

    @Query("UPDATE episodes SET downloadState = :state, localFilePath = :path, downloadedAt = :at, durationSec = COALESCE(:durationSec, durationSec) WHERE id = :id")
    suspend fun setDownloaded(id: Long, state: DownloadState, path: String?, at: Long?, durationSec: Int?)

    @Query("UPDATE episodes SET downloadState = 'NONE', localFilePath = NULL, downloadedAt = NULL WHERE id = :id")
    suspend fun clearDownload(id: Long)

    @Query("UPDATE episodes SET adSource = :source, adBlocksJson = :json, adResolvedAt = :at WHERE id = :id")
    suspend fun setAdBlocks(id: Long, source: AdSource, json: String?, at: Long)

    @Query("SELECT COUNT(*) FROM episodes WHERE downloadState = 'DOWNLOADED'")
    fun observeDownloadedCount(): Flow<Int>
}
