package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transactionRepository = mockk<TransactionRepository>()
    private val accountRepository = mockk<AccountRepository>(relaxed = true)

    private val transaction = Transaction(
        id = 11L,
        description = "Groceries",
        amount = 42.0,
        type = TransactionType.CREDIT,
        date = LocalDate.of(2026, 7, 10),
    )

    private fun createViewModel(): TransactionDetailViewModel {
        coEvery { transactionRepository.getTransaction(11L) } returns Resource.Success(transaction)
        return TransactionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to 11L)),
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
        )
    }

    @Test
    fun `deleteTransaction emits deleted event on success`() = runTest {
        coEvery {
            transactionRepository.deleteTransaction(11L, transaction.date)
        } returns Resource.Success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()
        val event = async { viewModel.deletedEvents.first() }

        viewModel.deleteTransaction()
        advanceUntilIdle()

        assertEquals(11L, event.await().transactionId)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.deleteFailed)
    }

    @Test
    fun `deleteTransaction sets failure flag on error`() = runTest {
        coEvery {
            transactionRepository.deleteTransaction(11L, transaction.date)
        } returns Resource.Error("Failed to delete transaction: 500")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteTransaction()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deleteFailed)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun `deleteTransaction ignores duplicate taps while deleting`() = runTest {
        coEvery {
            transactionRepository.deleteTransaction(11L, transaction.date)
        } coAnswers {
            kotlinx.coroutines.delay(50)
            Resource.Success(Unit)
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteTransaction()
        viewModel.deleteTransaction()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            transactionRepository.deleteTransaction(11L, transaction.date)
        }
    }

    @Test
    fun `clearDeleteError resets failure flag`() = runTest {
        coEvery {
            transactionRepository.deleteTransaction(11L, transaction.date)
        } returns Resource.Error("network")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.deleteTransaction()
        advanceUntilIdle()

        viewModel.clearDeleteError()

        assertFalse(viewModel.uiState.value.deleteFailed)
    }
}
