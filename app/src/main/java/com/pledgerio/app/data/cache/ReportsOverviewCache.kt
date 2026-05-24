package com.pledgerio.app.data.cache

import com.pledgerio.app.domain.model.ReportsOverview
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache of assembled overview reports per [YearMonth].
 * Cleared on logout via [com.pledgerio.app.data.local.LocalDataCleaner].
 */
@Singleton
class ReportsOverviewCache @Inject constructor() {

    data class Entry(
        val overview: ReportsOverview,
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

    suspend fun put(month: YearMonth, overview: ReportsOverview, fetchedAtMillis: Long = System.currentTimeMillis()) {
        mutex.withLock {
            entries[month] = Entry(overview, fetchedAtMillis)
        }
    }

    suspend fun invalidate(month: YearMonth) {
        mutex.withLock { entries.remove(month) }
    }

    suspend fun clearAll() {
        mutex.withLock { entries.clear() }
    }
}
