package com.pledgerio.app.reports.domain.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.reports.domain.model.BudgetPerformanceItem
import com.pledgerio.app.reports.domain.model.DatedAmount
import com.pledgerio.app.reports.domain.model.IncomeExpenseSummary
import com.pledgerio.app.reports.domain.model.PartitionAmount
import java.time.YearMonth

interface ReportRepository {
    suspend fun getIncomeExpenseSummary(month: YearMonth): Resource<IncomeExpenseSummary>
    suspend fun getCategoryBreakdown(month: YearMonth): Resource<List<PartitionAmount>>
    suspend fun getAccountBalances(month: YearMonth): Resource<List<PartitionAmount>>
    suspend fun getBudgetPerformance(month: YearMonth): Resource<List<BudgetPerformanceItem>>
    suspend fun getNetWorthTrend(month: YearMonth): Resource<List<DatedAmount>>
}
