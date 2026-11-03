package com.rogerbos.shakecast.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts", indices = [Index(value = ["feedUrl"], unique = true)])
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val feedUrl: String,
    val itunesId: Long?,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val subscribedAt: Long,
    val lastRefreshedAt: Long?,
    val etag: String?,
    val lastModified: String?,
    val autoSkipAds: Boolean,
    val autoDownload: Boolean,
    /** Last local or remote change to the subscription itself, for last-writer-wins sync. */
    @ColumnInfo(defaultValue = "0") val subscriptionUpdatedAtMs: Long = 0,
    @ColumnInfo(defaultValue = "1") val syncDirty: Boolean = true,
    /** Seconds to jump past when an episode starts from the beginning. */
    @ColumnInfo(defaultValue = "0") val introSkipSec: Int = 0,
    /** Seconds before the end at which the episode counts as finished. */
    @ColumnInfo(defaultValue = "0") val outroSkipSec: Int = 0
)
