package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pledgerio.app.domain.model.BudgetExpense

/**
 * Catalog of expense groups returned by `GET /v2/api/budgets/expenses`. Distinct from
 * `BudgetEntity`, which stores the monthly snapshot with spent amounts.
 */
@Entity(
    tableName = "expense_groups",
    indices = [Index("name")],
)
data class ExpenseGroupEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val expected: Double,
) {
    fun toDomain(): BudgetExpense = BudgetExpense(
        id = id,
        name = name,
        amount = 0.0,
        expected = expected,
    )

    companion object {
        fun fromExpense(expense: BudgetExpense): ExpenseGroupEntity = ExpenseGroupEntity(
            id = expense.id,
            name = expense.name,
            expected = expense.expected,
        )
    }
}
