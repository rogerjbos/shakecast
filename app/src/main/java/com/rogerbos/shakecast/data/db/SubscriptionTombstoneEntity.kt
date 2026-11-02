package com.rogerbos.shakecast.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Records an unsubscribe until it has been pushed to the sync server. */
@Entity(tableName = "subscription_tombstones")
data class SubscriptionTombstoneEntity(
    @PrimaryKey val feedUrl: String,
    val deletedAtMs: Long
)
