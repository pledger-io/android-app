package com.pledgerio.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Preference defaults and validation that do not require a DataStore/Android Context.
 * DataStore persistence is exercised indirectly via SettingsViewModel tests.
 */
class BudgetAlertPreferencesTest {

    @Test
    fun `default enabled and threshold match previous hardcoded behavior`() {
        assertTrue(BudgetAlertLogic.DEFAULT_ENABLED)
        assertEquals(80, BudgetAlertLogic.DEFAULT_THRESHOLD_PERCENT)
    }

    @Test
    fun `threshold validation accepts only discrete percents`() {
        assertEquals(50, BudgetAlertLogic.normalizeThresholdPercent(50))
        assertEquals(70, BudgetAlertLogic.normalizeThresholdPercent(70))
        assertEquals(80, BudgetAlertLogic.normalizeThresholdPercent(80))
        assertEquals(90, BudgetAlertLogic.normalizeThresholdPercent(90))
        assertEquals(100, BudgetAlertLogic.normalizeThresholdPercent(100))
    }

    @Test
    fun `threshold validation clamps unsupported values`() {
        assertEquals(80, BudgetAlertLogic.normalizeThresholdPercent(81))
        assertEquals(50, BudgetAlertLogic.normalizeThresholdPercent(0))
        assertEquals(100, BudgetAlertLogic.normalizeThresholdPercent(999))
    }

    @Test
    fun `fingerprint consume helper notifies once then suppresses`() {
        val fingerprint = BudgetAlertLogic.buildFingerprint(
            yearMonth = java.time.YearMonth.of(2026, 7),
            thresholdPercent = 80,
            overBudgetIds = listOf(12L, 45L),
        )
        var stored: String? = null

        fun consume(candidate: String): Boolean {
            if (!BudgetAlertLogic.isFingerprintNew(stored, candidate)) return false
            stored = candidate
            return true
        }

        assertTrue(consume(fingerprint))
        assertFalse(consume(fingerprint))
        assertTrue(consume(fingerprint.replace("|12,45", "|12,45,99")))
    }
}
