package com.pledgerio.app.data.cache

import com.pledgerio.app.domain.model.ReportsOverview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ReportsOverviewCacheTest {

    private val cache = ReportsOverviewCache()
    private val month = YearMonth.of(2026, 3)
    private val overview = ReportsOverview(incomeExpense = null)

    @Test
    fun `put and get round trip`() = runTest {
        cache.put(month, overview, fetchedAtMillis = 1_000L)
        val entry = cache.get(month)
        assertEquals(overview, entry?.overview)
        assertEquals(1_000L, entry?.fetchedAtMillis)
    }

    @Test
    fun `invalidate removes entry`() = runTest {
        cache.put(month, overview)
        cache.invalidate(month)
        assertNull(cache.get(month))
    }

    @Test
    fun `clearAll removes all entries`() = runTest {
        cache.put(month, overview)
        cache.put(month.plusMonths(1), overview)
        cache.clearAll()
        assertNull(cache.get(month))
        assertNull(cache.get(month.plusMonths(1)))
    }

    @Test
    fun `entry freshness follows policy`() {
        val entry = ReportsOverviewCache.Entry(
            overview = overview,
            fetchedAtMillis = System.currentTimeMillis(),
        )
        assertTrue(entry.isFresh(YearMonth.now()))
    }
}
