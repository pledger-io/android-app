package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.ui.transactions.form.TransactionFormLabels
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.domain.model.TransactionTemplate
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.TransactionTemplateStore
import com.pledgerio.app.util.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val budgetRepository = mockk<BudgetRepository>(relaxed = true)
    private val contractRepository = mockk<ContractRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private val transactionTemplateStore = mockk<TransactionTemplateStore>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private val checking = Account(id = 1, name = "Checking", typeCode = "default", currency = "EUR")
    private val savings = Account(id = 2, name = "Savings", typeCode = "savings", currency = "EUR")

    private fun setupRepository() {
        every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
        coEvery { userPreferences.setLastTransactionType(any()) } returns Unit
        every { transactionTemplateStore.templates } returns flowOf(emptyList())
        coEvery { accountRepository.refreshOwnedAccounts() } returns Resource.Success(
            listOf(checking, savings),
        )
    }

    private fun createViewModel(): TransactionFormViewModel {
        setupRepository()
        return TransactionFormViewModel(
            transactionRepository,
            accountRepository,
            currencyRepository,
            categoryRepository,
            budgetRepository,
            contractRepository,
            userPreferences,
            transactionTemplateStore,
            savedStateHandle,
        )
    }

    @Test
    fun `applyTemplate fills form fields`() = runTest {
        val template = TransactionTemplate(
            id = "t1",
            name = "Rent",
            description = "Monthly rent",
            amount = "1200",
            type = TransactionType.CREDIT.name,
            currency = "EUR",
            sourceAccountId = checking.id,
            sourceAccountName = checking.name,
            targetAccountId = null,
            targetAccountName = "",
            tags = listOf("housing"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.applyTemplate(template)
        advanceUntilIdle()

        assertEquals("Monthly rent", viewModel.uiState.value.description)
        assertEquals("1200", viewModel.uiState.value.amount)
        assertEquals(TransactionType.CREDIT, viewModel.uiState.value.type)
        assertEquals(listOf("housing"), viewModel.uiState.value.tags)
        assertEquals(checking.id, viewModel.uiState.value.sourceAccountId)
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
        val usdChecking = checking.copy(currency = "USD")
        coEvery { accountRepository.refreshOwnedAccounts() } returns Resource.Success(
            listOf(usdChecking, savings),
        )
        val viewModel = TransactionFormViewModel(
            transactionRepository,
            accountRepository,
            currencyRepository,
            categoryRepository,
            budgetRepository,
            contractRepository,
            userPreferences,
            transactionTemplateStore,
            savedStateHandle,
        )
        advanceUntilIdle()

        viewModel.onSourceDropdownSelected(usdChecking.id)
        assertEquals("USD", viewModel.uiState.value.currency)
    }

    @Test
    fun `restores last transaction type for new transaction`() = runTest {
        coEvery { userPreferences.getLastTransactionType() } returns TransactionType.TRANSFER
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(TransactionType.TRANSFER, viewModel.uiState.value.type)
    }

    @Test
    fun `onTypeChanged persists last transaction type`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onTypeChanged(TransactionType.DEBIT)
        advanceUntilIdle()

        coVerify { userPreferences.setLastTransactionType(TransactionType.DEBIT) }
    }

    @Test
    fun `partyTypeCodeForNewAccount returns creditor for expense payee`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("creditor", viewModel.partyTypeCodeForNewAccount(isSource = false))
        assertNull(viewModel.partyTypeCodeForNewAccount(isSource = true))
    }
}
