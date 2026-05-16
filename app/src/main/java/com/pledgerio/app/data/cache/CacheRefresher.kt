package com.pledgerio.app.data.cache

import com.pledgerio.app.data.local.dao.SyncMetadataDao
import com.pledgerio.app.data.local.entity.SyncMetadataEntity
import com.pledgerio.app.di.ApplicationScope
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates stale-while-revalidate refreshes across repositories. Holds a per-key
 * coalescing mutex so that simultaneous callers (e.g. multiple ViewModels collecting the
 * same Flow) trigger at most one network round-trip.
 */
@Singleton
class CacheRefresher @Inject constructor(
    private val syncMetadataDao: SyncMetadataDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val mutexes = mutableMapOf<String, Mutex>()
    private val inFlight = mutableMapOf<String, Job>()

    suspend fun lastSyncedAt(key: String): Long? = syncMetadataDao.getLastSyncedAt(key)

    suspend fun isStale(key: String, ttlMs: Long): Boolean =
        CachePolicy.isStale(lastSyncedAt(key), ttlMs)

    suspend fun markFresh(key: String, at: Long = System.currentTimeMillis()) {
        syncMetadataDao.upsert(SyncMetadataEntity(key, at))
    }

    suspend fun invalidate(key: String) {
        syncMetadataDao.delete(key)
    }

    /**
     * If [key]'s cache is stale (older than [ttlMs] or never synced) launches [block] on the
     * shared [applicationScope]. Callers do not wait for the result. Multiple stale checks
     * for the same key while a refresh is in-flight are coalesced to a single job.
     */
    fun launchIfStale(
        key: String,
        ttlMs: Long,
        block: suspend () -> Resource<*>,
    ) {
        applicationScope.launch {
            if (!isStale(key, ttlMs)) return@launch
            refreshNow(key, block)
        }
    }

    /**
     * Run [block] now (regardless of TTL) and coalesce concurrent callers. On success the
     * key is marked fresh. Returns the [Resource] from [block].
     */
    suspend fun <T> refreshNow(
        key: String,
        block: suspend () -> Resource<T>,
    ): Resource<T> {
        val mutex = synchronized(mutexes) { mutexes.getOrPut(key) { Mutex() } }
        return mutex.withLock {
            val result = block()
            if (result is Resource.Success) markFresh(key)
            result
        }
    }

    /**
     * Fire-and-forget refresh that is coalesced via [inFlight]. Used by mutations that
     * want a background refetch without blocking the UI.
     */
    fun refreshInBackground(key: String, block: suspend () -> Resource<*>) {
        synchronized(inFlight) {
            inFlight[key]?.let { if (it.isActive) return }
            val job = applicationScope.launch {
                try {
                    refreshNow(key, block)
                } finally {
                    synchronized(inFlight) { inFlight.remove(key) }
                }
            }
            inFlight[key] = job
        }
    }
}
