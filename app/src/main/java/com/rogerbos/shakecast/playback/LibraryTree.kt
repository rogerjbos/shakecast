package com.rogerbos.shakecast.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.data.repository.QueueRepository
import com.rogerbos.shakecast.domain.model.Episode
import com.rogerbos.shakecast.domain.model.Podcast
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The browse tree shown by Android Auto and other media browsers:
 * root → Downloads, Queue, Shows → one folder per show → episodes.
 */
@Singleton
class LibraryTree @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val podcastRepository: PodcastRepository,
    private val queueRepository: QueueRepository,
    private val mediaItemFactory: MediaItemFactory
) {
    /** Titles come from the service so the tree stays free of resources. */
    class Labels(val downloads: String, val queue: String, val shows: String, val appName: String)

    fun root(labels: Labels): MediaItem = folder(ROOT, labels.appName)

    /** Hints that browse screens should be lists; the car UI reads these from the root. */
    fun rootParams(): LibraryParams = LibraryParams.Builder()
        .setExtras(
            Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
            }
        )
        .build()

    suspend fun children(parentId: String, labels: Labels): List<MediaItem>? = when {
        parentId == ROOT -> listOf(folder(DOWNLOADS, labels.downloads), folder(QUEUE, labels.queue), folder(SHOWS, labels.shows))
        parentId == DOWNLOADS -> episodeRepository.observeDownloaded().first().filter { !it.isPlayed }.map(::playable)
        parentId == QUEUE -> queueRepository.observe().first().map(::playable)
        parentId == SHOWS -> podcastRepository.observeAll().first().map(::showFolder)
        parentId.startsWith(SHOW_PREFIX) -> parentId.removePrefix(SHOW_PREFIX).toLongOrNull()?.let { id ->
            episodeRepository.observeForPodcast(id).first().filter { !it.isPlayed }.take(MAX_EPISODES).map(::playable)
        }
        else -> null
    }

    suspend fun item(mediaId: String, labels: Labels): MediaItem? = when {
        mediaId == ROOT -> root(labels)
        mediaId == DOWNLOADS -> folder(DOWNLOADS, labels.downloads)
        mediaId == QUEUE -> folder(QUEUE, labels.queue)
        mediaId == SHOWS -> folder(SHOWS, labels.shows)
        mediaId.startsWith(SHOW_PREFIX) -> mediaId.removePrefix(SHOW_PREFIX).toLongOrNull()?.let { podcastRepository.get(it) }?.let(::showFolder)
        else -> mediaItemFactory.episodeId(mediaId)?.let { episodeRepository.get(it) }?.let(::playable)
    }

    private fun playable(episode: Episode): MediaItem = mediaItemFactory.create(episode)

    private fun showFolder(podcast: Podcast): MediaItem = MediaItem.Builder()
        .setMediaId(SHOW_PREFIX + podcast.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(podcast.title)
                .setArtist(podcast.author)
                .setArtworkUri(podcast.artworkUrl.takeIf { it.isNotBlank() }?.toUri())
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                .build()
        )
        .build()

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                .build()
        )
        .build()

    companion object {
        const val ROOT = "root"
        const val DOWNLOADS = "downloads"
        const val QUEUE = "queue"
        const val SHOWS = "shows"
        const val SHOW_PREFIX = "show/"
        private const val MAX_EPISODES = 100

        // androidx.media.utils.MediaConstants values, inlined to avoid depending on the legacy artifact directly.
        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_LIST = 1
    }
}
