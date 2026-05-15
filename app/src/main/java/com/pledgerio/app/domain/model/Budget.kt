package com.pledgerio.app.domain.model

data class Budget(
    val id: Long,
    val name: String,
    val amount: Double,
    val spent: Double = 0.0,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val expenses: List<BudgetExpense> = emptyList(),
) {
    val remaining: Double get() = amount - spent
    val percentUsed: Float get() = if (amount > 0) (spent / amount).toFloat().coerceIn(0f, 1.5f) else 0f
}

data class BudgetExpense(
    val id: Long,
    val name: String,
    val amount: Double,
    val expected: Double,
)

enum class BudgetPeriod {
    MONTHLY,
    YEARLY;

    companion object {
        fun fromString(value: String): BudgetPeriod = when (value.lowercase()) {
            "monthly" -> MONTHLY
            "yearly" -> YEARLY
            else -> MONTHLY
        }
    }
}
