package com.rogerbos.shakecast.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.model.DownloadState

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["podcastId", "guid"], unique = true),
        Index(value = ["pubDateMs"])
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String,
    val pubDateMs: Long,
    val durationSec: Int?,
    val enclosureUrl: String,
    val enclosureType: String,
    val enclosureLength: Long?,
    val transcriptUrl: String?,
    val transcriptType: String?,
    val chaptersUrl: String? = null,
    /** Serialised chapters; null until resolved, "[]" when the episode has none. */
    val chaptersJson: String? = null,
    val downloadState: DownloadState,
    val localFilePath: String?,
    val downloadedAt: Long?,
    val positionMs: Long,
    val isPlayed: Boolean,
    val playedAt: Long?,
    val lastPlayedAt: Long?,
    val adSource: AdSource,
    val adBlocksJson: String?,
    val adResolvedAt: Long?,
    /** Last change to positionMs/isPlayed, local or remote, for last-writer-wins sync. */
    @ColumnInfo(defaultValue = "0") val stateUpdatedAtMs: Long = 0,
    @ColumnInfo(defaultValue = "1") val syncDirty: Boolean = true
)
