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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.YearMonth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncWorkRunnerTest {

    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val budgetRepository = mockk<BudgetRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val contractRepository = mockk<ContractRepository>(relaxed = true)
    private val currencyRepository = mockk<CurrencyRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val outboxRepository = mockk<TransactionOutboxRepository>(relaxed = true)
    private val sessionGuard = mockk<SyncSessionGuard>()
    private val sessionDataBarrier = SessionDataBarrier()

    @Test
    fun `stale generation performs no synchronization`() = runTest {
        every { sessionGuard.isCurrent("stale") } returns false

        val outcome = runner().run("stale")

        assertEquals(SyncRunOutcome.StaleSession, outcome)
        coVerify(exactly = 0) { currencyRepository.sync() }
        coVerify(exactly = 0) { accountRepository.refreshAccountTypes() }
        coVerify(exactly = 0) { budgetRepository.getBudgets(any(), any()) }
    }

    @Test
    fun `generation invalidated during sync stops before the next repository`() = runTest {
        every { sessionGuard.isCurrent("active") } returnsMany listOf(true, false)
        coEvery { currencyRepository.sync() } returns true

        val outcome = runner().run("active")

        assertEquals(SyncRunOutcome.StaleSession, outcome)
        coVerify(exactly = 1) { currencyRepository.sync() }
        coVerify(exactly = 0) { accountRepository.refreshAccountTypes() }
        coVerify(exactly = 0) { categoryRepository.refreshCategories() }
    }

    @Test
    fun `current generation completes sync and returns budgets for guarded publication`() =
        runTest {
            val budget = Budget(id = 1, name = "Food", amount = 100.0, spent = 90.0)
            every { sessionGuard.isCurrent("active") } returns true
            coEvery { currencyRepository.sync() } returns true
            coEvery { accountRepository.refreshAccountTypes() } returns Resource.Success(emptyList())
            coEvery { categoryRepository.refreshCategories() } returns Resource.Success(emptyList())
            coEvery { tagRepository.refreshTags() } returns Resource.Success(emptyList())
            coEvery { contractRepository.refreshContracts() } returns Resource.Success(emptyList())
            coEvery { budgetRepository.refreshExpenseGroups() } returns Resource.Success(emptyList())
            coEvery { accountRepository.refreshOwnedAccounts() } returns Resource.Success(emptyList())
            coEvery {
                accountRepository.refreshCounterpartyAccounts()
            } returns Resource.Success(emptyList())
            coEvery { outboxRepository.flushPending("active") } returns
                com.pledgerio.app.domain.model.FlushResult.Completed
            every {
                budgetRepository.getBudgets(any(), any())
            } returns flowOf(
                Resource.Loading,
                Resource.Success(BudgetListState(budgets = listOf(budget))),
            )

            val outcome = runner().run("active")

            assertEquals(
                SyncRunOutcome.Completed(listOf(budget), YearMonth.now()),
                outcome,
            )
            coVerify { outboxRepository.flushPending("active") }
        }

    @Test
    fun `budget fetch skips Loading emission before collecting Success`() = runTest {
        val budget = Budget(id = 2, name = "Rent", amount = 1000.0, spent = 950.0)
        every { sessionGuard.isCurrent("active") } returns true
        coEvery { currencyRepository.sync() } returns true
        coEvery { accountRepository.refreshAccountTypes() } returns Resource.Success(emptyList())
        coEvery { categoryRepository.refreshCategories() } returns Resource.Success(emptyList())
        coEvery { tagRepository.refreshTags() } returns Resource.Success(emptyList())
        coEvery { contractRepository.refreshContracts() } returns Resource.Success(emptyList())
        coEvery { budgetRepository.refreshExpenseGroups() } returns Resource.Success(emptyList())
        coEvery { accountRepository.refreshOwnedAccounts() } returns Resource.Success(emptyList())
        coEvery {
            accountRepository.refreshCounterpartyAccounts()
        } returns Resource.Success(emptyList())
        coEvery { outboxRepository.flushPending("active") } returns
            com.pledgerio.app.domain.model.FlushResult.Completed
        every {
            budgetRepository.getBudgets(any(), any())
        } returns flowOf(
            Resource.Loading,
            Resource.Success(BudgetListState(budgets = listOf(budget))),
        )

        val outcome = runner().run("active")

        assertEquals(
            SyncRunOutcome.Completed(listOf(budget), YearMonth.now()),
            outcome,
        )
    }

    @Test
    fun `session guard does not publish after generation invalidation`() = runTest {
        val sessionManager = mockk<SessionManager>()
        every {
            sessionManager.runIfSyncGenerationCurrent("stale", any())
        } returns false
        var published = false

        val result = SyncSessionGuard(
            sessionManager,
            sessionDataBarrier,
        ).publishIfCurrent("stale") {
            published = true
        }

        assertFalse(result)
        assertFalse(published)
    }

    private fun runner() = SyncWorkRunner(
        accountRepository = accountRepository,
        budgetRepository = budgetRepository,
        categoryRepository = categoryRepository,
        contractRepository = contractRepository,
        currencyRepository = currencyRepository,
        tagRepository = tagRepository,
        outboxRepository = outboxRepository,
        sessionGuard = sessionGuard,
        sessionDataBarrier = sessionDataBarrier,
    )
}
