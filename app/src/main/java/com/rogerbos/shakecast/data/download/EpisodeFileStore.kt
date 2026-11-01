package com.rogerbos.shakecast.data.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts")

    fun fileFor(podcastId: Long, episodeId: Long): File =
        File(File(root, podcastId.toString()).apply { mkdirs() }, "$episodeId.mp3")

    fun partFileFor(podcastId: Long, episodeId: Long): File =
        File(fileFor(podcastId, episodeId).path + ".part")

    fun delete(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val f = File(path)
        val deleted = f.delete()
        File(path + ".part").delete()
        return deleted
    }

    fun deleteAllForPodcast(podcastId: Long) {
        File(root, podcastId.toString()).deleteRecursively()
    }

    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Measured duration of the local file, or null when the container cannot be read. */
    fun measureDurationSec(file: File): Int? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { (it / 1000L).toInt() }
        }
    }.onFailure { Timber.w(it, "Could not measure duration of %s", file) }.getOrNull()
}
