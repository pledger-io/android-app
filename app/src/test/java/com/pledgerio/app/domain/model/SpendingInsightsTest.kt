package com.pledgerio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingInsightsTest {

    private fun insight(
        severity: InsightSeverity,
        score: Double,
        message: String,
    ) = SpendingInsight(
        type = InsightType.SPENDING_SPIKE,
        severity = severity,
        category = "Groceries",
        message = message,
        score = score,
        detectedDate = null,
    )

    @Test
    fun `highlights order by severity then score`() {
        val insights = SpendingInsights(
            insights = listOf(
                insight(InsightSeverity.INFO, score = 0.9, message = "info"),
                insight(InsightSeverity.ALERT, score = 0.2, message = "alert-low"),
                insight(InsightSeverity.WARNING, score = 0.5, message = "warning"),
                insight(InsightSeverity.ALERT, score = 0.7, message = "alert-high"),
            ),
        )

        val highlights = insights.highlights(limit = 3)

        assertEquals(
            listOf("alert-high", "alert-low", "warning"),
            highlights.map { it.message },
        )
    }

    @Test
    fun `isEmpty only when both lists are empty`() {
        assertTrue(SpendingInsights().isEmpty)
        assertTrue(
            !SpendingInsights(
                patterns = listOf(
                    SpendingPattern(
                        type = PatternType.SEASONAL,
                        category = "Travel",
                        confidence = 0.5,
                        detectedDate = null,
                    ),
                ),
            ).isEmpty,
        )
    }
}
