package com.pledgerio.app.ui.budgets

import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.usecase.CreateInitialBudgetUseCase
import com.pledgerio.app.domain.usecase.SaveBudgetExpenseUseCase
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BudgetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val budgetRepository = mockk<BudgetRepository>()
    private val createInitialBudgetUseCase = mockk<CreateInitialBudgetUseCase>()
    private val saveBudgetExpenseUseCase = mockk<SaveBudgetExpenseUseCase>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load sets needsInitialSetup when repository returns 404 state`() = runTest {
        every { budgetRepository.getBudgets(any(), any()) } returns flowOf(
            Resource.Loading,
            Resource.Success(BudgetListState(needsInitialSetup = true)),
        )

        val viewModel = BudgetsViewModel(
            budgetRepository,
            createInitialBudgetUseCase,
            saveBudgetExpenseUseCase,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsInitialSetup)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `createInitialBudget reloads after success`() = runTest {
        var loadCount = 0
        every { budgetRepository.getBudgets(any(), any()) } answers {
            loadCount++
            if (loadCount == 1) {
                flowOf(
                    Resource.Loading,
                    Resource.Success(BudgetListState(needsInitialSetup = true)),
                )
            } else {
                flowOf(
                    Resource.Loading,
                    Resource.Success(BudgetListState(budgets = emptyList())),
                )
            }
        }
        coEvery { createInitialBudgetUseCase(any(), any(), any()) } returns Resource.Success(Unit)

        val viewModel = BudgetsViewModel(
            budgetRepository,
            createInitialBudgetUseCase,
            saveBudgetExpenseUseCase,
        )
        advanceUntilIdle()

        viewModel.onSetupIncomeChange("3000")
        viewModel.createInitialBudget()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.needsInitialSetup)
        assertFalse(viewModel.uiState.value.isCreatingInitial)
    }
}
