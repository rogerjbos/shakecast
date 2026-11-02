package com.rogerbos.shakecast.data.sync

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
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic() {
        workManager.cancelUniqueWork(PERIODIC_NAME)
    }

    /** Runs a sync soon; a short delay coalesces bursts such as pause followed by a transition. */
    fun enqueueSoon(delaySeconds: Long = 8) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connected)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(connected).build()
        workManager.enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun isSyncing(): Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(IMMEDIATE_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    companion object {
        private const val PERIODIC_NAME = "sync"
        private const val IMMEDIATE_NAME = "sync-now"
    }
}
