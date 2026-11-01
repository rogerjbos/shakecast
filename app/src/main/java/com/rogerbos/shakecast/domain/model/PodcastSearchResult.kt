package com.rogerbos.shakecast.domain.model

data class PodcastSearchResult(
    val itunesId: Long?,
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String,
    val genres: List<String>
)
