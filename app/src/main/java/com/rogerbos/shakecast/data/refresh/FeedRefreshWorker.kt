package com.rogerbos.shakecast.data.refresh

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rogerbos.shakecast.domain.usecase.RefreshFeeds
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val refreshFeeds: RefreshFeeds
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val outcome = refreshFeeds.refreshAll()
        return outcome.fold(
            onSuccess = { Result.success() },
            onFailure = { Timber.w(it, "Feed refresh failed"); Result.retry() }
        )
    }
}
