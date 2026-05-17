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

    @Test
    fun `empty first page does not request further pages`() = runTest {
        coEvery { accountRepository.getAccount(accountId) } returns Resource.Success(account)
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                page = 0,
                pageSize = any(),
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
                page = 0,
                pageSize = any(),
            )
        }
    }

    @Test
    fun `full first page allows loading next page`() = runTest {
        val pageItems = (1..25).map { id ->
            Transaction(
                id = id.toLong(),
                description = "Tx $id",
                amount = 1.0,
                type = TransactionType.CREDIT,
                date = LocalDate.now(),
            )
        }
        coEvery { accountRepository.getAccount(accountId) } returns Resource.Success(account)
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                page = 0,
                pageSize = any(),
            )
        } returns Resource.Success(
            PagedResult(
                items = pageItems,
                totalRecords = 999,
                totalPages = 40,
                pageSize = 25,
            ),
        )
        coEvery {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                page = 1,
                pageSize = any(),
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

        assertEquals(25, viewModel.uiState.value.transactions.size)
        viewModel.loadNextPage()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            transactionRepository.getTransactionsPage(
                startDate = any(),
                endDate = any(),
                accountId = accountId,
                page = 1,
                pageSize = any(),
            )
        }
    }
}
