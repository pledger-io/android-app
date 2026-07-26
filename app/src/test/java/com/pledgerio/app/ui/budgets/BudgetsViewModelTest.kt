package com.pledgerio.app.ui.budgets

import androidx.lifecycle.SavedStateHandle
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.usecase.CreateInitialBudgetUseCase
import com.pledgerio.app.domain.usecase.GetBudgetsUseCase
import com.pledgerio.app.domain.usecase.SaveBudgetExpenseUseCase
import com.pledgerio.app.domain.usecase.UpdateBudgetIncomeUseCase
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getBudgetsUseCase = mockk<GetBudgetsUseCase>()
    private val createInitialBudgetUseCase = mockk<CreateInitialBudgetUseCase>()
    private val saveBudgetExpenseUseCase = mockk<SaveBudgetExpenseUseCase>(relaxed = true)
    private val updateBudgetIncomeUseCase = mockk<UpdateBudgetIncomeUseCase>()
    private val savedStateHandle = SavedStateHandle(mapOf("year" to -1, "month" to -1))

    private fun createViewModel() = BudgetsViewModel(
        savedStateHandle = savedStateHandle,
        getBudgetsUseCase = getBudgetsUseCase,
        createInitialBudgetUseCase = createInitialBudgetUseCase,
        saveBudgetExpenseUseCase = saveBudgetExpenseUseCase,
        updateBudgetIncomeUseCase = updateBudgetIncomeUseCase,
    )

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
        every { getBudgetsUseCase(any(), any()) } returns flowOf(
            Resource.Loading,
            Resource.Success(BudgetListState(needsInitialSetup = true)),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsInitialSetup)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `createInitialBudget reloads after success`() = runTest {
        var loadCount = 0
        every { getBudgetsUseCase(any(), any()) } answers {
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

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSetupIncomeChange("3000")
        viewModel.createInitialBudget()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.needsInitialSetup)
        assertFalse(viewModel.uiState.value.isCreatingInitial)
    }

    @Test
    fun `saveIncomeForm updates monthly income`() = runTest {
        every { getBudgetsUseCase(any(), any()) } returns flowOf(
            Resource.Loading,
            Resource.Success(
                BudgetListState(
                    budgets = listOf(Budget(id = 1, name = "Groceries", amount = 400.0, spent = 50.0)),
                    income = 3500.0,
                ),
            ),
        )
        coEvery { updateBudgetIncomeUseCase(any(), any(), 4000.0) } returns Resource.Success(
            BudgetListState(
                budgets = listOf(Budget(id = 1, name = "Groceries", amount = 400.0, spent = 50.0)),
                income = 4000.0,
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openIncomeForm()
        viewModel.onIncomeFormAmountChange("4000")
        viewModel.saveIncomeForm()
        advanceUntilIdle()

        assertEquals(4000.0, viewModel.uiState.value.monthlyIncome!!, 0.001)
        assertFalse(viewModel.uiState.value.incomeFormVisible)
        coVerify { updateBudgetIncomeUseCase(any(), any(), 4000.0) }
    }

    @Test
    fun `load keeps income when budget has no expense groups`() = runTest {
        every { getBudgetsUseCase(any(), any()) } returns flowOf(
            Resource.Loading,
            Resource.Success(BudgetListState(budgets = emptyList(), income = 3500.0)),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(3500.0, viewModel.uiState.value.monthlyIncome!!, 0.001)
        assertTrue(viewModel.uiState.value.budgets.isEmpty())
        assertFalse(viewModel.uiState.value.needsInitialSetup)
    }

    @Test
    fun `previousMonth clears monthly income until reload`() = runTest {
        var loadCount = 0
        every { getBudgetsUseCase(any(), any()) } answers {
            loadCount++
            if (loadCount == 1) {
                flowOf(
                    Resource.Loading,
                    Resource.Success(
                        BudgetListState(
                            budgets = listOf(Budget(id = 1, name = "Groceries", amount = 400.0)),
                            income = 3500.0,
                        ),
                    ),
                )
            } else {
                flowOf(Resource.Loading)
            }
        }

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(3500.0, viewModel.uiState.value.monthlyIncome!!, 0.001)

        viewModel.previousMonth()
        assertNull(viewModel.uiState.value.monthlyIncome)
        assertTrue(viewModel.uiState.value.budgets.isEmpty())
    }
}
