package com.rogerbos.shakecast.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = syncEngine.sync().fold(
        onSuccess = { Result.success() },
        onFailure = {
            Timber.w(it, "Sync failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    )
}
