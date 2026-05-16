package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.local.entity.BudgetEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CreateBudgetRequest
import com.pledgerio.app.data.remote.dto.ExpenseComputedDto
import com.pledgerio.app.data.remote.dto.ExpenseDto
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetExpense
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val budgetDao: BudgetDao,
) : BudgetRepository {

    override fun getBudgets(year: Int, month: Int): Flow<Resource<BudgetListState>> = flow {
        emit(Resource.Loading)

        try {
            val budgetResponse = apiService.getBudgets(year = year, month = month)
            when {
                budgetResponse.code() == 404 -> {
                    emit(Resource.Success(BudgetListState(needsInitialSetup = true)))
                }
                budgetResponse.isSuccessful -> {
                    val dto = budgetResponse.body() ?: run {
                        emit(Resource.Error("Empty budget response"))
                        return@flow
                    }

                    val balanceResponse = apiService.getExpenseBalance(year, month)
                    val balances = if (balanceResponse.isSuccessful) {
                        balanceResponse.body()?.associateBy { it.id } ?: emptyMap()
                    } else {
                        emptyMap()
                    }

                    val budgets = mapBudgets(dto.expenses, balances)
                    budgetDao.deleteAll()
                    budgetDao.insertAll(budgets.map { BudgetEntity.fromDomain(it) })
                    emit(Resource.Success(BudgetListState(budgets = budgets)))
                }
                else -> {
                    emit(Resource.Error("Failed to fetch budgets: ${budgetResponse.code()}"))
                }
            }
        } catch (e: Exception) {
            budgetDao.getAll().collect { cached ->
                if (cached.isNotEmpty()) {
                    emit(Resource.Success(BudgetListState(budgets = cached.map { it.toDomain() })))
                } else {
                    emit(Resource.Error(e.message ?: "Network error"))
                }
            }
        }
    }

    override suspend fun createInitialBudget(year: Int, month: Int, income: Double): Resource<Unit> {
        return try {
            val response = apiService.createInitialBudget(
                CreateBudgetRequest(year = year, month = month, income = income),
            )
            when {
                response.isSuccessful -> Resource.Success(Unit)
                response.code() == 400 -> Resource.Error(
                    "A budget already exists for this period.",
                )
                else -> Resource.Error("Failed to create budget: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
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

    private fun mapBudgets(
        expenses: List<ExpenseDto>,
        balances: Map<Long, ExpenseComputedDto>,
    ): List<Budget> = expenses.map { expense ->
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
                ),
            ),
        )
    }
}
