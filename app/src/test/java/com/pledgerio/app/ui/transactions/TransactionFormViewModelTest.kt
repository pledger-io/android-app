package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.ui.transactions.form.TransactionFormLabels
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TransactionFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transactionRepository = mockk<TransactionRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val currencyRepository = mockk<CurrencyRepository>()

    private val checking = Account(id = 1, name = "Checking", typeCode = "default", currency = "EUR")
    private val savings = Account(id = 2, name = "Savings", typeCode = "savings", currency = "EUR")

    private fun setupRepository() {
        every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
        coEvery { accountRepository.getAccountTypes() } returns Resource.Success(
            listOf(AccountTypeOption("default", "Checking")),
        )
        coEvery { accountRepository.getAccountsByTypes(any()) } returns Resource.Success(
            listOf(checking, savings),
        )
    }

    private fun createViewModel(): TransactionFormViewModel {
        setupRepository()
        return TransactionFormViewModel(
            transactionRepository,
            accountRepository,
            currencyRepository,
        )
    }

    @Test
    fun `labels match transaction type`() {
        assertEquals("Paid from", TransactionFormLabels.sourceLabel(TransactionType.CREDIT))
        assertEquals("To (payee)", TransactionFormLabels.targetLabel(TransactionType.CREDIT))
        assertEquals("Received from", TransactionFormLabels.sourceLabel(TransactionType.DEBIT))
        assertEquals("Deposited to", TransactionFormLabels.targetLabel(TransactionType.DEBIT))
    }

    @Test
    fun `preserves owned source when switching expense to transfer`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSourceDropdownSelected(checking.id)
        viewModel.onTypeChanged(TransactionType.TRANSFER)
        advanceUntilIdle()

        assertEquals(checking.id, viewModel.uiState.value.sourceAccountId)
        assertNull(viewModel.uiState.value.targetAccountId)
    }

    @Test
    fun `clears counterparty target when switching expense to income`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSourceDropdownSelected(checking.id)
        viewModel.selectTargetAutocomplete(FilterOption(99, "Shop"))
        viewModel.onTypeChanged(TransactionType.DEBIT)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.targetAccountId)
        assertEquals(AccountInputKind.DEBTOR_AUTOCOMPLETE, viewModel.uiState.value.sourceInputKind)
    }

    @Test
    fun `submit sets validation attempted and blocks when invalid`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationAttempted)
        assertNotNull(viewModel.uiState.value.fieldErrors.amount)
        assertFalse(viewModel.uiState.value.saveSuccess)
    }

    @Test
    fun `setDateYesterday updates date`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDateYesterday()
        assertEquals(LocalDate.now().minusDays(1), viewModel.uiState.value.date)
    }

    @Test
    fun `owned account selection sets currency from account`() = runTest {
        every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
        coEvery { accountRepository.getAccountTypes() } returns Resource.Success(
            listOf(AccountTypeOption("default", "Checking")),
        )
        val usdChecking = checking.copy(currency = "USD")
        coEvery { accountRepository.getAccountsByTypes(any()) } returns Resource.Success(
            listOf(usdChecking, savings),
        )
        val viewModel = TransactionFormViewModel(
            transactionRepository,
            accountRepository,
            currencyRepository,
        )
        advanceUntilIdle()

        viewModel.onSourceDropdownSelected(usdChecking.id)
        assertEquals("USD", viewModel.uiState.value.currency)
    }
}
