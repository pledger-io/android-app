package com.pledgerio.app.ui.reports

import com.pledgerio.app.domain.model.IncomeExpenseSummary
import com.pledgerio.app.domain.model.PartitionAmount
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val prior = month.minusMonths(1)
        coEvery { overviewStore.get(any()) } returns null
        coEvery { overviewStore.invalidate(month) } returns Unit
        coEvery { overviewStore.put(any(), any(), any()) } returns Unit
        stubOverviewNetwork(month, prior)

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

    @Test
    fun `overview populates MoM when prior month succeeds`() = runTest {
        val month = YearMonth.now()
        val prior = month.minusMonths(1)
        coEvery { overviewStore.get(any()) } returns null
        coEvery { overviewStore.put(any(), any(), any()) } returns Unit
        stubOverviewNetwork(
            month = month,
            prior = prior,
            currentIncome = IncomeExpenseSummary(income = 200.0, expense = 50.0),
            priorIncome = IncomeExpenseSummary(income = 100.0, expense = 50.0),
        )

        val viewModel = ReportsViewModel(reportRepository, overviewStore)
        advanceUntilIdle()

        val overview = viewModel.uiState.value.overview
        assertNotNull(overview)
        assertEquals(100.0, overview!!.priorIncomeExpense?.income ?: 0.0, 0.0)
        val delta = overview.netCashFlowDelta
        assertNotNull(delta)
        assertEquals(100.0, delta!!.absolute, 0.0)
        assertEquals(2.0, delta.percent!!, 0.0001)
    }

    @Test
    fun `prior month soft failure leaves overview without MoM`() = runTest {
        val month = YearMonth.now()
        val prior = month.minusMonths(1)
        coEvery { overviewStore.get(any()) } returns null
        coEvery { overviewStore.put(any(), any(), any()) } returns Unit
        coEvery { reportRepository.getIncomeExpenseSummary(month) } returns
            Resource.Success(IncomeExpenseSummary(income = 50.0, expense = 20.0))
        coEvery { reportRepository.getIncomeExpenseSummary(prior) } returns
            Resource.Error("prior failed")
        coEvery { reportRepository.getCategoryBreakdown(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getCategoryBreakdown(prior) } returns Resource.Error("prior cats")
        coEvery { reportRepository.getAccountBalances(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getBudgetPerformance(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getNetWorthTrend(month) } returns Resource.Success(emptyList())

        val viewModel = ReportsViewModel(reportRepository, overviewStore)
        advanceUntilIdle()

        val overview = viewModel.uiState.value.overview
        assertNotNull(overview)
        assertEquals(50.0, overview!!.incomeExpense?.income ?: 0.0, 0.0)
        assertNull(overview.priorIncomeExpense)
        assertNull(overview.netCashFlowDelta)
        assertNull(viewModel.uiState.value.error)
    }

    private fun stubOverviewNetwork(
        month: YearMonth,
        prior: YearMonth,
        currentIncome: IncomeExpenseSummary = IncomeExpenseSummary(income = 50.0, expense = 20.0),
        priorIncome: IncomeExpenseSummary = IncomeExpenseSummary(income = 40.0, expense = 10.0),
        priorCategories: List<PartitionAmount> = emptyList(),
    ) {
        coEvery { reportRepository.getIncomeExpenseSummary(month) } returns Resource.Success(currentIncome)
        coEvery { reportRepository.getIncomeExpenseSummary(prior) } returns Resource.Success(priorIncome)
        coEvery { reportRepository.getCategoryBreakdown(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getCategoryBreakdown(prior) } returns Resource.Success(priorCategories)
        coEvery { reportRepository.getAccountBalances(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getBudgetPerformance(month) } returns Resource.Success(emptyList())
        coEvery { reportRepository.getNetWorthTrend(month) } returns Resource.Success(emptyList())
    }
}
