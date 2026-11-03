package com.rogerbos.shakecast.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** An episode the user queued; lower [position] plays first. */
@Entity(
    tableName = "queue_items",
    foreignKeys = [ForeignKey(entity = EpisodeEntity::class, parentColumns = ["id"], childColumns = ["episodeId"], onDelete = ForeignKey.CASCADE)]
)
data class QueueItemEntity(
    @PrimaryKey val episodeId: Long,
    val position: Long,
    val addedAtMs: Long
)
