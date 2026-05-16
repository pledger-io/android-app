package com.pledgerio.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val contractRepository: ContractRepository,
    private val currencyRepository: CurrencyRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Refresh the catalogs that rarely change first so the UI has fresh data on
            // the next cold start, then accounts and budgets.
            currencyRepository.sync()
            accountRepository.refreshAccountTypes()
            categoryRepository.refreshCategories()
            contractRepository.refreshContracts()
            budgetRepository.refreshExpenseGroups()
            accountRepository.refreshOwnedAccounts()
            accountRepository.refreshCounterpartyAccounts()

            val now = LocalDate.now()
            val budgetsResult = budgetRepository.getBudgets(now.year, now.monthValue).first()

            if (budgetsResult is Resource.Success && !budgetsResult.data.needsInitialSetup) {
                checkBudgetAlerts(budgetsResult.data.budgets)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun checkBudgetAlerts(budgets: List<Budget>) {
        val overBudget = budgets.filter { it.percentUsed >= 0.8f }
        if (overBudget.isNotEmpty()) {
            sendBudgetNotification(overBudget)
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
        private const val WORK_NAME = "pledger_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                12, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
        }
    }
}
