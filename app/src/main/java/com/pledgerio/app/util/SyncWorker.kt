package com.pledgerio.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.Budget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncWorkRunner: SyncWorkRunner,
    private val sessionGuard: SyncSessionGuard,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val generation = inputData.getString(INPUT_GENERATION)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        return try {
            when (val outcome = syncWorkRunner.run(generation)) {
                SyncRunOutcome.StaleSession -> Result.success()
                is SyncRunOutcome.Completed -> {
                    checkBudgetAlerts(generation, outcome.budgetsForAlerts)
                    Result.success()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            classifyFailure(e)
        } catch (e: HttpException) {
            classifyFailure(e)
        } catch (e: Exception) {
            classifyFailure(e)
        }
    }

    private suspend fun checkBudgetAlerts(generation: String, budgets: List<Budget>) {
        val overBudget = budgets.filter { it.percentUsed >= 0.8f }
        if (overBudget.isNotEmpty()) {
            sessionGuard.publishIfCurrent(generation) {
                sendBudgetNotification(overBudget)
            }
        }
    }

    private fun sendBudgetNotification(budgets: List<Budget>) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when budgets exceed thresholds"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val budgetNames = budgets.joinToString(", ") { it.name }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Budget Alert")
            .setContentText("${budgets.size} budget(s) over 80%: $budgetNames")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "budget_alerts"
        private const val NOTIFICATION_ID = 1001
        internal const val INPUT_GENERATION = "sync_generation"

        internal fun classifyFailure(error: Throwable): Result = when (error) {
            is IOException -> Result.retry()
            is HttpException -> if (error.code() == 401 || error.code() == 403) {
                Result.failure()
            } else {
                Result.retry()
            }
            else -> Result.failure()
        }

    }
}
