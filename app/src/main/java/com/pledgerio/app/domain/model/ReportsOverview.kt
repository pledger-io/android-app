package com.pledgerio.app.domain.model

/**
 * Aggregated snapshot for the Reports "Overview" tab for a single month.
 */
data class ReportsOverview(
    val incomeExpense: IncomeExpenseSummary?,
    val topCategories: List<PartitionAmount> = emptyList(),
    val accountBalances: List<PartitionAmount> = emptyList(),
    val budgetItems: List<BudgetPerformanceItem> = emptyList(),
    val netWorthInMonth: List<DatedAmount> = emptyList(),
)
