package com.rogerbos.shakecast.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data class Search(val query: String = "") : Route
    @Serializable data class PodcastDetail(val podcastId: Long) : Route
    @Serializable data object NowPlaying : Route
    @Serializable data object Settings : Route
}
