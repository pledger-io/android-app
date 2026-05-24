package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.PagedResult
import com.pledgerio.app.domain.repository.TransactionRepository
import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.usecase.GetTransactionsUseCase
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transactionRepository = mockk<TransactionRepository>()
    private val getTransactionsUseCase = mockk<GetTransactionsUseCase>()
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val budgetRepository = mockk<BudgetRepository>(relaxed = true)
    private val contractRepository = mockk<ContractRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
    private val savedStateHandle = SavedStateHandle(
        mapOf("expenseId" to -1L, "expenseName" to ""),
    )

    init {
        every { userPreferences.financeExperienceMode } returns
            MutableStateFlow(FinanceExperienceMode.GUIDED)
    }

    private fun createViewModel() = TransactionsViewModel(
        savedStateHandle = savedStateHandle,
        getTransactionsUseCase = getTransactionsUseCase,
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        budgetRepository = budgetRepository,
        contractRepository = contractRepository,
        userPreferences = userPreferences,
    )

    @Test
    fun `navigateToMonth keeps selected month when API returns no transactions`() = runTest {
        val target = YearMonth.of(2024, 3)
        coEvery {
            getTransactionsUseCase(
                startDate = any(),
                endDate = any(),
                accountId = any(),
                type = any(),
                filters = any(),
                page = any(),
                pageSize = any(),
            )
        } returns Resource.Success(
            PagedResult(emptyList(), totalRecords = 0, totalPages = 0, pageSize = 25),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.navigateToMonth(target)
        advanceUntilIdle()

        assertEquals(target, viewModel.uiState.value.currentMonth)
        assertEquals(
            target.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            viewModel.uiState.value.monthLabel,
        )
        assertFalse(viewModel.uiState.value.hasMoreInMonth)
    }

    @Test
    fun `nextMonth from previous month keeps current month when empty`() = runTest {
        coEvery {
            getTransactionsUseCase(
                startDate = any(),
                endDate = any(),
                accountId = any(),
                type = any(),
                filters = any(),
                page = any(),
                pageSize = any(),
            )
        } returns Resource.Success(
            PagedResult(emptyList(), totalRecords = 0, totalPages = 0, pageSize = 25),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.previousMonth()
        advanceUntilIdle()
        viewModel.nextMonth()
        advanceUntilIdle()

        assertEquals(YearMonth.now(), viewModel.uiState.value.currentMonth)
    }
}
