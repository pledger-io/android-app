package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pledgerio.app.domain.model.Budget
import kotlin.math.abs

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val amount: Double = 0.0,
    val spent: Double = 0.0,
    val period: String = "monthly",
    val lastSynced: Long = System.currentTimeMillis(),
) {
    fun toDomain(): Budget = Budget(
        id = id,
        name = name,
        amount = amount,
        spent = abs(spent),
    )

    companion object {
        fun fromDomain(budget: Budget): BudgetEntity = BudgetEntity(
            id = budget.id,
            name = budget.name,
            amount = budget.amount,
            spent = budget.spent,
        )
    }
}
