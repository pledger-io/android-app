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
        val requested = YearMonth.of(year, month)
        if (isRoomSnapshotFor(requested)) {
            val cached = budgetDao.getAll().first()
            emit(
                Resource.Success(
                    BudgetListState(
                        budgets = cached.map { it.toDomain() },
                        income = null,
                    ),
                ),
            )
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

    override suspend fun updateBudgetIncome(
        year: Int,
        month: Int,
        income: Double,
    ): Resource<BudgetListState> {
        return try {
            val response = apiService.updateBudgetIncome(
                CreateBudgetRequest(year = year, month = month, income = income),
            )
            when {
                !response.isSuccessful -> when (response.code()) {
                    404 -> Resource.Error("No budget found for this period.")
                    400 -> Resource.Error("Could not update income. Check the amount.")
                    else -> Resource.Error("Failed to update income: HTTP ${response.code()}")
                }
                else -> fetchAndCacheBudgets(year, month)
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun saveExpenseGroup(
        id: Long?,
        name: String,
        budgetAmount: Double,
        year: Int,
        month: Int,
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
                    cacheRefresher.refreshInBackground(SyncKeys.EXPENSE_GROUPS) {
                        refreshExpenseGroupsUnlocked()
                    }
                    fetchAndCacheBudgets(year, month)
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
            refreshExpenseGroupsUnlocked()
        }
    }

    private suspend fun refreshExpenseGroupsUnlocked(): Resource<List<BudgetExpense>> {
        return try {
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

    override suspend fun getBudget(id: Long): Resource<Budget> {
        return try {
            val cached = budgetDao.getById(id)
            if (cached != null) {
                val now = LocalDate.now()
                val month = YearMonth.from(now)
                // Refresh in the background so the next read shows fresh spent amounts.
                // Avoid markFresh on 404 Success (needsInitialSetup) from CacheRefresher.refreshNow.
                cacheRefresher.refreshInBackground(SyncKeys.budgetMonth(month)) {
                    when (val result = fetchAndCacheBudgets(now.year, now.monthValue)) {
                        is Resource.Success -> if (result.data.needsInitialSetup) {
                            Resource.Error("No budget for period")
                        } else {
                            result
                        }
                        else -> result
                    }
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
        val requested = YearMonth.of(year, month)
        return try {
            val budgetResponse = apiService.getBudgets(year = year, month = month)
            when {
                budgetResponse.code() == 404 -> {
                    clearBudgetSnapshot(requested)
                    Resource.Success(BudgetListState(needsInitialSetup = true))
                }
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
                    replaceBudgetSnapshot(requested, budgets)
                    cacheExpenseGroups(dto.expenses)
                    Resource.Success(
                        BudgetListState(
                            budgets = budgets,
                            income = dto.income,
                        ),
                    )
                }
                else -> Resource.Error("Failed to fetch budgets: ${budgetResponse.code()}")
            }
        } catch (e: Exception) {
            // Prefer the Room snapshot for this month even if budgetMonth was invalidated
            // (e.g. after a transaction mutation) so offline reads still work.
            if (readRoomBudgetMonth() == requested) {
                val cached = budgetDao.getAll().first()
                if (cached.isNotEmpty()) {
                    return Resource.Success(
                        BudgetListState(
                            budgets = cached.map { it.toDomain() },
                            income = null,
                        ),
                    )
                }
            }
            Resource.Error(e.message ?: "Network error")
        }
    }

    private suspend fun isRoomSnapshotFor(month: YearMonth): Boolean {
        val roomMonth = readRoomBudgetMonth() ?: return false
        if (roomMonth != month) return false
        return cacheRefresher.lastSyncedAt(SyncKeys.budgetMonth(month)) != null
    }

    private suspend fun replaceBudgetSnapshot(month: YearMonth, budgets: List<Budget>) {
        val previous = readRoomBudgetMonth()
        if (previous != null && previous != month) {
            cacheRefresher.invalidate(SyncKeys.budgetMonth(previous))
        }
        budgetDao.deleteAll()
        if (budgets.isNotEmpty()) {
            budgetDao.insertAll(budgets.map { BudgetEntity.fromDomain(it) })
        }
        writeRoomBudgetMonth(month)
        cacheRefresher.markFresh(SyncKeys.budgetMonth(month))
    }

    private suspend fun clearBudgetSnapshot(month: YearMonth) {
        val previous = readRoomBudgetMonth()
        if (previous == null || previous == month) {
            budgetDao.deleteAll()
            cacheRefresher.invalidate(SyncKeys.BUDGET_ROOM_MONTH)
        }
        cacheRefresher.invalidate(SyncKeys.budgetMonth(month))
    }

    private suspend fun readRoomBudgetMonth(): YearMonth? {
        val packed = cacheRefresher.lastSyncedAt(SyncKeys.BUDGET_ROOM_MONTH) ?: return null
        val year = (packed / 100L).toInt()
        val monthValue = (packed % 100L).toInt()
        if (monthValue !in 1..12 || year < 1) return null
        return YearMonth.of(year, monthValue)
    }

    private suspend fun writeRoomBudgetMonth(month: YearMonth) {
        cacheRefresher.markFresh(
            SyncKeys.BUDGET_ROOM_MONTH,
            at = month.year * 100L + month.monthValue,
        )
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
        ) { refreshExpenseGroupsUnlocked() }
    }
}

/** Spent outflows from the API are negative; normalize to a positive amount for display. */
private fun ExpenseComputedDto.spentAsPositive(): Double = abs(spent)
