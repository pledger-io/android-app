package com.pledgerio.app.data.cache

import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * TTL for cached [com.pledgerio.app.domain.model.ReportsOverview] snapshots.
 * Historical months change rarely; the current month is refreshed more often.
 */
object ReportsCachePolicy {

    fun ttlForMonth(month: YearMonth, now: YearMonth = YearMonth.now()): Long = when {
        !month.isBefore(now) -> CURRENT_MONTH_TTL_MS
        month == now.minusMonths(1) -> PREVIOUS_MONTH_TTL_MS
        else -> HISTORICAL_MONTH_TTL_MS
    }

    fun isFresh(
        fetchedAtMillis: Long,
        month: YearMonth,
        now: YearMonth = YearMonth.now(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val ttl = ttlForMonth(month, now)
        return nowMillis - fetchedAtMillis < ttl
    }

    private val CURRENT_MONTH_TTL_MS = TimeUnit.MINUTES.toMillis(15)
    private val PREVIOUS_MONTH_TTL_MS = TimeUnit.HOURS.toMillis(1)
    private val HISTORICAL_MONTH_TTL_MS = TimeUnit.DAYS.toMillis(7)
}
