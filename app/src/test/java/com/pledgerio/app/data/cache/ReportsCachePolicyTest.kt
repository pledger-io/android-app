package com.pledgerio.app.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class ReportsCachePolicyTest {

    private val now = YearMonth.of(2026, 5)

    @Test
    fun `current month uses short ttl`() {
        val fetchedAt = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)
        assertTrue(ReportsCachePolicy.isFresh(fetchedAt, now, now))

        val staleAt = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20)
        assertFalse(ReportsCachePolicy.isFresh(staleAt, now, now))
    }

    @Test
    fun `historical month uses long ttl`() {
        val historical = YearMonth.of(2024, 1)
        val fetchedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        assertTrue(ReportsCachePolicy.isFresh(fetchedAt, historical, now))

        val staleAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)
        assertFalse(ReportsCachePolicy.isFresh(staleAt, historical, now))
    }
}
