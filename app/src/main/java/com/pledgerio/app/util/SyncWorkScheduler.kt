package com.pledgerio.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface SyncWorkScheduler {
    suspend fun schedule(generation: String)
    suspend fun cancelAndAwait()
}

@Singleton
class WorkManagerSyncWorkScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : SyncWorkScheduler {

    private val workManager = WorkManager.getInstance(context)

    override suspend fun schedule(generation: String) {
        require(generation.isNotBlank()) { "A work generation is required" }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(SyncWorker.INPUT_GENERATION, generation)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        ).await()
    }

    override suspend fun cancelAndAwait() {
        workManager.cancelUniqueWork(WORK_NAME).await()
    }

    companion object {
        internal const val WORK_NAME = "pledger_sync"
        private const val SYNC_INTERVAL_HOURS = 12L
    }
}
