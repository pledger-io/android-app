package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetExpense
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getBudgets(year: Int, month: Int): Flow<Resource<List<Budget>>>
    suspend fun searchExpenses(name: String): Resource<List<BudgetExpense>>
    suspend fun getBudget(id: Long): Resource<Budget>
    suspend fun createBudget(budget: Budget): Resource<Budget>
    suspend fun updateBudget(budget: Budget): Resource<Budget>
    suspend fun deleteBudget(id: Long): Resource<Unit>
}
