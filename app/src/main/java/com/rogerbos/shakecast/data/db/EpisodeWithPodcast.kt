package com.rogerbos.shakecast.data.db

import androidx.room.Embedded

data class EpisodeWithPodcast(
    @Embedded val episode: EpisodeEntity,
    val podcastTitle: String,
    val podcastArtworkUrl: String,
    val podcastFeedUrl: String
)
