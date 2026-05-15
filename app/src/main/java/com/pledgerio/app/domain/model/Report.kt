package com.pledgerio.app.domain.model

data class IncomeExpenseReport(
    val months: List<MonthlyBalance>,
)

data class MonthlyBalance(
    val year: Int,
    val month: Int,
    val income: Double,
    val expense: Double,
) {
    val net: Double get() = income - expense
}

data class CategoryReport(
    val categories: List<CategorySpending>,
)

data class CategorySpending(
    val categoryId: Long,
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
)
