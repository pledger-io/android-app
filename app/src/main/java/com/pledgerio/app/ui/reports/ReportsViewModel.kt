package com.pledgerio.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.BudgetPerformanceItem
import com.pledgerio.app.domain.model.DatedAmount
import com.pledgerio.app.domain.model.IncomeExpenseSummary
import com.pledgerio.app.domain.model.PartitionAmount
import com.pledgerio.app.domain.model.ReportsOverview
import com.pledgerio.app.domain.repository.ReportRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

data class ReportsUiState(
    val selectedType: ReportType = ReportType.OVERVIEW,
    val currentMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val overview: ReportsOverview? = null,
    val incomeExpense: IncomeExpenseSummary? = null,
    val partitions: List<PartitionAmount> = emptyList(),
    val budgetItems: List<BudgetPerformanceItem> = emptyList(),
    val netWorthTrend: List<DatedAmount> = emptyList(),
    val lastUpdatedAtMillis: Long? = null,
) {
    val monthLabel: String get() = currentMonth.format(MONTH_FORMATTER)
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadReport()
    }

    fun selectReportType(type: ReportType) {
        _uiState.update { it.copy(selectedType = type) }
        loadReport()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadReport()
    }

    fun previousMonth() {
        navigateToMonth(_uiState.value.currentMonth.minusMonths(1))
    }

    fun nextMonth() {
        val next = _uiState.value.currentMonth.plusMonths(1)
        if (next <= YearMonth.now()) {
            navigateToMonth(next)
        }
    }

    private fun navigateToMonth(month: YearMonth) {
        _uiState.update { it.copy(currentMonth = month) }
        loadReport()
    }

    private fun loadReport() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            if (!state.isRefreshing) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            when (state.selectedType) {
                ReportType.OVERVIEW -> loadOverview(state.currentMonth, state.isRefreshing)
                else -> loadSingleReport(state.selectedType, state.currentMonth, state.isRefreshing)
            }
        }
    }

    private suspend fun loadOverview(month: YearMonth, isRefreshing: Boolean) {
        coroutineScope {
            val incomeDeferred = async { reportRepository.getIncomeExpenseSummary(month) }
            val categoryDeferred = async { reportRepository.getCategoryBreakdown(month) }
            val balanceDeferred = async { reportRepository.getAccountBalances(month) }
            val budgetDeferred = async { reportRepository.getBudgetPerformance(month) }
            val netWorthDeferred = async { reportRepository.getNetWorthTrend(month) }

            val income = incomeDeferred.await()
            val categories = categoryDeferred.await()
            val balances = balanceDeferred.await()
            val budgets = budgetDeferred.await()
            val netWorth = netWorthDeferred.await()

            val errors = listOf(income, categories, balances, budgets, netWorth)
                .filterIsInstance<Resource.Error>()
                .map { it.message }

            val overview = ReportsOverview(
                incomeExpense = (income as? Resource.Success)?.data,
                topCategories = (categories as? Resource.Success)?.data.orEmpty().take(5),
                accountBalances = (balances as? Resource.Success)?.data.orEmpty(),
                budgetItems = (budgets as? Resource.Success)?.data.orEmpty(),
                netWorthInMonth = (netWorth as? Resource.Success)?.data.orEmpty().inMonth(month),
            )

            val hasAnyData = overview.incomeExpense != null ||
                overview.topCategories.isNotEmpty() ||
                overview.accountBalances.isNotEmpty() ||
                overview.budgetItems.isNotEmpty() ||
                overview.netWorthInMonth.isNotEmpty()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    overview = overview,
                    incomeExpense = null,
                    partitions = emptyList(),
                    budgetItems = emptyList(),
                    netWorthTrend = emptyList(),
                    error = if (!hasAnyData && errors.isNotEmpty()) errors.firstOrNull() else null,
                    lastUpdatedAtMillis = if (hasAnyData) System.currentTimeMillis() else it.lastUpdatedAtMillis,
                )
            }
        }
    }

    private suspend fun loadSingleReport(
        type: ReportType,
        month: YearMonth,
        isRefreshing: Boolean,
    ) {
        val result = when (type) {
            ReportType.INCOME_EXPENSE -> reportRepository.getIncomeExpenseSummary(month)
            ReportType.CATEGORY -> reportRepository.getCategoryBreakdown(month)
            ReportType.BUDGET -> reportRepository.getBudgetPerformance(month)
            ReportType.NET_WORTH -> reportRepository.getNetWorthTrend(month)
            ReportType.BALANCE -> reportRepository.getAccountBalances(month)
            ReportType.OVERVIEW -> return
        }
        when (result) {
            is Resource.Success -> {
                val cleared = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                    overview = null,
                    lastUpdatedAtMillis = System.currentTimeMillis(),
                    incomeExpense = null,
                    partitions = emptyList(),
                    budgetItems = emptyList(),
                    netWorthTrend = emptyList(),
                )
                _uiState.value = when (type) {
                    ReportType.INCOME_EXPENSE -> cleared.copy(
                        incomeExpense = result.data as IncomeExpenseSummary,
                    )
                    ReportType.CATEGORY, ReportType.BALANCE -> cleared.copy(
                        partitions = result.data as List<PartitionAmount>,
                    )
                    ReportType.BUDGET -> cleared.copy(
                        budgetItems = result.data as List<BudgetPerformanceItem>,
                    )
                    ReportType.NET_WORTH -> cleared.copy(
                        netWorthTrend = (result.data as List<DatedAmount>).inMonth(month),
                    )
                    ReportType.OVERVIEW -> cleared
                }
            }
            is Resource.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = result.message,
                    )
                }
            }
            is Resource.Loading -> Unit
        }
    }
}
