package com.pledgerio.app.transactions.data

import com.pledgerio.app.transactions.data.TransactionMutationInvalidator
import com.pledgerio.app.shared.data.cache.CacheRefresher
import com.pledgerio.app.shared.data.cache.SyncKeys
import com.pledgerio.app.reports.domain.repository.ReportsOverviewStore
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TransactionMutationInvalidatorTest {

    private val reportsOverviewStore = mockk<ReportsOverviewStore>(relaxed = true)
    private val cacheRefresher = mockk<CacheRefresher>(relaxed = true)
    private val invalidator = TransactionMutationInvalidator(
        reportsOverviewStore,
        cacheRefresher,
    )

    @Test
    fun `invalidate clears report cache for transaction month and next month MoM`() = runTest {
        val date = LocalDate.of(2026, 7, 18)
        val month = YearMonth.from(date)

        invalidator.invalidate(date)

        coVerifyOrder {
            reportsOverviewStore.invalidate(month)
            reportsOverviewStore.invalidate(month.plusMonths(1))
            cacheRefresher.invalidate(SyncKeys.budgetMonth(month))
        }
    }

    @Test
    fun `invalidate ignores null dates`() = runTest {
        invalidator.invalidate(null)

        coVerify(exactly = 0) { reportsOverviewStore.invalidate(any()) }
        coVerify(exactly = 0) { cacheRefresher.invalidate(any()) }
    }

    @Test
    fun `invalidate marks owned account balances stale`() = runTest {
        invalidator.invalidate(LocalDate.of(2026, 7, 18))

        coVerify { cacheRefresher.invalidate(SyncKeys.OWNED_ACCOUNTS) }
    }
}
