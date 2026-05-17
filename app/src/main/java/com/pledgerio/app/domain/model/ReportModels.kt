package com.pledgerio.app.domain.model

data class IncomeExpenseSummary(
    val income: Double,
    val expense: Double,
)

data class PartitionAmount(
    val label: String,
    val amount: Double,
)

data class BudgetPerformanceItem(
    val name: String,
    val spent: Double,
    val budgeted: Double,
)

data class DatedAmount(
    val date: String,
    val amount: Double,
)
