package com.pledgerio.app.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.pledgerio.app.domain.model.FlushResult
import com.pledgerio.app.domain.repository.TransactionOutboxRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

interface OutboxFlushScheduler {
    suspend fun schedule(generation: String)
    suspend fun cancelAndAwait()
}

@Singleton
class WorkManagerOutboxFlushScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : OutboxFlushScheduler {

    private val workManager = WorkManager.getInstance(context)

    override suspend fun schedule(generation: String) {
        require(generation.isNotBlank()) { "A work generation is required" }
        val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(OutboxFlushWorker.INPUT_GENERATION, generation)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        ).await()
    }

    override suspend fun cancelAndAwait() {
        workManager.cancelUniqueWork(WORK_NAME).await()
    }

    companion object {
        internal const val WORK_NAME = "pledger_outbox_flush"
    }
}

@HiltWorker
class OutboxFlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val outboxRepository: TransactionOutboxRepository,
    private val sessionDataBarrier: SessionDataBarrier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val generation = inputData.getString(INPUT_GENERATION)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        return try {
            sessionDataBarrier.withWorkerStep {
                when (outboxRepository.flushPending(generation)) {
                    FlushResult.Completed,
                    FlushResult.AbortedStaleSession,
                    -> Result.success()
                    FlushResult.StoppedOnNetworkError -> Result.retry()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        internal const val INPUT_GENERATION = "sync_generation"
    }
}
