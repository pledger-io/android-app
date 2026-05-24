package com.pledgerio.app.ui.reports

import com.pledgerio.app.domain.model.DatedAmount
import java.time.LocalDate
import java.time.YearMonth

/** Daily net-worth points that fall inside [month]. */
fun List<DatedAmount>.inMonth(month: YearMonth): List<DatedAmount> {
    val start = month.atDay(1)
    val end = month.atEndOfMonth()
    return filter { point ->
        runCatching { LocalDate.parse(point.date) }
            .getOrNull()
            ?.let { it in start..end }
            ?: false
    }.sortedBy { it.date }
}
