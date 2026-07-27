package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.domain.repository.ReportsOverviewStore
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Invalidates only derived data affected by a transaction mutation.
 */
@Singleton
class TransactionMutationInvalidator @Inject constructor(
    private val reportsOverviewStore: ReportsOverviewStore,
    private val cacheRefresher: CacheRefresher,
) {
    suspend fun invalidate(date: LocalDate?) {
        if (date == null) return
        val month = YearMonth.from(date)
        // Next month's overview embeds prior-month MoM totals; clear both.
        val reportMonths = listOf(month, month.plusMonths(1))
        var failure: Exception? = null
        for (reportMonth in reportMonths) {
            try {
                reportsOverviewStore.invalidate(reportMonth)
            } catch (error: Exception) {
                failure?.addSuppressed(error) ?: run { failure = error }
            }
        }
        try {
            cacheRefresher.invalidate(SyncKeys.budgetMonth(month))
        } catch (error: Exception) {
            failure?.addSuppressed(error) ?: throw error
        }
        failure?.let { throw it }
    }
}
