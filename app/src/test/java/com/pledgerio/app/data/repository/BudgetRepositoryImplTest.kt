package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.local.dao.ExpenseGroupDao
import com.pledgerio.app.data.local.entity.BudgetEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.BudgetDto
import com.pledgerio.app.data.remote.dto.CreateBudgetRequest
import com.pledgerio.app.data.remote.dto.DateRangeDto
import com.pledgerio.app.data.remote.dto.ExpenseComputedDto
import com.pledgerio.app.data.remote.dto.ExpenseDto
import com.pledgerio.app.data.remote.dto.ExpenseRequest
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.util.FakeSyncMetadataDao
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.YearMonth

class BudgetRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val budgetDao = mockk<BudgetDao>(relaxed = true)
    private val expenseGroupDao = mockk<ExpenseGroupDao>(relaxed = true)
    private val syncMetadataDao = FakeSyncMetadataDao()
    private lateinit var cacheRefresher: CacheRefresher
    private lateinit var repository: BudgetRepositoryImpl

    @Before
    fun setUp() {
        coEvery { budgetDao.getAll() } returns flowOf(emptyList())
        cacheRefresher = CacheRefresher(syncMetadataDao, TestScope())
        repository = BudgetRepositoryImpl(apiService, budgetDao, expenseGroupDao, cacheRefresher)
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
        assertEquals(3500.0, success.data.income!!, 0.001)
    }

    @Test
    fun `getBudgets does not emit wrong-month Room cache`() = runTest {
        val mayEntity = BudgetEntity(id = 1, name = "May Group", amount = 100.0, spent = 10.0)
        coEvery { budgetDao.getAll() } returns flowOf(listOf(mayEntity))
        syncMetadataDao.seed(SyncKeys.BUDGET_ROOM_MONTH, 2026 * 100L + 5)
        syncMetadataDao.seed(SyncKeys.budgetMonth(YearMonth.of(2026, 5)), System.currentTimeMillis())

        val juneDto = BudgetDto(
            income = 3600.0,
            expenses = listOf(ExpenseDto(id = 2, name = "June Group", expected = 200.0)),
        )
        coEvery { apiService.getBudgets(2026, 6, any()) } returns Response.success(juneDto)
        coEvery { apiService.getExpenseBalance(2026, 6, any()) } returns Response.success(emptyList())

        val results = repository.getBudgets(2026, 6).toList()
        val successes = results.filterIsInstance<Resource.Success<BudgetListState>>()

        assertEquals(1, successes.size)
        assertEquals("June Group", successes.single().data.budgets.single().name)
        assertEquals(3600.0, successes.single().data.income!!, 0.001)
    }

    @Test
    fun `getBudgets emits Room cache only for matching fresh month`() = runTest {
        val mayEntity = BudgetEntity(id = 1, name = "May Group", amount = 100.0, spent = 10.0)
        coEvery { budgetDao.getAll() } returns flowOf(listOf(mayEntity))
        syncMetadataDao.seed(SyncKeys.BUDGET_ROOM_MONTH, 2026 * 100L + 5)
        syncMetadataDao.seed(SyncKeys.budgetMonth(YearMonth.of(2026, 5)), System.currentTimeMillis())

        val mayDto = BudgetDto(
            income = 3500.0,
            expenses = listOf(ExpenseDto(id = 1, name = "May Group", expected = 100.0)),
        )
        coEvery { apiService.getBudgets(2026, 5, any()) } returns Response.success(mayDto)
        coEvery { apiService.getExpenseBalance(2026, 5, any()) } returns Response.success(emptyList())

        val results = repository.getBudgets(2026, 5).toList()
        val successes = results.filterIsInstance<Resource.Success<BudgetListState>>()

        assertEquals(2, successes.size)
        assertNull(successes.first().data.income)
        assertEquals("May Group", successes.first().data.budgets.single().name)
        assertEquals(3500.0, successes.last().data.income!!, 0.001)
    }

    @Test
    fun `updateBudgetIncome patches and refreshes month`() = runTest {
        val updated = BudgetDto(
            income = 4000.0,
            expenses = listOf(ExpenseDto(id = 1, name = "Groceries", expected = 400.0)),
        )
        coEvery {
            apiService.updateBudgetIncome(CreateBudgetRequest(2026, 5, 4000.0))
        } returns Response.success(updated)
        coEvery { apiService.getBudgets(2026, 5, any()) } returns Response.success(updated)
        coEvery { apiService.getExpenseBalance(2026, 5, any()) } returns Response.success(emptyList())

        val result = repository.updateBudgetIncome(2026, 5, 4000.0)

        assertTrue(result is Resource.Success)
        assertEquals(4000.0, (result as Resource.Success).data.income!!, 0.001)
        coVerify { apiService.updateBudgetIncome(CreateBudgetRequest(2026, 5, 4000.0)) }
    }

    @Test
    fun `saveExpenseGroup creates expense and refreshes viewed month`() = runTest {
        val budgetDto = BudgetDto(
            income = 3500.0,
            expenses = listOf(ExpenseDto(id = 1, name = "Groceries", expected = 400.0)),
        )
        coEvery {
            apiService.saveExpense(ExpenseRequest(name = "Groceries", amount = 400.0))
        } returns Response.success(budgetDto)
        coEvery { apiService.getBudgets(2026, 3, any()) } returns Response.success(budgetDto)
        coEvery { apiService.getExpenseBalance(2026, 3, any()) } returns Response.success(emptyList())

        val result = repository.saveExpenseGroup(
            id = null,
            name = "Groceries",
            budgetAmount = 400.0,
            year = 2026,
            month = 3,
        )

        assertTrue(result is Resource.Success)
        assertEquals(1, (result as Resource.Success).data.budgets.size)
        assertEquals(3500.0, result.data.income!!, 0.001)
    }

    @Test
    fun `saveExpenseGroup updates expense when id provided`() = runTest {
        val budgetDto = BudgetDto(
            expenses = listOf(ExpenseDto(id = 5, name = "Groceries", expected = 500.0)),
        )
        coEvery {
            apiService.saveExpense(ExpenseRequest(id = 5, name = "Groceries", amount = 500.0))
        } returns Response.success(budgetDto)
        coEvery { apiService.getBudgets(2026, 5, any()) } returns Response.success(budgetDto)
        coEvery { apiService.getExpenseBalance(2026, 5, any()) } returns Response.success(emptyList())

        val result = repository.saveExpenseGroup(
            id = 5,
            name = "Groceries",
            budgetAmount = 500.0,
            year = 2026,
            month = 5,
        )

        assertTrue(result is Resource.Success)
        assertEquals(500.0, (result as Resource.Success).data.budgets.first().amount, 0.001)
    }

    @Test
    fun `getBudgets normalizes negative spent from balance API`() = runTest {
        val budgetDto = BudgetDto(
            expenses = listOf(ExpenseDto(id = 1, name = "Groceries", expected = 400.0)),
        )
        coEvery { apiService.getBudgets(any(), any(), any()) } returns Response.success(budgetDto)
        coEvery { apiService.getExpenseBalance(any(), any(), any()) } returns Response.success(
            listOf(ExpenseComputedDto(id = 1, spent = -150.0, left = 250.0)),
        )

        val results = repository.getBudgets(2026, 5).toList()
        val success = results.filterIsInstance<Resource.Success<BudgetListState>>().last()
        val budget = success.data.budgets.first()

        assertEquals(150.0, budget.spent, 0.001)
        assertEquals(250.0, budget.remaining, 0.001)
        assertEquals(0.375f, budget.percentUsed, 0.001f)
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
