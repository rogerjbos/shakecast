package com.rogerbos.shakecast.domain.usecase

import androidx.core.net.toUri
import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.remote.chapters.ChaptersFetcher
import com.rogerbos.shakecast.data.remote.chapters.Id3ChapterReader
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.di.IoDispatcher
import com.rogerbos.shakecast.domain.model.Chapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Finds chapters for an episode: the feed's chapters JSON first, then ID3 CHAP frames in the audio. */
class ResolveChapters @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val episodeRepository: EpisodeRepository,
    private val chaptersFetcher: ChaptersFetcher,
    private val id3ChapterReader: Id3ChapterReader,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun resolve(episodeId: Long, force: Boolean = false): List<Chapter> = withContext(io) {
        val episode = episodeDao.getById(episodeId)?.episode ?: return@withContext emptyList()
        if (!force && episode.chaptersJson != null) return@withContext episodeRepository.get(episodeId)?.chapters.orEmpty()

        var chapters: List<Chapter> = emptyList()
        episode.chaptersUrl?.takeIf { it.isNotBlank() }?.let { url ->
            chapters = chaptersFetcher.fetch(url).onFailure { Timber.w(it, "Chapters fetch failed for %s", url) }.getOrDefault(emptyList())
        }
        if (chapters.isEmpty() && episode.enclosureType.let { it.isEmpty() || it.contains("mpeg") || it.contains("mp3") }) {
            val uri = episode.localFilePath?.takeIf { File(it).exists() }?.let { "file://$it".toUri() } ?: episode.enclosureUrl.toUri()
            chapters = id3ChapterReader.read(uri).onFailure { Timber.w(it, "ID3 chapter read failed for %s", uri) }.getOrDefault(emptyList())
        }
        episodeRepository.setChapters(episodeId, chapters)
        Timber.i("Chapters for episode %d: %d", episodeId, chapters.size)
        chapters
    }
}
