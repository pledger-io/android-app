package com.pledgerio.app.util

import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TagRepository
import com.pledgerio.app.domain.repository.TransactionOutboxRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface SyncRunOutcome {
    data object StaleSession : SyncRunOutcome
    data class Completed(
        val budgetsForAlerts: List<Budget>,
        val yearMonth: YearMonth,
    ) : SyncRunOutcome
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
        if (!runStep(generation) { currencyRepository.sync() }) return SyncRunOutcome.StaleSession
        if (!runStep(generation) { accountRepository.refreshAccountTypes() }) {
            return SyncRunOutcome.StaleSession
        }
        if (!runStep(generation) { categoryRepository.refreshCategories() }) {
            return SyncRunOutcome.StaleSession
        }
        if (!runStep(generation) { tagRepository.refreshTags() }) return SyncRunOutcome.StaleSession
        if (!runStep(generation) { contractRepository.refreshContracts() }) {
            return SyncRunOutcome.StaleSession
        }
        if (!runStep(generation) { budgetRepository.refreshExpenseGroups() }) {
            return SyncRunOutcome.StaleSession
        }
        if (!runStep(generation) { accountRepository.refreshOwnedAccounts() }) {
            return SyncRunOutcome.StaleSession
        }
        if (!runStep(generation) { accountRepository.refreshCounterpartyAccounts() }) {
            return SyncRunOutcome.StaleSession
        }
        if (!runStep(generation) { outboxRepository.flushPending(generation) }) {
            return SyncRunOutcome.StaleSession
        }

        val yearMonth = YearMonth.now()
        var budgetsResult: Resource<BudgetListState>? = null
        if (
            !runStep(generation) {
                budgetsResult = budgetRepository
                    .getBudgets(yearMonth.year, yearMonth.monthValue)
                    .first { it !is Resource.Loading }
            }
        ) {
            return SyncRunOutcome.StaleSession
        }

        val budgets = (budgetsResult as? Resource.Success)
            ?.data
            ?.takeUnless { it.needsInitialSetup }
            ?.budgets
            .orEmpty()
        return SyncRunOutcome.Completed(budgets, yearMonth)
    }

    private suspend fun runStep(
        generation: String,
        action: suspend () -> Unit,
    ): Boolean = sessionDataBarrier.withWorkerStep {
        if (!sessionGuard.isCurrent(generation)) return@withWorkerStep false
        action()
        sessionGuard.isCurrent(generation)
    }
}
