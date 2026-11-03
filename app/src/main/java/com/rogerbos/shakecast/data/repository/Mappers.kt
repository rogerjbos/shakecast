package com.rogerbos.shakecast.data.repository

import com.rogerbos.shakecast.data.db.EpisodeWithPodcast
import com.rogerbos.shakecast.data.db.PodcastEntity
import com.rogerbos.shakecast.domain.model.AdBlock
import com.rogerbos.shakecast.domain.model.Chapter
import com.rogerbos.shakecast.domain.model.Episode
import com.rogerbos.shakecast.domain.model.Podcast
import kotlinx.serialization.json.Json

fun PodcastEntity.toDomain(): Podcast = Podcast(
    id = id,
    feedUrl = feedUrl,
    itunesId = itunesId,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    subscribedAt = subscribedAt,
    lastRefreshedAt = lastRefreshedAt,
    autoSkipAds = autoSkipAds,
    autoDownload = autoDownload,
    introSkipSec = introSkipSec,
    outroSkipSec = outroSkipSec
)

fun EpisodeWithPodcast.toDomain(json: Json): Episode {
    val e = episode
    val blocks = e.adBlocksJson?.let { raw ->
        runCatching { json.decodeFromString<List<AdBlock>>(raw) }.getOrDefault(emptyList())
    }.orEmpty()
    val chapters = e.chaptersJson?.let { raw ->
        runCatching { json.decodeFromString<List<Chapter>>(raw) }.getOrDefault(emptyList())
    }.orEmpty()
    return Episode(
        id = e.id,
        podcastId = e.podcastId,
        podcastTitle = podcastTitle,
        podcastFeedUrl = podcastFeedUrl,
        podcastArtworkUrl = podcastArtworkUrl,
        guid = e.guid,
        title = e.title,
        description = e.description,
        pubDateMs = e.pubDateMs,
        durationSec = e.durationSec,
        enclosureUrl = e.enclosureUrl,
        enclosureType = e.enclosureType,
        transcriptUrl = e.transcriptUrl,
        transcriptType = e.transcriptType,
        downloadState = e.downloadState,
        localFilePath = e.localFilePath,
        positionMs = e.positionMs,
        isPlayed = e.isPlayed,
        lastPlayedAt = e.lastPlayedAt,
        adSource = e.adSource,
        adBlocks = blocks,
        chapters = chapters,
        chaptersResolved = e.chaptersJson != null
    )
}
