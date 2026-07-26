package com.pledgerio.app.util

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes authenticated cache writes with session transitions.
 *
 * WorkManager cancellation does not guarantee that a running worker has stopped. Every worker
 * repository step therefore holds this barrier, while invalidation, cache cleanup, and new
 * credential activation hold the same barrier for the complete transition.
 */
@Singleton
class SessionDataBarrier @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withWorkerStep(action: suspend () -> T): T = mutex.withLock {
        action()
    }

    suspend fun <T> withSessionTransition(action: suspend () -> T): T = mutex.withLock {
        action()
    }
}
