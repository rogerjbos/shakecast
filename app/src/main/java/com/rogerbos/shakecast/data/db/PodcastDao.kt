package com.rogerbos.shakecast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts")
    suspend fun getAll(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getById(id: Long): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observeById(id: Long): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(podcast: PodcastEntity): Long

    @Update
    suspend fun update(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE podcasts SET lastRefreshedAt = :at, etag = :etag, lastModified = :lastModified WHERE id = :id")
    suspend fun updateRefreshMeta(id: Long, at: Long, etag: String?, lastModified: String?)

    @Query("UPDATE podcasts SET title = :title, author = :author, description = :description, artworkUrl = :artworkUrl WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, author: String, description: String, artworkUrl: String)

    @Query("UPDATE podcasts SET autoSkipAds = :enabled WHERE id = :id")
    suspend fun setAutoSkipAds(id: Long, enabled: Boolean)

    @Query("UPDATE podcasts SET autoDownload = :enabled WHERE id = :id")
    suspend fun setAutoDownload(id: Long, enabled: Boolean)
}
