package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.BudgetDto
import com.pledgerio.app.data.remote.dto.CreateBudgetRequest
import com.pledgerio.app.data.remote.dto.DateRangeDto
import com.pledgerio.app.data.remote.dto.ExpenseDto
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class BudgetRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val budgetDao = mockk<BudgetDao>(relaxed = true)
    private lateinit var repository: BudgetRepositoryImpl

    @Before
    fun setUp() {
        repository = BudgetRepositoryImpl(apiService, budgetDao)
    }

    @Test
    fun `getBudgets emits needsInitialSetup on 404`() = runTest {
        coEvery { apiService.getBudgets(any(), any(), any()) } returns Response.error(
            404,
            "".toResponseBody(),
        )

        val results = repository.getBudgets(2026, 5).toList()
        val success = results.filterIsInstance<Resource.Success<BudgetListState>>().last()

        assertTrue(success.data.needsInitialSetup)
        assertTrue(success.data.budgets.isEmpty())
    }

    @Test
    fun `createInitialBudget succeeds on 201`() = runTest {
        coEvery { apiService.createInitialBudget(any()) } returns Response.success(null)

        val result = repository.createInitialBudget(2026, 5, 3500.0)

        assertTrue(result is Resource.Success)
    }

    @Test
    fun `createInitialBudget sends year month and income`() = runTest {
        coEvery { apiService.createInitialBudget(CreateBudgetRequest(2026, 3, 4200.0)) } returns
            Response.success(null)

        val result = repository.createInitialBudget(2026, 3, 4200.0)

        assertTrue(result is Resource.Success)
    }

    @Test
    fun `getBudgets succeeds when period endDate is null`() = runTest {
        val budgetDto = BudgetDto(
            income = 3500.0,
            period = DateRangeDto(startDate = "2026-05-01", endDate = null),
            expenses = listOf(ExpenseDto(id = 1, name = "Groceries", expected = 400.0)),
        )
        coEvery { apiService.getBudgets(any(), any(), any()) } returns Response.success(budgetDto)
        coEvery { apiService.getExpenseBalance(any(), any(), any()) } returns Response.success(emptyList())

        val results = repository.getBudgets(2026, 5).toList()
        val success = results.filterIsInstance<Resource.Success<BudgetListState>>().last()

        assertFalse(success.data.needsInitialSetup)
        assertEquals(1, success.data.budgets.size)
    }

    @Test
    fun `createInitialBudget fails on 400`() = runTest {
        coEvery { apiService.createInitialBudget(any()) } returns Response.error(
            400,
            "".toResponseBody(),
        )

        val result = repository.createInitialBudget(2026, 5, 3500.0)

        assertTrue(result is Resource.Error)
        assertFalse((result as Resource.Error).message.isNullOrBlank())
    }
}
