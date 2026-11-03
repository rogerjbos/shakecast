package com.rogerbos.shakecast.domain.model

data class Episode(
    val id: Long,
    val podcastId: Long,
    val podcastTitle: String,
    val podcastArtworkUrl: String,
    val podcastFeedUrl: String,
    val guid: String,
    val title: String,
    val description: String,
    val pubDateMs: Long,
    val durationSec: Int?,
    val enclosureUrl: String,
    val enclosureType: String,
    val transcriptUrl: String?,
    val transcriptType: String?,
    val downloadState: DownloadState,
    val localFilePath: String?,
    val positionMs: Long,
    val isPlayed: Boolean,
    val lastPlayedAt: Long?,
    val adSource: AdSource,
    val adBlocks: List<AdBlock>
) {
    val isDownloaded: Boolean get() = downloadState == DownloadState.DOWNLOADED && localFilePath != null
}
