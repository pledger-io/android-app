package com.pledgerio.app.util

import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.model.FlushResult
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TagRepository
import com.pledgerio.app.domain.repository.TransactionOutboxRepository
import java.io.IOException
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

sealed interface SyncRunOutcome {
    data object StaleSession : SyncRunOutcome
    data class RetryableFailure(
        val retryableFailureCount: Int,
        val permanentFailureCount: Int,
    ) : SyncRunOutcome

    data class PermanentFailure(
        val failureCount: Int,
    ) : SyncRunOutcome

    data class Completed(
        val budgetsForAlerts: List<Budget>,
        val yearMonth: YearMonth,
    ) : SyncRunOutcome
}

private enum class SyncStepOutcome {
    SUCCESS,
    STALE_SESSION,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
}

private class SyncFailureAccumulator {
    private var retryableFailureCount = 0
    private var permanentFailureCount = 0

    fun add(outcome: SyncStepOutcome) {
        when (outcome) {
            SyncStepOutcome.RETRYABLE_FAILURE -> retryableFailureCount++
            SyncStepOutcome.PERMANENT_FAILURE -> permanentFailureCount++
            SyncStepOutcome.SUCCESS,
            SyncStepOutcome.STALE_SESSION,
            -> Unit
        }
    }

    fun toRunOutcome(): SyncRunOutcome? = when {
        retryableFailureCount > 0 -> SyncRunOutcome.RetryableFailure(
            retryableFailureCount = retryableFailureCount,
            permanentFailureCount = permanentFailureCount,
        )
        permanentFailureCount > 0 -> SyncRunOutcome.PermanentFailure(permanentFailureCount)
        else -> null
    }
}

class SyncSessionGuard @Inject constructor(
    private val sessionManager: SessionManager,
    private val sessionDataBarrier: SessionDataBarrier,
) {
    fun isCurrent(generation: String): Boolean =
        sessionManager.runIfSyncGenerationCurrent(generation) {}

    suspend fun publishIfCurrent(generation: String, action: () -> Unit): Boolean =
        sessionDataBarrier.withWorkerStep {
            sessionManager.runIfSyncGenerationCurrent(generation, action)
        }
}

class SyncWorkRunner @Inject constructor(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val contractRepository: ContractRepository,
    private val currencyRepository: CurrencyRepository,
    private val tagRepository: TagRepository,
    private val outboxRepository: TransactionOutboxRepository,
    private val sessionGuard: SyncSessionGuard,
    private val sessionDataBarrier: SessionDataBarrier,
) {
    suspend fun run(generation: String): SyncRunOutcome {
        val failures = SyncFailureAccumulator()

        fun record(outcome: SyncStepOutcome): Boolean {
            if (outcome == SyncStepOutcome.STALE_SESSION) return false
            failures.add(outcome)
            return true
        }

        if (!record(runBooleanStep(generation) { currencyRepository.sync() })) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runResourceStep(generation) { accountRepository.refreshAccountTypes() })) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runResourceStep(generation) { categoryRepository.refreshCategories() })) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runResourceStep(generation) { tagRepository.refreshTags() })) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runResourceStep(generation) { contractRepository.refreshContracts() })) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runResourceStep(generation) { budgetRepository.refreshExpenseGroups() })) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runResourceStep(generation) { accountRepository.refreshOwnedAccounts() })) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runResourceStep(generation) {
                accountRepository.refreshCounterpartyAccounts()
            })
        ) {
            return SyncRunOutcome.StaleSession
        }
        if (!record(runFlushStep(generation) { outboxRepository.flushPending(generation) })) {
            return SyncRunOutcome.StaleSession
        }

        val yearMonth = YearMonth.now()
        var budgetsResult: Resource<BudgetListState>? = null
        if (
            !record(runResourceStep(generation) {
                budgetsResult = budgetRepository
                    .getBudgets(yearMonth.year, yearMonth.monthValue)
                    .first { it !is Resource.Loading }
                checkNotNull(budgetsResult)
            })
        ) {
            return SyncRunOutcome.StaleSession
        }

        failures.toRunOutcome()?.let { return it }

        val budgets = (budgetsResult as? Resource.Success)
            ?.data
            ?.takeUnless { it.needsInitialSetup }
            ?.budgets
            .orEmpty()
        return SyncRunOutcome.Completed(budgets, yearMonth)
    }

    private suspend fun runBooleanStep(
        generation: String,
        action: suspend () -> Boolean,
    ): SyncStepOutcome = runStep(generation, action) { successful ->
        if (successful) SyncStepOutcome.SUCCESS else SyncStepOutcome.PERMANENT_FAILURE
    }

    private suspend fun runResourceStep(
        generation: String,
        action: suspend () -> Resource<*>,
    ): SyncStepOutcome = runStep(generation, action) { resource ->
        when (resource) {
            is Resource.Success -> SyncStepOutcome.SUCCESS
            is Resource.Error -> if (resource.isRetryable()) {
                SyncStepOutcome.RETRYABLE_FAILURE
            } else {
                SyncStepOutcome.PERMANENT_FAILURE
            }
            Resource.Loading -> SyncStepOutcome.PERMANENT_FAILURE
        }
    }

    private suspend fun runFlushStep(
        generation: String,
        action: suspend () -> FlushResult,
    ): SyncStepOutcome = runStep(generation, action) { result ->
        when (result) {
            FlushResult.Completed -> SyncStepOutcome.SUCCESS
            FlushResult.AbortedStaleSession -> SyncStepOutcome.STALE_SESSION
            FlushResult.StoppedOnNetworkError -> SyncStepOutcome.RETRYABLE_FAILURE
        }
    }

    private suspend fun <T> runStep(
        generation: String,
        action: suspend () -> T,
        classify: (T) -> SyncStepOutcome,
    ): SyncStepOutcome = sessionDataBarrier.withWorkerStep {
        if (!sessionGuard.isCurrent(generation)) {
            return@withWorkerStep SyncStepOutcome.STALE_SESSION
        }
        val outcome = classify(action())
        if (sessionGuard.isCurrent(generation)) outcome else SyncStepOutcome.STALE_SESSION
    }
}

private fun Resource.Error.isRetryable(): Boolean {
    when (val cause = exception) {
        is IOException -> return true
        is HttpException -> return cause.code().isRetryableHttpStatus()
        null -> Unit
        else -> return false
    }

    val httpStatus = HTTP_STATUS.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (httpStatus != null) return httpStatus.isRetryableHttpStatus()

    // These resources all come from remote refresh operations. Several repositories currently
    // omit the original exception/status, so an unclassified error must remain retryable.
    return true
}

private fun Int.isRetryableHttpStatus(): Boolean = this == 408 || this == 429 || this >= 500

private val HTTP_STATUS = Regex("""(?i)(?:HTTP\s*|:\s*)(\d{3})\b""")
