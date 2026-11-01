package com.rogerbos.shakecast.data.db

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
    val autoDownload: Boolean
)
