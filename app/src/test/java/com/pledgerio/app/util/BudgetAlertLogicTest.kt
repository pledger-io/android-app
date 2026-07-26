package com.pledgerio.app.util

import com.pledgerio.app.domain.model.Budget
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertLogicTest {

    private val yearMonth = YearMonth.of(2026, 7)

    @Test
    fun `defaults match design`() {
        assertTrue(BudgetAlertLogic.DEFAULT_ENABLED)
        assertEquals(80, BudgetAlertLogic.DEFAULT_THRESHOLD_PERCENT)
        assertEquals(listOf(50, 70, 80, 90, 100), BudgetAlertLogic.VALID_THRESHOLDS)
        assertEquals(1001, BudgetAlertLogic.NOTIFICATION_ID)
        assertEquals("budget_alerts", BudgetAlertLogic.CHANNEL_ID)
    }

    @Test
    fun `normalizeThresholdPercent keeps valid values`() {
        BudgetAlertLogic.VALID_THRESHOLDS.forEach { percent ->
            assertEquals(percent, BudgetAlertLogic.normalizeThresholdPercent(percent))
        }
    }

    @Test
    fun `normalizeThresholdPercent snaps invalid values to nearest valid`() {
        assertEquals(50, BudgetAlertLogic.normalizeThresholdPercent(40))
        assertEquals(70, BudgetAlertLogic.normalizeThresholdPercent(65))
        assertEquals(80, BudgetAlertLogic.normalizeThresholdPercent(85))
        assertEquals(100, BudgetAlertLogic.normalizeThresholdPercent(200))
    }

    @Test
    fun `filterOverThreshold returns empty when all below threshold`() {
        val budgets = listOf(
            budget(1, "Food", amount = 100.0, spent = 70.0),
            budget(2, "Rent", amount = 100.0, spent = 50.0),
        )
        assertTrue(BudgetAlertLogic.filterOverThreshold(budgets, 80).isEmpty())
    }

    @Test
    fun `filterOverThreshold includes budgets at and above threshold`() {
        val budgets = listOf(
            budget(1, "Food", amount = 100.0, spent = 80.0),
            budget(2, "Rent", amount = 100.0, spent = 79.0),
            budget(3, "Fun", amount = 100.0, spent = 95.0),
        )
        val over = BudgetAlertLogic.filterOverThreshold(budgets, 80)
        assertEquals(listOf(1L, 3L), over.map { it.id })
    }

    @Test
    fun `filterOverThreshold respects configured threshold`() {
        val budgets = listOf(
            budget(1, "Food", amount = 100.0, spent = 55.0),
            budget(2, "Rent", amount = 100.0, spent = 75.0),
        )
        assertEquals(listOf(1L, 2L), BudgetAlertLogic.filterOverThreshold(budgets, 50).map { it.id })
        assertEquals(listOf(2L), BudgetAlertLogic.filterOverThreshold(budgets, 70).map { it.id })
        assertTrue(BudgetAlertLogic.filterOverThreshold(budgets, 90).isEmpty())
    }

    @Test
    fun `buildFingerprint sorts ids and includes month and threshold`() {
        val fingerprint = BudgetAlertLogic.buildFingerprint(
            yearMonth = yearMonth,
            thresholdPercent = 80,
            overBudgetIds = listOf(45L, 12L),
        )
        assertEquals("2026-07|80|12,45", fingerprint)
    }

    @Test
    fun `buildFingerprint normalizes threshold`() {
        val fingerprint = BudgetAlertLogic.buildFingerprint(
            yearMonth = yearMonth,
            thresholdPercent = 82,
            overBudgetIds = listOf(1L),
        )
        assertEquals("2026-07|80|1", fingerprint)
    }

    @Test
    fun `isFingerprintNew suppresses identical fingerprints`() {
        val fingerprint = "2026-07|80|12,45"
        assertTrue(BudgetAlertLogic.isFingerprintNew(null, fingerprint))
        assertTrue(BudgetAlertLogic.isFingerprintNew("other", fingerprint))
        assertFalse(BudgetAlertLogic.isFingerprintNew(fingerprint, fingerprint))
    }

    @Test
    fun `budgetsDeepLinkUri matches DeepLinkParser format`() {
        assertEquals(
            "pledger://budgets?year=2026&month=7",
            BudgetAlertLogic.budgetsDeepLinkUri(yearMonth),
        )
    }

    private fun budget(id: Long, name: String, amount: Double, spent: Double) =
        Budget(id = id, name = name, amount = amount, spent = spent)
}
