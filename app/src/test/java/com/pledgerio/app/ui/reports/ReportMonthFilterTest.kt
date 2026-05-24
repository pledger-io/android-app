package com.pledgerio.app.ui.reports

import com.pledgerio.app.domain.model.DatedAmount
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class ReportMonthFilterTest {

    @Test
    fun `inMonth keeps only dates within selected month`() {
        val month = YearMonth.of(2026, 5)
        val points = listOf(
            DatedAmount("2026-04-30", 900.0),
            DatedAmount("2026-05-01", 1000.0),
            DatedAmount("2026-05-15", 1100.0),
            DatedAmount("2026-06-01", 1200.0),
        )

        val filtered = points.inMonth(month)

        assertEquals(2, filtered.size)
        assertEquals("2026-05-01", filtered[0].date)
        assertEquals("2026-05-15", filtered[1].date)
    }
}
