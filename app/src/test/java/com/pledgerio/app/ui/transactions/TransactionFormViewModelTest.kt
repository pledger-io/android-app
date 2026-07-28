package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.BudgetExpense
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.model.CreateOutcome
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.OutboxStatus
import com.pledgerio.app.domain.model.PendingTransactionCreate
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionClassificationSuggestion
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TagRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.domain.model.Tag
import com.pledgerio.app.ui.transactions.form.AutoClassifyStatus
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.domain.model.TransactionTemplate
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.TransactionTemplateStore
import com.pledgerio.app.util.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private val transactionTemplateStore = mockk<TransactionTemplateStore>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private val checking = Account(id = 1, name = "Checking", typeCode = "default", currency = "EUR")
    private val savings = Account(id = 2, name = "Savings", typeCode = "savings", currency = "EUR")

    private fun setupRepository(
        experienceMode: FinanceExperienceMode = FinanceExperienceMode.GUIDED,
        ownedAccounts: List<Account> = listOf(checking, savings),
    ) {
        every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
        every { userPreferences.financeExperienceMode } returns MutableStateFlow(experienceMode)
        coEvery { userPreferences.setLastTransactionType(any()) } returns Unit
        every { transactionTemplateStore.templates } returns flowOf(emptyList())
        every { tagRepository.observeTags() } returns flowOf(
            listOf(Tag("housing"), Tag("travel")),
        )
        every { tagRepository.observeMatching(any()) } returns flowOf(emptyList())
        coEvery { tagRepository.refreshTags() } returns Resource.Success(emptyList())
        every { categoryRepository.observeMatching(any()) } returns flowOf(emptyList())
        every { budgetRepository.observeExpenseGroups(any()) } returns flowOf(emptyList())
        every { contractRepository.observeMatching(any()) } returns flowOf(emptyList())
        coEvery { accountRepository.refreshOwnedAccounts() } returns Resource.Success(ownedAccounts)
        coEvery { accountRepository.searchAccounts(any(), any(), any()) } returns Resource.Success(emptyList())
    }

    private fun createViewModel(
        experienceMode: FinanceExperienceMode = FinanceExperienceMode.GUIDED,
        ownedAccounts: List<Account> = listOf(checking, savings),
        savedState: SavedStateHandle = savedStateHandle,
    ): TransactionFormViewModel {
        setupRepository(experienceMode = experienceMode, ownedAccounts = ownedAccounts)
        return TransactionFormViewModel(
            transactionRepository,
            accountRepository,
            currencyRepository,
            categoryRepository,
            budgetRepository,
            contractRepository,
            tagRepository,
            userPreferences,
            transactionTemplateStore,
            savedState,
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
        assertFalse(viewModel.uiState.value.canSubmit)
        assertFalse(viewModel.uiState.value.saveSuccess)
    }

    @Test
    fun `non-finite and non-positive transaction amounts are invalid`() {
        listOf("Infinity", "NaN", "0", "-1").forEach { amount ->
            val state = TransactionFormUiState(
                description = "Transfer",
                amount = amount,
                type = TransactionType.TRANSFER,
                sourceAccountId = checking.id,
                targetAccountId = savings.id,
            )

            assertFalse("$amount must be rejected", state.isValid)
        }
    }

    @Test
    fun `transfer between the same account is invalid`() {
        val state = TransactionFormUiState(
            description = "Transfer",
            amount = "25",
            type = TransactionType.TRANSFER,
            sourceAccountId = checking.id,
            targetAccountId = checking.id,
        )

        assertFalse(state.isValid)
        assertFalse(state.canSubmit)
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
        val usdChecking = checking.copy(currency = "USD")
        val viewModel = createViewModel(ownedAccounts = listOf(usdChecking, savings))
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
    fun `addTag creates new tag via repository then adds chip`() = runTest {
        coEvery { tagRepository.createTag("groceries") } returns Resource.Success(Tag("groceries"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addTag("groceries")
        advanceUntilIdle()

        assertEquals(listOf("groceries"), viewModel.uiState.value.tags)
        coVerify { tagRepository.createTag("groceries") }
    }

    @Test
    fun `selectTagFromSuggestion skips create when tag is in catalog`() = runTest {
        every { tagRepository.observeTags() } returns flowOf(listOf(Tag("travel")))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectTagFromSuggestion("travel")
        advanceUntilIdle()

        assertEquals(listOf("travel"), viewModel.uiState.value.tags)
        coVerify(exactly = 0) { tagRepository.createTag(any()) }
    }

    @Test
    fun `partyTypeCodeForNewAccount returns creditor for expense payee`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("creditor", viewModel.partyTypeCodeForNewAccount(isSource = false))
        assertNull(viewModel.partyTypeCodeForNewAccount(isSource = true))
    }

    @Test
    fun `guided mode keeps optional sections collapsed by default`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(FinanceExperienceMode.GUIDED, viewModel.uiState.value.financeExperienceMode)
        assertFalse(viewModel.uiState.value.moreOptionsExpanded)
        assertFalse(viewModel.uiState.value.showTemplatesSection)
    }

    @Test
    fun `power mode expands optional sections and shows templates by default`() = runTest {
        val viewModel = createViewModel(experienceMode = FinanceExperienceMode.POWER)
        advanceUntilIdle()

        assertEquals(FinanceExperienceMode.POWER, viewModel.uiState.value.financeExperienceMode)
        assertTrue(viewModel.uiState.value.moreOptionsExpanded)
        assertTrue(viewModel.uiState.value.showTemplatesSection)
    }

    @Test
    fun `auto classify applies suggested category expense and tags`() = runTest {
        coEvery {
            transactionRepository.suggestClassifications(any(), any(), any(), any())
        } returns Resource.Success(
            TransactionClassificationSuggestion(
                budget = "Groceries",
                category = "Food",
                tags = listOf("weekly", "essentials"),
            ),
        )
        coEvery { categoryRepository.searchCategories("Food") } returns Resource.Success(
            listOf(Category(id = 10, name = "Food")),
        )
        coEvery { budgetRepository.searchExpenses("Groceries") } returns Resource.Success(
            listOf(BudgetExpense(id = 11, name = "Groceries", amount = 0.0, expected = 300.0)),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDescriptionChanged("Weekly groceries")
        viewModel.onAmountChanged("42.10")
        viewModel.onSourceDropdownSelected(checking.id)
        viewModel.onTargetQueryChanged("Supermarket")

        viewModel.autoClassify()
        advanceUntilIdle()

        assertEquals(10L, viewModel.uiState.value.categorySelected?.id)
        assertEquals(11L, viewModel.uiState.value.expenseSelected?.id)
        assertTrue(viewModel.uiState.value.tags.contains("weekly"))
        assertTrue(viewModel.uiState.value.tags.contains("essentials"))
        assertTrue(viewModel.uiState.value.moreOptionsExpanded)
        assertTrue(viewModel.uiState.value.autoClassifyStatus is AutoClassifyStatus.Applied)
    }

    @Test
    fun `edit load resolves category id from metadata name`() = runTest {
        val transactionId = 99L
        coEvery { transactionRepository.getTransaction(transactionId) } returns Resource.Success(
            Transaction(
                id = transactionId,
                description = "Lunch",
                amount = 12.5,
                type = TransactionType.CREDIT,
                date = LocalDate.now(),
                sourceAccountId = checking.id,
                sourceAccountName = checking.name,
                destinationAccountId = 5L,
                destinationAccountName = "Cafe",
                categoryName = "Food",
            ),
        )
        coEvery { categoryRepository.searchCategories("Food") } returns Resource.Success(
            listOf(Category(id = 10, name = "Food")),
        )
        setupRepository()
        val viewModel = TransactionFormViewModel(
            transactionRepository,
            accountRepository,
            currencyRepository,
            categoryRepository,
            budgetRepository,
            contractRepository,
            tagRepository,
            userPreferences,
            transactionTemplateStore,
            SavedStateHandle(mapOf("transactionId" to transactionId)),
        )
        advanceUntilIdle()

        assertEquals(10L, viewModel.uiState.value.categorySelected?.id)
        assertEquals("Food", viewModel.uiState.value.categoryQuery)
    }

    @Test
    fun `edit submit keeps category when adding budget expense`() = runTest {
        val transactionId = 99L
        val existing = Transaction(
            id = transactionId,
            description = "Lunch",
            amount = 12.5,
            type = TransactionType.CREDIT,
            date = LocalDate.now(),
            sourceAccountId = checking.id,
            sourceAccountName = checking.name,
            destinationAccountId = 5L,
            destinationAccountName = "Cafe",
            categoryName = "Food",
        )
        coEvery { transactionRepository.getTransaction(transactionId) } returns Resource.Success(existing)
        coEvery { categoryRepository.searchCategories("Food") } returns Resource.Success(
            listOf(Category(id = 10, name = "Food")),
        )
        coEvery { budgetRepository.searchExpenses("Transport") } returns Resource.Success(
            listOf(BudgetExpense(id = 20, name = "Transport", amount = 0.0, expected = 100.0)),
        )
        coEvery { transactionRepository.updateTransaction(any()) } returns Resource.Success(existing)
        setupRepository()
        val viewModel = TransactionFormViewModel(
            transactionRepository,
            accountRepository,
            currencyRepository,
            categoryRepository,
            budgetRepository,
            contractRepository,
            tagRepository,
            userPreferences,
            transactionTemplateStore,
            SavedStateHandle(mapOf("transactionId" to transactionId)),
        )
        advanceUntilIdle()

        viewModel.selectExpense(FilterOption(20, "Transport"))
        viewModel.submit()
        advanceUntilIdle()

        coVerify {
            transactionRepository.updateTransaction(
                match { it.categoryId == 10L && it.expenseId == 20L },
            )
        }
    }

    @Test
    fun `create submit Queued sets saveSuccess and savedOffline`() = runTest {
        setupRepository()
        coEvery {
            transactionRepository.createTransactionOrEnqueue(any())
        } returns Resource.Success(
            CreateOutcome.Queued(
                PendingTransactionCreate(
                    localId = "local-1",
                    createdAtMillis = 1L,
                    status = OutboxStatus.PENDING,
                    lastError = null,
                    attemptCount = 0,
                    date = LocalDate.now(),
                    currency = "EUR",
                    description = "Coffee",
                    amount = 4.5,
                    sourceAccountId = checking.id,
                    destinationAccountId = 9L,
                ),
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDescriptionChanged("Coffee")
        viewModel.onAmountChanged("4.5")
        viewModel.onSourceDropdownSelected(checking.id)
        viewModel.selectTargetAutocomplete(FilterOption(9, "Cafe"))
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        assertTrue(viewModel.uiState.value.savedOffline)
        coVerify { transactionRepository.createTransactionOrEnqueue(any()) }
    }
}
