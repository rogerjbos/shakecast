package com.rogerbos.shakecast.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A span the user skipped by hand, keyed by feed and guid so it survives
 * episode rows being pruned and can be shared between devices.
 */
@Entity(
    tableName = "manual_skips",
    indices = [Index(value = ["feedUrl", "guid", "startMs"], unique = true), Index(value = ["feedUrl", "guid"])]
)
data class ManualSkipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val feedUrl: String,
    val guid: String,
    val startMs: Long,
    val endMs: Long,
    val updatedAtMs: Long,
    val deleted: Boolean,
    val syncDirty: Boolean
)
