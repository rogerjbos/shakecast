package com.rogerbos.shakecast.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.rogerbos.shakecast.domain.model.Episode
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaItemFactory @Inject constructor() {
    fun create(episode: Episode): MediaItem {
        val uri: Uri = episode.localFilePath
            ?.takeIf { episode.isDownloaded && File(it).exists() }
            ?.let { Uri.fromFile(File(it)) }
            ?: episode.enclosureUrl.toUri()
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastTitle)
            .setAlbumTitle(episode.podcastTitle)
            .setArtworkUri(episode.podcastArtworkUrl.takeIf { it.isNotBlank() }?.toUri())
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun episodeId(mediaId: String?): Long? = mediaId?.toLongOrNull()
}
