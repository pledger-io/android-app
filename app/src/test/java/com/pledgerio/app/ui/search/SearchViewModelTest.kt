package com.pledgerio.app.ui.search

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.model.PagedAccounts
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.PagedResult
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SearchDefaults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transactionRepository = mockk<TransactionRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()

    private fun transaction(id: Long = 1L, description: String = "Coffee") = Transaction(
        id = id,
        description = description,
        amount = 3.5,
        type = TransactionType.DEBIT,
        date = LocalDate.now(),
    )

    private fun ownedAccount(id: Long = 10L, name: String = "Checking") =
        Account(id = id, name = name, typeCode = "default")

    private fun category(id: Long = 20L, name: String = "Food") =
        Category(id = id, name = name)

    private fun createViewModel(): SearchViewModel =
        SearchViewModel(transactionRepository, accountRepository, categoryRepository)

    private suspend fun kotlinx.coroutines.test.TestScope.searchAndIdle(
        viewModel: SearchViewModel,
        query: String,
    ) {
        viewModel.onQueryChanged(query)
        advanceTimeBy(SearchDefaults.DEBOUNCE_MS)
        advanceUntilIdle()
    }

    private fun stubHappyPath(
        query: String = "co",
        transactions: List<Transaction> = listOf(transaction()),
        owned: List<Account> = listOf(ownedAccount()),
        parties: List<Account> = emptyList(),
        categories: List<Category> = listOf(category()),
    ) {
        every { accountRepository.observeOwnedAccounts() } returns flowOf(owned)
        coEvery {
            accountRepository.getCounterpartyAccountsPage(0, 25, query)
        } returns Resource.Success(
            PagedAccounts(parties, totalRecords = parties.size.toLong(), offset = 0, pageSize = 25),
        )
        coEvery { categoryRepository.searchCategories(query) } returns Resource.Success(categories)
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                filters = match { it.description == query },
                page = 0,
                pageSize = 20,
            )
        } returns Resource.Success(
            PagedResult(
                items = transactions,
                totalRecords = transactions.size.toLong(),
                totalPages = 1,
                pageSize = 20,
            ),
        )
    }

    @Test
    fun `blank query does not search repositories`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChanged("")
        advanceTimeBy(SearchDefaults.DEBOUNCE_MS)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.transactions.isEmpty())
        assertNull(viewModel.uiState.value.error)
        coVerify(exactly = 0) {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                filters = any(),
                page = any(),
                pageSize = any(),
            )
        }
        verify(exactly = 0) { accountRepository.observeOwnedAccounts() }
        coVerify(exactly = 0) { accountRepository.refreshOwnedAccounts() }
    }

    @Test
    fun `success merges transactions accounts and categories`() = runTest(mainDispatcherRule.dispatcher) {
        val party = Account(id = 11L, name = "Corner Shop", typeCode = "creditor")
        stubHappyPath(
            query = "co",
            transactions = listOf(transaction(description = "Coffee")),
            owned = listOf(ownedAccount(name = "Corporate Checking")),
            parties = listOf(party),
            categories = listOf(category(name = "Coffee shops")),
        )

        val viewModel = createViewModel()
        searchAndIdle(viewModel, "co")

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertNull(state.error)
        assertEquals(1, state.transactions.size)
        assertEquals("Coffee", state.transactions.first().description)
        assertEquals(listOf(10L, 11L), state.accounts.map { it.id })
        assertEquals(1, state.categories.size)
        assertEquals("Coffee shops", state.categories.first().name)
        coVerify(exactly = 0) { accountRepository.refreshOwnedAccounts() }
    }

    @Test
    fun `transaction error still returns accounts and categories`() = runTest(mainDispatcherRule.dispatcher) {
        every { accountRepository.observeOwnedAccounts() } returns flowOf(
            listOf(ownedAccount(name = "Checking")),
        )
        coEvery {
            accountRepository.getCounterpartyAccountsPage(0, 25, "ch")
        } returns Resource.Success(
            PagedAccounts(emptyList(), totalRecords = 0, offset = 0, pageSize = 25),
        )
        coEvery { categoryRepository.searchCategories("ch") } returns Resource.Success(
            listOf(category(name = "Charity")),
        )
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                filters = match { it.description == "ch" },
                page = 0,
                pageSize = 20,
            )
        } returns Resource.Error("Network down")

        val viewModel = createViewModel()
        searchAndIdle(viewModel, "ch")

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertEquals("Network down", state.error)
        assertTrue(state.transactions.isEmpty())
        assertEquals(1, state.accounts.size)
        assertEquals("Checking", state.accounts.first().name)
        assertEquals(1, state.categories.size)
        assertEquals("Charity", state.categories.first().name)
        coVerify(exactly = 0) { accountRepository.refreshOwnedAccounts() }
    }

    @Test
    fun `owned accounts path uses observeOwnedAccounts not refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubHappyPath(
                query = "check",
                owned = listOf(
                    ownedAccount(id = 1L, name = "Checking"),
                    ownedAccount(id = 2L, name = "Savings"),
                ),
            )

            val viewModel = createViewModel()
            searchAndIdle(viewModel, "check")

            assertEquals(listOf(1L), viewModel.uiState.value.accounts.map { it.id })
            verify(atLeast = 1) { accountRepository.observeOwnedAccounts() }
            coVerify(exactly = 0) { accountRepository.refreshOwnedAccounts() }
        }
}
