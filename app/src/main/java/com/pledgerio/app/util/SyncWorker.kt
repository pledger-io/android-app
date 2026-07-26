package com.pledgerio.app.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pledgerio.app.domain.model.Budget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncWorkRunner: SyncWorkRunner,
    private val sessionGuard: SyncSessionGuard,
    private val userPreferences: UserPreferences,
    private val budgetAlertNotifier: BudgetAlertNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val generation = inputData.getString(INPUT_GENERATION)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        return try {
            when (val outcome = syncWorkRunner.run(generation)) {
                SyncRunOutcome.StaleSession -> Result.success()
                is SyncRunOutcome.Completed -> {
                    checkBudgetAlerts(
                        generation = generation,
                        budgets = outcome.budgetsForAlerts,
                        yearMonth = outcome.yearMonth,
                    )
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

    private suspend fun checkBudgetAlerts(
        generation: String,
        budgets: List<Budget>,
        yearMonth: YearMonth,
    ) {
        if (!userPreferences.getBudgetAlertsEnabled()) return

        val threshold = userPreferences.getBudgetAlertThresholdPercent()
        val overBudget = BudgetAlertLogic.filterOverThreshold(budgets, threshold)
        if (overBudget.isEmpty()) {
            userPreferences.clearBudgetAlertFingerprint()
            return
        }

        val fingerprint = BudgetAlertLogic.buildFingerprint(
            yearMonth = yearMonth,
            thresholdPercent = threshold,
            overBudgetIds = overBudget.map { it.id },
        )
        if (!userPreferences.consumeBudgetAlertFingerprint(fingerprint)) return

        sessionGuard.publishIfCurrent(generation) {
            budgetAlertNotifier.notifyOverBudget(overBudget, yearMonth, threshold)
        }
    }

    companion object {
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
