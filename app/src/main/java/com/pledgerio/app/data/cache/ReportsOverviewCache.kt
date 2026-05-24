package com.pledgerio.app.data.cache

import com.pledgerio.app.domain.model.ReportsOverview
import com.pledgerio.app.domain.repository.ReportsOverviewStore
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
class ReportsOverviewCache @Inject constructor() : ReportsOverviewStore {

    data class Entry(
        override val overview: ReportsOverview,
        override val fetchedAtMillis: Long,
    ) : ReportsOverviewStore.Entry {
        override fun isFresh(
            month: YearMonth,
            now: YearMonth,
            nowMillis: Long,
        ): Boolean = ReportsCachePolicy.isFresh(fetchedAtMillis, month, now, nowMillis)
    }

    private val mutex = Mutex()
    private val entries = mutableMapOf<YearMonth, Entry>()

    override suspend fun get(month: YearMonth): Entry? = mutex.withLock { entries[month] }

    override suspend fun put(
        month: YearMonth,
        overview: ReportsOverview,
        fetchedAtMillis: Long,
    ) {
        mutex.withLock {
            entries[month] = Entry(overview, fetchedAtMillis)
        }
    }

    override suspend fun invalidate(month: YearMonth) {
        mutex.withLock { entries.remove(month) }
    }

    override suspend fun clearAll() {
        mutex.withLock { entries.clear() }
    }
}
