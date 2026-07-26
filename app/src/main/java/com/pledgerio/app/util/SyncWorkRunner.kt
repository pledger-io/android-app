package com.pledgerio.app.util

import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TagRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface SyncRunOutcome {
    data object StaleSession : SyncRunOutcome
    data class Completed(val budgetsForAlerts: List<Budget>) : SyncRunOutcome
}

class SyncSessionGuard @Inject constructor(
    private val sessionManager: SessionManager,
) {
    fun isCurrent(generation: String): Boolean =
        sessionManager.runIfSyncGenerationCurrent(generation) {}

    fun publishIfCurrent(generation: String, action: () -> Unit): Boolean =
        sessionManager.runIfSyncGenerationCurrent(generation, action)
}

class SyncWorkRunner @Inject constructor(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val contractRepository: ContractRepository,
    private val currencyRepository: CurrencyRepository,
    private val tagRepository: TagRepository,
    private val sessionGuard: SyncSessionGuard,
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

        if (!sessionGuard.isCurrent(generation)) return SyncRunOutcome.StaleSession
        val now = java.time.LocalDate.now()
        val budgetsResult = budgetRepository.getBudgets(now.year, now.monthValue).first()
        if (!sessionGuard.isCurrent(generation)) return SyncRunOutcome.StaleSession

        val budgets = (budgetsResult as? Resource.Success)
            ?.data
            ?.takeUnless { it.needsInitialSetup }
            ?.budgets
            .orEmpty()
        return SyncRunOutcome.Completed(budgets)
    }

    private suspend fun runStep(
        generation: String,
        action: suspend () -> Unit,
    ): Boolean {
        if (!sessionGuard.isCurrent(generation)) return false
        action()
        return sessionGuard.isCurrent(generation)
    }
}
