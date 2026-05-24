package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.ReportsOverview
import java.time.YearMonth

interface ReportsOverviewStore {

    interface Entry {
        val overview: ReportsOverview
        val fetchedAtMillis: Long

        fun isFresh(
            month: YearMonth,
            now: YearMonth = YearMonth.now(),
            nowMillis: Long = System.currentTimeMillis(),
        ): Boolean
    }

    suspend fun get(month: YearMonth): Entry?

    suspend fun put(
        month: YearMonth,
        overview: ReportsOverview,
        fetchedAtMillis: Long = System.currentTimeMillis(),
    )

    suspend fun invalidate(month: YearMonth)

    suspend fun clearAll()
}
