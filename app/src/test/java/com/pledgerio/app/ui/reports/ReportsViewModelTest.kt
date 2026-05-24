package com.pledgerio.app.ui.reports

import com.pledgerio.app.domain.model.IncomeExpenseSummary
import com.pledgerio.app.domain.model.ReportsOverview
import com.pledgerio.app.domain.repository.ReportRepository
import com.pledgerio.app.domain.repository.ReportsOverviewStore
import com.pledgerio.app.util.MainDispatcherRule
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reportRepository = mockk<ReportRepository>()
    private val overviewStore = mockk<ReportsOverviewStore>()

    @Test
    fun `init uses fresh cached overview without network calls`() = runTest {
        val month = YearMonth.now()
        val cachedOverview = ReportsOverview(
            incomeExpense = IncomeExpenseSummary(income = 1200.0, expense = 800.0),
        )
        val entry = mockk<ReportsOverviewStore.Entry>()
        every { entry.overview } returns cachedOverview
        every { entry.fetchedAtMillis } returns 1234L
        every { entry.isFresh(month, any(), any()) } returns true
        coEvery { overviewStore.get(month) } returns entry

        val viewModel = ReportsViewModel(reportRepository, overviewStore)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(cachedOverview, state.overview)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        coVerify(exactly = 0) { reportRepository.getIncomeExpenseSummary(any()) }
    }

    @Test
    fun `refresh invalidates cache and fetches overview from repositories`() = runTest {
        val month = YearMonth.now()
        coEvery { overviewStore.get(any()) } returns null
        coEvery { overviewStore.invalidate(month) } returns Unit
        coEvery { overviewStore.put(any(), any(), any()) } returns Unit
        coEvery { reportRepository.getIncomeExpenseSummary(month) } returns
            Resource.Success(IncomeExpenseSummary(income = 50.0, expense = 20.0))
        coEvery { reportRepository.getCategoryBreakdown(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getAccountBalances(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getBudgetPerformance(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getNetWorthTrend(month) } returns Resource.Success(emptyList())

        val viewModel = ReportsViewModel(reportRepository, overviewStore)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertEquals(50.0, state.overview?.incomeExpense?.income ?: 0.0, 0.0)
        coVerify(atLeast = 1) { overviewStore.invalidate(month) }
        coVerify(atLeast = 1) { overviewStore.put(month, any(), any()) }
    }
}
