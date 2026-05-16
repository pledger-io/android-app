package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetExpense
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getBudgets(year: Int, month: Int): Flow<Resource<BudgetListState>>
    suspend fun createInitialBudget(year: Int, month: Int, income: Double): Resource<Unit>
    suspend fun saveExpenseGroup(
        id: Long?,
        name: String,
        budgetAmount: Double,
    ): Resource<BudgetListState>
    suspend fun searchExpenses(name: String): Resource<List<BudgetExpense>>
    suspend fun getBudget(id: Long): Resource<Budget>
    suspend fun createBudget(budget: Budget): Resource<Budget>
    suspend fun updateBudget(budget: Budget): Resource<Budget>
    suspend fun deleteBudget(id: Long): Resource<Unit>
}
