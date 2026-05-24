package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.BalanceRequest
import com.pledgerio.app.data.remote.dto.DateRangeDto
import com.pledgerio.app.domain.model.BudgetPerformanceItem
import com.pledgerio.app.domain.model.DatedAmount
import com.pledgerio.app.domain.model.IncomeExpenseSummary
import com.pledgerio.app.domain.model.PartitionAmount
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.ReportRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
) : ReportRepository {

    override suspend fun getIncomeExpenseSummary(month: YearMonth): Resource<IncomeExpenseSummary> {
        return try {
            var income = 0.0
            var expense = 0.0
            var offset = 0
            val pageSize = 100
            val range = monthRange(month)
            while (true) {
                val response = apiService.getTransactions(
                    startDate = range.startDate,
                    endDate = range.endDate ?: month.atEndOfMonth().plusDays(1).toString(),
                    offset = offset,
                    numberOfResults = pageSize,
                )
                if (!response.isSuccessful) {
                    return Resource.Error("Failed to load transactions: HTTP ${response.code()}")
                }
                val body = response.body() ?: break
                body.content.forEach { dto ->
                    when (TransactionType.fromString(dto.type)) {
                        TransactionType.DEBIT -> income += dto.amount
                        TransactionType.CREDIT -> expense += dto.amount
                        TransactionType.TRANSFER -> Unit
                    }
                }
                val loaded = offset + body.content.size
                if (body.content.isEmpty() || loaded >= body.info.records) break
                offset = loaded
            }
            Resource.Success(IncomeExpenseSummary(income = income, expense = expense))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not load income and expenses")
        }
    }

    override suspend fun getCategoryBreakdown(month: YearMonth): Resource<List<PartitionAmount>> =
        loadPartitioned("category", month)

    override suspend fun getAccountBalances(month: YearMonth): Resource<List<PartitionAmount>> {
        return try {
            when (val owned = accountRepository.refreshOwnedAccounts()) {
                is Resource.Success -> {
                    val accounts = owned.data
                    if (accounts.isEmpty()) {
                        return Resource.Success(emptyList())
                    }
                    val response = apiService.getPartitionedBalance(
                        partition = "account",
                        request = BalanceRequest(
                            range = monthRange(month),
                            //accounts = accounts.map { it.id },
                        ),
                    )
                    if (!response.isSuccessful) {
                        return Resource.Error("Failed to load report: HTTP ${response.code()}")
                    }
                    val balancesByName = response.body()
                        ?.associateBy({ it.partition }, { it.balance })
                        ?: emptyMap()
                    val partitions = accounts
                        .map { account ->
                            PartitionAmount(
                                label = account.name,
                                amount = kotlin.math.abs(balancesByName[account.name] ?: 0.0),
                            )
                        }
                        .sortedByDescending { it.amount }
                    Resource.Success(partitions)
                }
                is Resource.Error -> Resource.Error(owned.message)
                is Resource.Loading -> Resource.Error("Account data unavailable")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not load report data")
        }
    }

    override suspend fun getBudgetPerformance(month: YearMonth): Resource<List<BudgetPerformanceItem>> {
        return try {
            when (val result = budgetRepository.getBudgets(month.year, month.monthValue).first { it !is Resource.Loading }) {
                is Resource.Success -> {
                    val items = result.data.budgets.map { budget ->
                        BudgetPerformanceItem(
                            name = budget.name,
                            spent = budget.spent,
                            budgeted = budget.amount,
                        )
                    }
                    Resource.Success(items)
                }
                is Resource.Error -> Resource.Error(result.message)
                is Resource.Loading -> Resource.Error("Budget data unavailable")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not load budget performance")
        }
    }

    override suspend fun getNetWorthTrend(month: YearMonth): Resource<List<DatedAmount>> {
        return try {
            val dateRange = DateRangeDto(startDate = "1970-01-01", endDate = month.plusMonths(1).atDay(1).toString())
            val response = apiService.getDatedBalance(
                type = "daily",
                request = BalanceRequest(range = dateRange),
            )
            if (!response.isSuccessful) {
                return Resource.Error("Failed to load net worth trend: HTTP ${response.code()}")
            }
            val items = response.body().orEmpty().map { DatedAmount(date = it.date, amount = it.balance) }
            Resource.Success(items)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not load net worth trend")
        }
    }

    private suspend fun loadPartitioned(
        partition: String,
        month: YearMonth,
    ): Resource<List<PartitionAmount>> {
        return try {
            val response = apiService.getPartitionedBalance(
                partition = partition,
                request = BalanceRequest(range = monthRange(month)),
            )
            if (!response.isSuccessful) {
                return Resource.Error("Failed to load report: HTTP ${response.code()}")
            }
            val items = response.body().orEmpty()
                .map { PartitionAmount(label = it.partition, amount = kotlin.math.abs(it.balance)) }
                .filter { it.amount > 0.0 }
                .sortedByDescending { it.amount }
            Resource.Success(items)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not load report data")
        }
    }

    private fun monthRange(month: YearMonth): DateRangeDto {
        val start = month.atDay(1).toString()
        val end = month.plusMonths(1).atDay(1).toString()
        return DateRangeDto(startDate = start, endDate = end)
    }
}
