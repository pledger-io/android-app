package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.BalanceDatedDto
import com.pledgerio.app.data.remote.dto.BalancePartitionedDto
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.YearMonth

class ReportRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val accountRepository = mockk<AccountRepository>()
    private val budgetRepository = mockk<BudgetRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private lateinit var repository: ReportRepositoryImpl

    @Before
    fun setUp() {
        repository = ReportRepositoryImpl(
            apiService,
            accountRepository,
            budgetRepository,
            categoryRepository,
        )
    }

    @Test
    fun `getNetWorthTrend uses daily balance grouping for selected month`() = runTest {
        val month = YearMonth.of(2026, 5)
        val typeSlot = slot<String>()
        coEvery {
            apiService.getDatedBalance(capture(typeSlot), any())
        } returns Response.success(
            listOf(
                BalanceDatedDto(date = "2026-05-01", balance = 1000.0),
                BalanceDatedDto(date = "2026-05-02", balance = 1100.0),
            ),
        )

        val result = repository.getNetWorthTrend(month)

        assertEquals("daily", typeSlot.captured)
        coVerify { apiService.getDatedBalance("daily", any()) }
        assertTrue(result is Resource.Success)
        val points = (result as Resource.Success).data
        assertEquals(2, points.size)
        assertEquals("2026-05-01", points[0].date)
        assertEquals(1000.0, points[0].amount, 0.01)
    }

    @Test
    fun `getAccountBalances lists every owned account including zero balances and ids`() = runTest {
        val month = YearMonth.of(2026, 5)
        val owned = listOf(
            Account(id = 1, name = "Checking", balance = 100.0),
            Account(id = 2, name = "Savings", balance = 0.0),
        )
        coEvery { accountRepository.refreshOwnedAccounts() } returns Resource.Success(owned)
        coEvery {
            apiService.getPartitionedBalance("account", any())
        } returns Response.success(
            listOf(BalancePartitionedDto(balance = 100.0, partition = "Checking")),
        )

        val result = repository.getAccountBalances(month)

        assertTrue(result is Resource.Success)
        val partitions = (result as Resource.Success).data
        assertEquals(2, partitions.size)
        assertEquals("Checking", partitions[0].label)
        assertEquals(1L, partitions[0].id)
        assertEquals("Savings", partitions[1].label)
        assertEquals(2L, partitions[1].id)
        assertEquals(0.0, partitions[1].amount, 0.01)
    }

    @Test
    fun `getBudgetPerformance includes expense ids`() = runTest {
        val month = YearMonth.of(2026, 5)
        every { budgetRepository.getBudgets(2026, 5) } returns flowOf(
            Resource.Success(
                BudgetListState(
                    budgets = listOf(
                        Budget(id = 11L, name = "Groceries", amount = 400.0, spent = 250.0),
                    ),
                ),
            ),
        )

        val result = repository.getBudgetPerformance(month)

        assertTrue(result is Resource.Success)
        val items = (result as Resource.Success).data
        assertEquals(1, items.size)
        assertEquals(11L, items[0].expenseId)
        assertEquals("Groceries", items[0].name)
    }

    @Test
    fun `getCategoryBreakdown resolves category ids from catalog`() = runTest {
        val month = YearMonth.of(2026, 5)
        coEvery {
            apiService.getPartitionedBalance("category", any())
        } returns Response.success(
            listOf(
                BalancePartitionedDto(balance = -80.0, partition = "Food"),
                BalancePartitionedDto(balance = -20.0, partition = "Unknown"),
            ),
        )
        every { categoryRepository.observeCategories() } returns flowOf(
            listOf(Category(id = 7L, name = "Food", description = "")),
        )

        val result = repository.getCategoryBreakdown(month)

        assertTrue(result is Resource.Success)
        val partitions = (result as Resource.Success).data
        assertEquals(7L, partitions.find { it.label == "Food" }?.id)
        assertNull(partitions.find { it.label == "Unknown" }?.id)
    }
}
