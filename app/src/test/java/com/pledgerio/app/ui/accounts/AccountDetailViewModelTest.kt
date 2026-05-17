package com.pledgerio.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.PagedResult
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val accountId = 42L

    private val account = Account(
        id = accountId,
        name = "Checking",
        typeCode = "default",
        currency = "EUR",
    )

    private fun transaction(id: Long) = Transaction(
        id = id,
        description = "Tx $id",
        amount = 1.0,
        type = TransactionType.CREDIT,
        date = LocalDate.now(),
    )

    @Test
    fun `empty first page does not request further pages`() = runTest {
        coEvery { accountRepository.getAccount(accountId) } returns Resource.Success(account)
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 0,
            )
        } returns Resource.Success(
            PagedResult(
                items = emptyList(),
                totalRecords = 999,
                totalPages = 40,
                pageSize = 25,
            ),
        )

        val viewModel = AccountDetailViewModel(
            SavedStateHandle(mapOf("accountId" to accountId)),
            accountRepository,
            transactionRepository,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(emptyList<Transaction>(), viewModel.uiState.value.transactions)
        coVerify(exactly = 1) {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 0,
            )
        }
    }

    @Test
    fun `partial first page still loads more when total exceeds loaded count`() = runTest {
        coEvery { accountRepository.getAccount(accountId) } returns Resource.Success(account)
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 0,
            )
        } returns Resource.Success(
            PagedResult(
                items = listOf(transaction(1)),
                totalRecords = 10,
                totalPages = 1,
                pageSize = 25,
            ),
        )
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 1,
            )
        } returns Resource.Success(
            PagedResult(
                items = (2L..10L).map { transaction(it) },
                totalRecords = 10,
                totalPages = 1,
                pageSize = 25,
            ),
        )

        val viewModel = AccountDetailViewModel(
            SavedStateHandle(mapOf("accountId" to accountId)),
            accountRepository,
            transactionRepository,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasMore)
        assertEquals(1, viewModel.uiState.value.transactions.size)

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.transactions.size)
        assertFalse(viewModel.uiState.value.hasMore)
        coVerify {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 1,
            )
        }
    }

    @Test
    fun `full first page uses next offset based on loaded count`() = runTest {
        val pageItems = (1L..25L).map { transaction(it) }
        coEvery { accountRepository.getAccount(accountId) } returns Resource.Success(account)
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 0,
            )
        } returns Resource.Success(
            PagedResult(
                items = pageItems,
                totalRecords = 30,
                totalPages = 2,
                pageSize = 25,
            ),
        )
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 25,
            )
        } returns Resource.Success(
            PagedResult(
                items = (26L..30L).map { transaction(it) },
                totalRecords = 30,
                totalPages = 2,
                pageSize = 25,
            ),
        )

        val viewModel = AccountDetailViewModel(
            SavedStateHandle(mapOf("accountId" to accountId)),
            accountRepository,
            transactionRepository,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasMore)
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(30, viewModel.uiState.value.transactions.size)
        coVerify {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                pageSize = any(),
                offset = 25,
            )
        }
    }
}
