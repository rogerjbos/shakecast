package com.rogerbos.shakecast.domain.model

data class Podcast(
    val id: Long,
    val feedUrl: String,
    val itunesId: Long?,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val subscribedAt: Long,
    val lastRefreshedAt: Long?,
    val autoSkipAds: Boolean,
    val autoDownload: Boolean,
    val introSkipSec: Int,
    val outroSkipSec: Int
)
