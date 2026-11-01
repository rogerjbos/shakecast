package com.rogerbos.shakecast.data.remote.itunes

import com.rogerbos.shakecast.domain.model.PodcastSearchResult
import kotlinx.serialization.Serializable

@Serializable
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesPodcastDto> = emptyList()
)

@Serializable
data class ItunesPodcastDto(
    val collectionId: Long? = null,
    val collectionName: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val genres: List<String> = emptyList()
) {
    fun toDomain(): PodcastSearchResult? {
        val feed = feedUrl?.takeIf { it.isNotBlank() } ?: return null
        return PodcastSearchResult(
            itunesId = collectionId,
            title = collectionName.orEmpty(),
            author = artistName.orEmpty(),
            feedUrl = feed,
            artworkUrl = artworkUrl600 ?: artworkUrl100.orEmpty(),
            genres = genres
        )
    }
}
