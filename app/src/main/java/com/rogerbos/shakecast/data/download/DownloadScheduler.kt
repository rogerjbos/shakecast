package com.rogerbos.shakecast.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun enqueue(episodeId: Long, ignoreNetworkPolicy: Boolean = false) {
        val settings = settingsRepository.current()
        val network = if (settings.downloadWifiOnly && !ignoreNetworkPolicy) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(network)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(EpisodeDownloadWorker.KEY_EPISODE_ID to episodeId))
            .addTag(TAG)
            .build()
        episodeDao.setDownloadState(episodeId, DownloadState.QUEUED)
        WorkManager.getInstance(context).enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.KEEP, request)
    }

    suspend fun enqueueAll(episodeIds: List<Long>) {
        for (id in episodeIds) enqueue(id)
    }

    suspend fun cancel(episodeId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(episodeId))
        episodeDao.setDownloadState(episodeId, DownloadState.NONE)
    }

    private fun workName(episodeId: Long) = "download-$episodeId"

    companion object {
        const val TAG = "episode-download"
    }
}
