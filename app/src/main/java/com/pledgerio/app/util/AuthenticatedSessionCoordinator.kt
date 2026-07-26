package com.pledgerio.app.util

import com.pledgerio.app.data.local.LocalDataCleaner
import com.pledgerio.app.di.ApplicationScope
import com.pledgerio.app.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns transitions between authenticated sessions and their background work.
 *
 * User-driven transitions are serialized. [SessionDataBarrier] prevents a running worker
 * repository step from overlapping cache cleanup or new-session activation; WorkManager
 * cancellation itself is scheduling control and is not relied on as a worker-completion signal.
 * Terminal failures invalidate credentials synchronously, then wait for the barrier
 * asynchronously to avoid deadlocking an interceptor running inside a guarded worker step.
 */
@Singleton
class AuthenticatedSessionCoordinator @Inject constructor(
    private val sessionManager: SessionManager,
    private val localDataCleaner: LocalDataCleaner,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val sessionDataBarrier: SessionDataBarrier,
    private val appLog: AppLog,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val transitionMutex = Mutex()
    private val credentialLock = Any()

    suspend fun activateSession(
        accessToken: String,
        username: String,
        refreshToken: String?,
        expiresInSeconds: Long,
    ): Unit = withContext(ioDispatcher) {
        require(accessToken.isNotBlank()) { "An access token is required" }
        transitionMutex.withLock {
            withContext(NonCancellable) {
                sessionDataBarrier.withSessionTransition {
                    // Fail closed before any cancellation or cleanup operation can fail.
                    synchronized(credentialLock) {
                        sessionManager.clearAuthTokens()
                    }
                    val preparationFailure = collectFailure(
                        first = attempt("Could not cancel prior background work") {
                            syncWorkScheduler.cancelAndAwait()
                        },
                        second = attempt("Could not clear prior cached data") {
                            localDataCleaner.clearAllUserData()
                        },
                    )
                    if (preparationFailure != null) throw preparationFailure

                    val generation = synchronized(credentialLock) {
                        sessionManager.installAuthenticatedSession(
                            accessToken = accessToken,
                            username = username,
                            refreshToken = refreshToken,
                            expiresInSeconds = expiresInSeconds,
                        )
                    }

                    try {
                        syncWorkScheduler.schedule(generation)
                    } catch (error: Exception) {
                        synchronized(credentialLock) {
                            sessionManager.clearAuthTokens()
                        }
                        attempt("Could not cancel work after activation failure") {
                            syncWorkScheduler.cancelAndAwait()
                        }
                        throw error
                    }
                }
            }
        }
    }

    suspend fun logout(remoteLogout: suspend () -> Unit): Unit = withContext(ioDispatcher) {
        transitionMutex.withLock {
            withContext(NonCancellable) {
                sessionDataBarrier.withSessionTransition {
                    attempt("Could not invalidate background work during logout") {
                        synchronized(credentialLock) {
                            sessionManager.invalidateSyncGeneration()
                        }
                    }
                    attempt("Could not cancel background work during logout") {
                        syncWorkScheduler.cancelAndAwait()
                    }
                    attempt("Remote logout failed") {
                        remoteLogout()
                    }
                    attempt("Could not clear credentials during logout") {
                        synchronized(credentialLock) {
                            sessionManager.clearAuthTokens()
                        }
                    }
                    attempt("Could not clear cached data during logout") {
                        localDataCleaner.clearAllUserData()
                    }
                    Unit
                }
            }
        }
    }

    suspend fun switchServer(baseUrl: String): Unit = withContext(ioDispatcher) {
        transitionMutex.withLock {
            withContext(NonCancellable) {
                sessionDataBarrier.withSessionTransition {
                    synchronized(credentialLock) {
                        sessionManager.clearAuthTokens()
                    }
                    val preparationFailure = collectFailure(
                        first = attempt("Could not cancel work while switching server") {
                            syncWorkScheduler.cancelAndAwait()
                        },
                        second = attempt("Could not clear cached data while switching server") {
                            localDataCleaner.clearAllUserData()
                        },
                    )
                    if (preparationFailure != null) throw preparationFailure

                    synchronized(credentialLock) {
                        sessionManager.clearAuthTokensAndSaveBaseUrl(baseUrl)
                    }
                }
            }
        }
    }

    suspend fun reconcileAtStartup(): Unit = withContext(ioDispatcher) {
        try {
            transitionMutex.withLock {
                sessionDataBarrier.withSessionTransition {
                    val generation = synchronized(credentialLock) {
                        if (!sessionManager.isLoggedIn()) {
                            sessionManager.invalidateSyncGeneration()
                            null
                        } else {
                            sessionManager.getSyncGeneration()
                                ?: sessionManager.rotateSyncGeneration()
                        }
                    }
                    if (generation == null) {
                        syncWorkScheduler.cancelAndAwait()
                    } else {
                        try {
                            syncWorkScheduler.schedule(generation)
                        } catch (error: Exception) {
                            // Any retained work must become stale if reconciliation cannot update it.
                            synchronized(credentialLock) {
                                sessionManager.invalidateSyncGeneration()
                            }
                            throw error
                        }
                    }
                }
            }
        } catch (_: Exception) {
            appLog.w(TAG, "Background work reconciliation failed safely")
        }
    }

    /**
     * Handles an unrecoverable 401 without blocking the OkHttp interceptor thread.
     *
     * [expectedScope] prevents an old request from terminating a newer login or server.
     */
    fun terminateSessionAsync(expectedScope: AuthenticatedSessionScope) {
        val invalidated = synchronized(credentialLock) {
            sessionManager.clearAuthTokensIfCurrent(expectedScope)
        }
        if (!invalidated) return

        applicationScope.launch {
            transitionMutex.withLock {
                val stillTerminated = synchronized(credentialLock) {
                    !sessionManager.isLoggedIn() && sessionManager.getSyncGeneration() == null
                }
                if (!stillTerminated) return@withLock

                sessionDataBarrier.withSessionTransition {
                    val stillTerminatedInsideBarrier = synchronized(credentialLock) {
                        !sessionManager.isLoggedIn() &&
                            sessionManager.getSyncGeneration() == null
                    }
                    if (!stillTerminatedInsideBarrier) return@withSessionTransition

                    attempt("Could not cancel work after terminal authentication failure") {
                        syncWorkScheduler.cancelAndAwait()
                    }
                    attempt("Could not clear cache after terminal authentication failure") {
                        localDataCleaner.clearAllUserData()
                    }
                }
            }
        }
    }

    private suspend fun attempt(
        failureMessage: String,
        action: suspend () -> Unit,
    ): Exception? = try {
        action()
        null
    } catch (error: Exception) {
        appLog.w(TAG, failureMessage)
        error
    }

    private fun collectFailure(first: Exception?, second: Exception?): Exception? {
        if (first == null) return second
        if (second != null) first.addSuppressed(second)
        return first
    }

    companion object {
        private const val TAG = "SessionLifecycle"
    }
}
