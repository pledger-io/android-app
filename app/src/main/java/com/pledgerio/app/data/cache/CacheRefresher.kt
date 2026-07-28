package com.pledgerio.app.data.cache

import com.pledgerio.app.data.local.dao.SyncMetadataDao
import com.pledgerio.app.data.local.entity.SyncMetadataEntity
import com.pledgerio.app.di.ApplicationScope
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates stale-while-revalidate refreshes across repositories. Shares each per-key
 * in-flight result so that simultaneous callers (e.g. multiple ViewModels collecting the
 * same Flow) trigger at most one network round-trip.
 */
@Singleton
class CacheRefresher @Inject constructor(
    private val syncMetadataDao: SyncMetadataDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val inFlightRefreshes = mutableMapOf<String, CompletableDeferred<Resource<*>>>()
    private val staleRefreshJobs = mutableMapOf<String, Job>()
    private val backgroundJobs = mutableMapOf<String, Job>()

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
        launchCoalesced(staleRefreshJobs, key) {
            if (!isStale(key, ttlMs)) return@launchCoalesced
            refreshNow(key) {
                // Another caller may have refreshed after the first stale check but before
                // this refresh became the per-key leader.
                if (isStale(key, ttlMs)) block() else Resource.Success(Unit)
            }
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
        val refreshContext = currentCoroutineContext()[RefreshContext]
        if (key in refreshContext?.keys.orEmpty()) {
            // Defensive support for legacy callers that accidentally wrap refreshNow with
            // refreshNow for the same key. The outer refresh remains responsible for marking.
            return block()
        }

        val candidate = CompletableDeferred<Resource<*>>()
        val shared = synchronized(inFlightRefreshes) {
            inFlightRefreshes[key]
                ?.takeUnless { it.isCompleted }
                ?: candidate.also { inFlightRefreshes[key] = it }
        }
        if (shared !== candidate) {
            @Suppress("UNCHECKED_CAST")
            return shared.await() as Resource<T>
        }

        try {
            val result = withContext(
                RefreshContext(refreshContext?.keys.orEmpty() + key),
            ) {
                block()
            }
            if (result is Resource.Success) markFresh(key)
            candidate.complete(result)
            return result
        } catch (throwable: Throwable) {
            candidate.completeExceptionally(throwable)
            throw throwable
        } finally {
            synchronized(inFlightRefreshes) {
                if (inFlightRefreshes[key] === candidate) {
                    inFlightRefreshes.remove(key)
                }
            }
        }
    }

    /**
     * Fire-and-forget refresh that is coalesced with other background jobs for [key].
     * Used by mutations that
     * want a background refetch without blocking the UI.
     */
    fun refreshInBackground(key: String, block: suspend () -> Resource<*>) {
        launchCoalesced(backgroundJobs, key) {
            refreshNow(key, block)
        }
    }

    private fun launchCoalesced(
        jobs: MutableMap<String, Job>,
        key: String,
        block: suspend () -> Unit,
    ) {
        synchronized(jobs) {
            jobs[key]?.let { if (it.isActive) return }
            val job = applicationScope.launch {
                try {
                    block()
                } finally {
                    synchronized(jobs) {
                        if (jobs[key] === coroutineContext[Job]) {
                            jobs.remove(key)
                        }
                    }
                }
            }
            jobs[key] = job
        }
    }

    private class RefreshContext(
        val keys: Set<String>,
    ) : AbstractCoroutineContextElement(RefreshContext) {
        companion object Key : CoroutineContext.Key<RefreshContext>
    }
}
