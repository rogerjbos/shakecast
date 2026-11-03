package com.rogerbos.shakecast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

private const val QUEUE_WITH_PODCAST =
    "SELECT e.*, p.title AS podcastTitle, p.artworkUrl AS podcastArtworkUrl, p.feedUrl AS podcastFeedUrl " +
        "FROM queue_items q JOIN episodes e ON e.id = q.episodeId JOIN podcasts p ON p.id = e.podcastId"

@Dao
interface QueueDao {
    @Query("$QUEUE_WITH_PODCAST ORDER BY q.position ASC")
    fun observeQueue(): Flow<List<EpisodeWithPodcast>>

    @Query("$QUEUE_WITH_PODCAST ORDER BY q.position ASC")
    suspend fun queue(): List<EpisodeWithPodcast>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun items(): List<QueueItemEntity>

    @Query("SELECT COUNT(*) FROM queue_items WHERE episodeId = :episodeId")
    suspend fun count(episodeId: Long): Int

    @Query("SELECT COALESCE(MAX(position), 0) FROM queue_items")
    suspend fun maxPosition(): Long

    @Query("SELECT COALESCE(MIN(position), 0) FROM queue_items")
    suspend fun minPosition(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QueueItemEntity)

    @Query("DELETE FROM queue_items WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: Long)

    @Query("UPDATE queue_items SET position = :position WHERE episodeId = :episodeId")
    suspend fun setPosition(episodeId: Long, position: Long)

    @Transaction
    suspend fun swapWithPrevious(episodeId: Long) {
        val ordered = items()
        val index = ordered.indexOfFirst { it.episodeId == episodeId }
        if (index <= 0) return
        val above = ordered[index - 1]
        val current = ordered[index]
        setPosition(current.episodeId, above.position)
        setPosition(above.episodeId, current.position)
    }
}
