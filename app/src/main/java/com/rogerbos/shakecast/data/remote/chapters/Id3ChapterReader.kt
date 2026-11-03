package com.rogerbos.shakecast.data.remote.chapters

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.rogerbos.shakecast.domain.model.Chapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads ID3v2 CHAP frames from an MP3, local or remote (only the header is fetched). */
class Id3ChapterReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun read(uri: Uri): Result<List<Chapter>> = runCatching {
        val groups = MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(uri)).get()
        val chapters = mutableListOf<Chapter>()
        for (g in 0 until groups.length) {
            val group = groups.get(g)
            for (t in 0 until group.length) {
                val metadata = group.getFormat(t).metadata ?: continue
                for (i in 0 until metadata.length()) {
                    val frame = metadata.get(i) as? ChapterFrame ?: continue
                    val title = (0 until frame.subFrameCount)
                        .map { frame.getSubFrame(it) }
                        .filterIsInstance<TextInformationFrame>()
                        .firstOrNull { it.id == "TIT2" }
                        ?.values?.firstOrNull()
                        ?.trim()
                        ?.ifEmpty { null }
                        ?: frame.chapterId
                    val end = frame.endTimeMs.takeIf { it > frame.startTimeMs }?.let { it / 1000.0 }
                    chapters += Chapter(start = frame.startTimeMs / 1000.0, end = end, title = title)
                }
            }
        }
        chapters.sortedBy { it.start }.distinctBy { it.start }
    }
}
