package com.rogerbos.shakecast.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {
    @Query("SELECT * FROM subscription_tombstones")
    suspend fun tombstones(): List<SubscriptionTombstoneEntity>

    @Query("SELECT * FROM subscription_tombstones WHERE feedUrl = :feedUrl")
    suspend fun tombstone(feedUrl: String): SubscriptionTombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: SubscriptionTombstoneEntity)

    @Query("DELETE FROM subscription_tombstones WHERE feedUrl = :feedUrl AND deletedAtMs <= :upToMs")
    suspend fun deleteTombstone(feedUrl: String, upToMs: Long)
}
