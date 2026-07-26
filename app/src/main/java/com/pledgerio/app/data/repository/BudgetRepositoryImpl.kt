package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CachePolicy
import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.BudgetDao
import com.pledgerio.app.data.local.dao.ExpenseGroupDao
import com.pledgerio.app.data.local.entity.BudgetEntity
import com.pledgerio.app.data.local.entity.ExpenseGroupEntity
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val budgetDao: BudgetDao,
    private val expenseGroupDao: ExpenseGroupDao,
    private val cacheRefresher: CacheRefresher,
) : BudgetRepository {

    override fun getBudgets(year: Int, month: Int): Flow<Resource<BudgetListState>> = flow {
        emit(Resource.Loading)
        val cached = budgetDao.getAll().first()
        if (cached.isNotEmpty()) {
            emit(Resource.Success(BudgetListState(budgets = cached.map { it.toDomain() })))
        }
        emit(fetchAndCacheBudgets(year, month))
    }

    override fun observeExpenseGroups(query: String): Flow<List<BudgetExpense>> =
        expenseGroupDao.observeMatching(query.trim())
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .onStart { triggerExpenseGroupRefresh() }

    override suspend fun createInitialBudget(year: Int, month: Int, income: Double): Resource<Unit> {
        return try {
            val response = apiService.createInitialBudget(
                CreateBudgetRequest(year = year, month = month, income = income),
            )
            when {
                response.isSuccessful -> {
                    cacheRefresher.invalidate(SyncKeys.EXPENSE_GROUPS)
                    Resource.Success(Unit)
                }
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
                    cacheRefresher.invalidate(SyncKeys.EXPENSE_GROUPS)
                    cacheRefresher.refreshInBackground(SyncKeys.EXPENSE_GROUPS) { refreshExpenseGroups() }
                    val now = LocalDate.now()
                    fetchAndCacheBudgets(now.year, now.monthValue)
                }
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun searchExpenses(name: String): Resource<List<BudgetExpense>> {
        triggerExpenseGroupRefresh()
        val query = name.trim()
        val cached = expenseGroupDao.searchOnce(query, limit = 20).map { it.toDomain() }
        if (cached.isNotEmpty()) return Resource.Success(cached)
        return when (val refreshed = refreshExpenseGroups()) {
            is Resource.Success -> Resource.Success(
                refreshed.data.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) },
            )
            else -> refreshed
        }
    }

    override suspend fun refreshExpenseGroups(): Resource<List<BudgetExpense>> {
        return cacheRefresher.refreshNow(SyncKeys.EXPENSE_GROUPS) {
            try {
                val response = apiService.getExpenses()
                if (response.isSuccessful) {
                    val expenses = response.body()?.map { dto ->
                        BudgetExpense(
                            id = dto.id,
                            name = dto.name,
                            amount = 0.0,
                            expected = dto.expected,
                        )
                    } ?: emptyList()
                    expenseGroupDao.replaceAll(expenses.map { ExpenseGroupEntity.fromExpense(it) })
                    Resource.Success(expenses)
                } else {
                    Resource.Error("Failed to fetch expense groups: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Network error")
            }
        }
    }

    override suspend fun getBudget(id: Long): Resource<Budget> {
        return try {
            val cached = budgetDao.getById(id)
            if (cached != null) {
                // Refresh in the background so the next read shows fresh spent amounts.
                cacheRefresher.refreshInBackground(
                    SyncKeys.budgetMonth(YearMonth.from(LocalDate.now())),
                ) {
                    val now = LocalDate.now()
                    fetchAndCacheBudgets(now.year, now.monthValue)
                }
                return Resource.Success(cached.toDomain())
            }
            val now = LocalDate.now()
            when (val refreshed = fetchAndCacheBudgets(now.year, now.monthValue)) {
                is Resource.Success -> {
                    val match = refreshed.data.budgets.firstOrNull { it.id == id }
                    if (match != null) Resource.Success(match) else Resource.Error("Budget not found")
                }
                is Resource.Error -> Resource.Error(refreshed.message)
                is Resource.Loading -> Resource.Error("Budget not found")
            }
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
                    cacheExpenseGroups(dto.expenses)
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

    private suspend fun cacheExpenseGroups(expenses: List<ExpenseDto>) {
        val entities = expenses.map {
            ExpenseGroupEntity(id = it.id, name = it.name, expected = it.expected)
        }
        expenseGroupDao.replaceAll(entities)
        cacheRefresher.markFresh(SyncKeys.EXPENSE_GROUPS)
    }

    private fun mapBudgets(
        expenses: List<ExpenseDto>,
        balances: Map<Long, ExpenseComputedDto>,
    ): List<Budget> = expenses.map { expense ->
        val computed = balances[expense.id]
        // The balance API reports outflows as negative amounts; the UI treats spent as
        // a positive magnitude when computing remaining budget and progress.
        val spent = computed?.spentAsPositive() ?: 0.0
        Budget(
            id = expense.id,
            name = expense.name,
            amount = expense.expected,
            spent = spent,
            expenses = listOf(
                BudgetExpense(
                    id = expense.id,
                    name = expense.name,
                    amount = spent,
                    expected = expense.expected,
                ),
            ),
        )
    }

    private fun triggerExpenseGroupRefresh() {
        cacheRefresher.launchIfStale(
            key = SyncKeys.EXPENSE_GROUPS,
            ttlMs = CachePolicy.EXPENSE_GROUPS_TTL_MS,
        ) { refreshExpenseGroups() }
    }
}

/** Spent outflows from the API are negative; normalize to a positive amount for display. */
private fun ExpenseComputedDto.spentAsPositive(): Double = abs(spent)
