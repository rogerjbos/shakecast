package com.rogerbos.shakecast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualSkipDao {
    @Query("SELECT * FROM manual_skips WHERE feedUrl = :feedUrl AND guid = :guid AND deleted = 0 ORDER BY startMs")
    fun observeFor(feedUrl: String, guid: String): Flow<List<ManualSkipEntity>>

    @Query("SELECT * FROM manual_skips WHERE feedUrl = :feedUrl AND guid = :guid AND startMs = :startMs")
    suspend fun find(feedUrl: String, guid: String, startMs: Long): ManualSkipEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(skip: ManualSkipEntity): Long

    @Query("UPDATE manual_skips SET endMs = :endMs, updatedAtMs = :updatedAtMs, deleted = :deleted, syncDirty = :dirty WHERE id = :id")
    suspend fun update(id: Long, endMs: Long, updatedAtMs: Long, deleted: Boolean, dirty: Boolean)

    @Query("UPDATE manual_skips SET deleted = 1, updatedAtMs = :at, syncDirty = 1 WHERE feedUrl = :feedUrl AND guid = :guid AND deleted = 0")
    suspend fun forgetAll(feedUrl: String, guid: String, at: Long)

    @Query("SELECT * FROM manual_skips WHERE syncDirty = 1")
    suspend fun dirty(): List<ManualSkipEntity>

    @Query("UPDATE manual_skips SET syncDirty = 0 WHERE id = :id AND updatedAtMs <= :upToMs")
    suspend fun clearDirty(id: Long, upToMs: Long)

    /** Re-publishes every span, used after signing in to a new account. */
    @Query("UPDATE manual_skips SET syncDirty = 1")
    suspend fun markAllDirty()
}
