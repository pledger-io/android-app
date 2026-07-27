package com.pledgerio.app.domain.model

import kotlin.math.abs

data class IncomeExpenseSummary(
    val income: Double,
    val expense: Double,
) {
    val net: Double get() = income - expense
}

data class PartitionAmount(
    val label: String,
    val amount: Double,
    /** Category id or account id depending on the report; null when unresolved. */
    val id: Long? = null,
)

data class BudgetPerformanceItem(
    val name: String,
    val spent: Double,
    val budgeted: Double,
    /** Expense-group id for drill-down into transactions; null when unavailable. */
    val expenseId: Long? = null,
)

/**
 * Month-over-month change of a scalar value.
 *
 * [percent] is [absolute] / |prior|; null when the prior baseline is zero (undefined %).
 */
data class MonthDelta(
    val absolute: Double,
    val percent: Double?,
)

fun monthDelta(current: Double, prior: Double): MonthDelta {
    val absolute = current - prior
    val percent = if (prior != 0.0) absolute / abs(prior) else null
    return MonthDelta(absolute = absolute, percent = percent)
}

data class DatedAmount(
    val date: String,
    val amount: Double,
)
