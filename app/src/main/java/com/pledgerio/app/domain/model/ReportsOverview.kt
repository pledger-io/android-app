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
    /** Prior-month income/expense when available (soft-fail; MoM hidden if null). */
    val priorIncomeExpense: IncomeExpenseSummary? = null,
    /** Prior-month category breakdown for optional MoM on top categories (by label). */
    val priorCategories: List<PartitionAmount> = emptyList(),
) {
    val netCashFlowDelta: MonthDelta?
        get() {
            val current = incomeExpense ?: return null
            val prior = priorIncomeExpense ?: return null
            return monthDelta(current.net, prior.net)
        }

    fun categoryDelta(label: String): MonthDelta? {
        val current = topCategories.find { it.label == label } ?: return null
        val prior = priorCategories.find { it.label == label } ?: return null
        return monthDelta(current.amount, prior.amount)
    }
}
