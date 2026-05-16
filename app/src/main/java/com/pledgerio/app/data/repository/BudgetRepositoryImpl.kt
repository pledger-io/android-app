package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.local.entity.BudgetEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CreateBudgetRequest
import com.pledgerio.app.data.remote.dto.ExpenseRequest
import com.pledgerio.app.data.remote.dto.ExpenseComputedDto
import com.pledgerio.app.data.remote.dto.ExpenseDto
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetExpense
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val budgetDao: BudgetDao,
) : BudgetRepository {

    override fun getBudgets(year: Int, month: Int): Flow<Resource<BudgetListState>> = flow {
        emit(Resource.Loading)
        emit(fetchAndCacheBudgets(year, month))
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

    override suspend fun saveExpenseGroup(
        id: Long?,
        name: String,
        budgetAmount: Double,
    ): Resource<BudgetListState> {
        return try {
            val response = apiService.saveExpense(
                ExpenseRequest(
                    id = id,
                    name = name.trim(),
                    amount = budgetAmount,
                ),
            )
            when {
                !response.isSuccessful -> when (response.code()) {
                    404 -> Resource.Error("No active budget found. Create a budget first.")
                    400 -> Resource.Error("Could not save expense group. Check the name and amount.")
                    else -> Resource.Error("Failed to save expense group: HTTP ${response.code()}")
                }
                else -> {
                    val now = LocalDate.now()
                    fetchAndCacheBudgets(now.year, now.monthValue)
                }
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

    private suspend fun fetchAndCacheBudgets(year: Int, month: Int): Resource<BudgetListState> {
        return try {
            val budgetResponse = apiService.getBudgets(year = year, month = month)
            when {
                budgetResponse.code() == 404 -> Resource.Success(BudgetListState(needsInitialSetup = true))
                budgetResponse.isSuccessful -> {
                    val dto = budgetResponse.body()
                        ?: return Resource.Error("Empty budget response")
                    val balanceResponse = apiService.getExpenseBalance(year, month)
                    val balances = if (balanceResponse.isSuccessful) {
                        balanceResponse.body()?.associateBy { it.id } ?: emptyMap()
                    } else {
                        emptyMap()
                    }
                    val budgets = mapBudgets(dto.expenses, balances)
                    cacheBudgets(budgets)
                    Resource.Success(BudgetListState(budgets = budgets))
                }
                else -> Resource.Error("Failed to fetch budgets: ${budgetResponse.code()}")
            }
        } catch (e: Exception) {
            val cached = budgetDao.getAll().first()
            if (cached.isNotEmpty()) {
                Resource.Success(BudgetListState(budgets = cached.map { it.toDomain() }))
            } else {
                Resource.Error(e.message ?: "Network error")
            }
        }
    }

    private suspend fun cacheBudgets(budgets: List<Budget>) {
        budgetDao.deleteAll()
        if (budgets.isNotEmpty()) {
            budgetDao.insertAll(budgets.map { BudgetEntity.fromDomain(it) })
        }
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
