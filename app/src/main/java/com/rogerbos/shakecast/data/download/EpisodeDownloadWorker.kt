package com.rogerbos.shakecast.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.domain.model.DownloadState
import com.rogerbos.shakecast.domain.usecase.ResolveAdBlocks
import com.rogerbos.shakecast.domain.usecase.ResolveChapters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

@HiltWorker
class EpisodeDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val fileStore: EpisodeFileStore,
    private val client: OkHttpClient,
    private val resolveAdBlocks: ResolveAdBlocks,
    private val resolveChapters: ResolveChapters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val episodeId = inputData.getLong(KEY_EPISODE_ID, -1L)
        val episode = episodeDao.getById(episodeId)?.episode ?: return@withContext Result.failure()
        if (episode.downloadState == DownloadState.DOWNLOADED && episode.localFilePath?.let { File(it).exists() } == true) {
            return@withContext Result.success()
        }

        val target = fileStore.fileFor(episode.podcastId, episode.id)
        val part = fileStore.partFileFor(episode.podcastId, episode.id)
        episodeDao.setDownloadState(episodeId, DownloadState.DOWNLOADING)

        val result = runCatching {
            val request = Request.Builder().url(episode.enclosureUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for ${episode.enclosureUrl}")
                val body = response.body ?: throw IllegalStateException("Empty body")
                part.outputStream().use { out -> body.byteStream().copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
            }
            if (!part.renameTo(target)) throw IllegalStateException("Could not move ${part.name} into place")
            target
        }

        result.fold(
            onSuccess = { file ->
                val duration = fileStore.measureDurationSec(file)
                episodeDao.setDownloaded(episodeId, DownloadState.DOWNLOADED, file.absolutePath, System.currentTimeMillis(), duration)
                Timber.i("Downloaded episode %d (%d bytes)", episodeId, file.length())
                resolveAdBlocks.resolve(episodeId)
                resolveChapters.resolve(episodeId, force = true)
                Result.success()
            },
            onFailure = { error ->
                part.delete()
                Timber.w(error, "Download failed for episode %d (attempt %d)", episodeId, runAttemptCount)
                if (runAttemptCount < MAX_ATTEMPTS - 1) {
                    episodeDao.setDownloadState(episodeId, DownloadState.QUEUED)
                    Result.retry()
                } else {
                    episodeDao.setDownloadState(episodeId, DownloadState.FAILED)
                    Result.failure()
                }
            }
        )
    }

    companion object {
        const val KEY_EPISODE_ID = "episodeId"
        private const val MAX_ATTEMPTS = 3
    }
}
