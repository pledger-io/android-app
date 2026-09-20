package com.pledgerio.app.data.cache

import com.pledgerio.app.domain.model.SpendingInsights
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache of detected insights per [YearMonth].
 * Cleared on logout via [com.pledgerio.app.data.local.LocalDataCleaner].
 */
@Singleton
class SpendingInsightsCache @Inject constructor() {

    data class Entry(
        val insights: SpendingInsights,
        val fetchedAtMillis: Long,
    ) {
        fun isFresh(
            month: YearMonth,
            now: YearMonth = YearMonth.now(),
            nowMillis: Long = System.currentTimeMillis(),
        ): Boolean = ReportsCachePolicy.isFresh(fetchedAtMillis, month, now, nowMillis)
    }

    private val mutex = Mutex()
    private val entries = mutableMapOf<YearMonth, Entry>()

    suspend fun get(month: YearMonth): Entry? = mutex.withLock { entries[month] }

    suspend fun put(
        month: YearMonth,
        insights: SpendingInsights,
        fetchedAtMillis: Long = System.currentTimeMillis(),
    ) {
        mutex.withLock { entries[month] = Entry(insights, fetchedAtMillis) }
    }

    suspend fun clearAll() {
        mutex.withLock { entries.clear() }
    }
}
