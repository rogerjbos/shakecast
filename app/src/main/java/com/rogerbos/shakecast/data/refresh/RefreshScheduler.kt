package com.rogerbos.shakecast.data.refresh

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic(intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(intervalHours.toLong(), TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun enqueueNow() {
        val request = OneTimeWorkRequestBuilder<FeedRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun isRefreshing(): Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(IMMEDIATE_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    companion object {
        private const val PERIODIC_NAME = "feed-refresh"
        private const val IMMEDIATE_NAME = "feed-refresh-now"
    }
}
