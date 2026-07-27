package com.pledgerio.app.util

import com.pledgerio.app.domain.model.Budget
import java.time.YearMonth

/**
 * Pure helpers for budget alert threshold filtering, fingerprint dedup, and deep-link URIs.
 * Kept free of Android framework types so unit tests need no WorkManager or Context.
 */
object BudgetAlertLogic {
    val VALID_THRESHOLDS: List<Int> = listOf(50, 70, 80, 90, 100)
    const val DEFAULT_THRESHOLD_PERCENT = 80
    const val DEFAULT_ENABLED = true
    const val CHANNEL_ID = "budget_alerts"
    const val NOTIFICATION_ID = 1001

    fun normalizeThresholdPercent(percent: Int): Int =
        VALID_THRESHOLDS.minByOrNull { kotlin.math.abs(it - percent) }
            ?: DEFAULT_THRESHOLD_PERCENT

    fun filterOverThreshold(budgets: List<Budget>, thresholdPercent: Int): List<Budget> {
        val fraction = normalizeThresholdPercent(thresholdPercent) / 100f
        return budgets.filter { it.percentUsed >= fraction }
    }

    /**
     * Format: `"{yearMonth}|{threshold}|{sortedBudgetIds joined by comma}"`
     * e.g. `2026-07|80|12,45`
     */
    fun buildFingerprint(
        yearMonth: YearMonth,
        thresholdPercent: Int,
        overBudgetIds: Collection<Long>,
    ): String {
        val threshold = normalizeThresholdPercent(thresholdPercent)
        val ids = overBudgetIds.sorted().joinToString(",")
        return "$yearMonth|$threshold|$ids"
    }

    /** Returns true when [fingerprint] is new relative to [previous] and should trigger a notify. */
    fun isFingerprintNew(previous: String?, fingerprint: String): Boolean =
        previous != fingerprint

    fun budgetsDeepLinkUri(yearMonth: YearMonth): String =
        "pledger://budgets?year=${yearMonth.year}&month=${yearMonth.monthValue}"
}
