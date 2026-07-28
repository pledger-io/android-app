package com.pledgerio.app.data.cache

import com.pledgerio.app.util.FakeSyncMetadataDao
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheRefresherTest {

    @Test
    fun `same-key nested background refresh completes without deadlock`() = runTest {
        val metadata = FakeSyncMetadataDao()
        val refresher = CacheRefresher(metadata, backgroundScope)
        val completed = CompletableDeferred<Resource<String>>()

        refresher.launchIfStale(KEY, ttlMs = 1_000) {
            refresher.refreshNow(KEY) {
                Resource.Success("refreshed")
            }.also { completed.complete(it) }
        }

        val result = withTimeout(100) { completed.await() }

        assertEquals("refreshed", (result as Resource.Success).data)
        assertNotNull(metadata.getLastSyncedAt(KEY))
    }

    @Test
    fun `same-key forced background refresh completes without deadlock`() = runTest {
        val metadata = FakeSyncMetadataDao()
        val refresher = CacheRefresher(metadata, backgroundScope)
        val completed = CompletableDeferred<Resource<String>>()

        refresher.refreshInBackground(KEY) {
            refresher.refreshNow(KEY) {
                Resource.Success("refreshed")
            }.also { completed.complete(it) }
        }

        val result = withTimeout(100) { completed.await() }

        assertEquals("refreshed", (result as Resource.Success).data)
        assertNotNull(metadata.getLastSyncedAt(KEY))
    }

    @Test
    fun `concurrent refreshNow callers share one execution`() = runTest {
        val refresher = CacheRefresher(FakeSyncMetadataDao(), backgroundScope)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0

        val first = async {
            refresher.refreshNow(KEY) {
                executions++
                started.complete(Unit)
                release.await()
                Resource.Success("first")
            }
        }
        started.await()
        val second = async {
            refresher.refreshNow(KEY) {
                executions++
                Resource.Success("second")
            }
        }
        runCurrent()

        assertEquals(1, executions)
        release.complete(Unit)
        assertEquals("first", (first.await() as Resource.Success).data)
        assertEquals("first", (second.await() as Resource.Success).data)
        assertEquals(1, executions)
    }

    @Test
    fun `concurrent stale launches perform one refresh`() = runTest {
        val metadata = FakeSyncMetadataDao()
        val refresher = CacheRefresher(metadata, backgroundScope)
        val release = CompletableDeferred<Unit>()
        var executions = 0

        repeat(10) {
            refresher.launchIfStale(KEY, ttlMs = 1_000) {
                executions++
                release.await()
                Resource.Success(Unit)
            }
        }
        runCurrent()

        assertEquals(1, executions)
        release.complete(Unit)
        runCurrent()
        assertEquals(1, executions)
        assertNotNull(metadata.getLastSyncedAt(KEY))
    }

    @Test
    fun `success marks fresh and failure leaves key stale`() = runTest {
        val metadata = FakeSyncMetadataDao()
        val refresher = CacheRefresher(metadata, backgroundScope)

        val success = refresher.refreshNow("success") { Resource.Success(Unit) }
        val failure = refresher.refreshNow("failure") { Resource.Error("network error") }

        assertTrue(success is Resource.Success)
        assertTrue(failure is Resource.Error)
        assertNotNull(metadata.getLastSyncedAt("success"))
        assertNull(metadata.getLastSyncedAt("failure"))
    }

    private companion object {
        const val KEY = "test-key"
    }
}
