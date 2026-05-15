package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.local.entity.BudgetEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetExpense
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val budgetDao: BudgetDao,
) : BudgetRepository {

    override fun getBudgets(year: Int, month: Int): Flow<Resource<List<Budget>>> = flow {
        emit(Resource.Loading)

        try {
            val budgetResponse = apiService.getBudgets(year = year, month = month)
            if (budgetResponse.isSuccessful) {
                val dto = budgetResponse.body() ?: run {
                    emit(Resource.Error("Empty budget response"))
                    return@flow
                }

                // Fetch computed expense balances for the period
                val balanceResponse = apiService.getExpenseBalance(year, month)
                val balances = if (balanceResponse.isSuccessful) {
                    balanceResponse.body()?.associateBy { it.id } ?: emptyMap()
                } else emptyMap()

                val budgets = dto.expenses.map { expense ->
                    val computed = balances[expense.id]
                    Budget(
                        id = expense.id,
                        name = expense.name,
                        amount = expense.expected,
                        spent = computed?.spent ?: 0.0,
                        expenses = listOf(
                            BudgetExpense(
                                id = expense.id,
                                name = expense.name,
                                amount = computed?.spent ?: 0.0,
                                expected = expense.expected,
                            )
                        ),
                    )
                }

                budgetDao.deleteAll()
                budgetDao.insertAll(budgets.map { BudgetEntity.fromDomain(it) })
                emit(Resource.Success(budgets))
            } else {
                emit(Resource.Error("Failed to fetch budgets: ${budgetResponse.code()}"))
            }
        } catch (e: Exception) {
            budgetDao.getAll().collect { cached ->
                if (cached.isNotEmpty()) {
                    emit(Resource.Success(cached.map { it.toDomain() }))
                } else {
                    emit(Resource.Error(e.message ?: "Network error"))
                }
            }
        }
    }

    override suspend fun searchExpenses(name: String): Resource<List<BudgetExpense>> {
        return try {
            val response = apiService.getExpenses(name = name.takeIf { it.isNotBlank() })
            if (response.isSuccessful) {
                val expenses = response.body()?.map { dto ->
                    BudgetExpense(
                        id = dto.id,
                        name = dto.name,
                        amount = 0.0,
                        expected = dto.expected,
                    )
                } ?: emptyList()
                Resource.Success(expenses)
            } else {
                Resource.Error("Failed to search expenses")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getBudget(id: Long): Resource<Budget> {
        return try {
            val cached = budgetDao.getById(id)
            if (cached != null) Resource.Success(cached.toDomain())
            else Resource.Error("Budget not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Error fetching budget")
        }
    }

    override suspend fun createBudget(budget: Budget): Resource<Budget> {
        return Resource.Error("Not implemented via API yet")
    }

    override suspend fun updateBudget(budget: Budget): Resource<Budget> {
        return Resource.Error("Not implemented via API yet")
    }

    override suspend fun deleteBudget(id: Long): Resource<Unit> {
        return Resource.Error("Not implemented via API yet")
    }
}
