package com.pledgerio.app.util

import com.pledgerio.app.data.local.LocalDataCleaner
import com.pledgerio.app.di.ApplicationScope
import com.pledgerio.app.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns transitions between authenticated sessions and their background work.
 *
 * User-driven transitions are serialized and await WorkManager cancellation before cached data
 * or credentials are replaced. Terminal authentication failures originate inside OkHttp and
 * therefore invalidate credentials synchronously, then finish cancellation asynchronously to
 * avoid waiting for a worker from the worker's own network interceptor.
 */
@Singleton
class AuthenticatedSessionCoordinator @Inject constructor(
    private val sessionManager: SessionManager,
    private val localDataCleaner: LocalDataCleaner,
    private val syncWorkScheduler: SyncWorkScheduler,
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
    ) = withContext(ioDispatcher) {
        require(accessToken.isNotBlank()) { "An access token is required" }
        transitionMutex.withLock {
            invalidateGeneration()
            syncWorkScheduler.cancelAndAwait()
            localDataCleaner.clearAllUserData()

            val generation = synchronized(credentialLock) {
                sessionManager.clearAuthTokens()
                sessionManager.saveToken(accessToken)
                sessionManager.saveUsername(username)
                refreshToken?.let(sessionManager::saveRefreshToken)
                if (expiresInSeconds > 0) {
                    sessionManager.saveTokenExpiry(expiresInSeconds)
                }
                sessionManager.rotateSyncGeneration()
            }

            try {
                syncWorkScheduler.schedule(generation)
            } catch (error: Exception) {
                synchronized(credentialLock) {
                    sessionManager.clearAuthTokens()
                }
                throw error
            }
        }
    }

    suspend fun logout(remoteLogout: suspend () -> Unit) = withContext(ioDispatcher) {
        transitionMutex.withLock {
            invalidateGeneration()
            syncWorkScheduler.cancelAndAwait()
            try {
                remoteLogout()
            } catch (_: Exception) {
                // Local logout must complete even if the server is unavailable.
            } finally {
                localDataCleaner.clearAllUserData()
                synchronized(credentialLock) {
                    sessionManager.clearAuthTokens()
                }
            }
        }
    }

    suspend fun switchServer(baseUrl: String) = withContext(ioDispatcher) {
        transitionMutex.withLock {
            invalidateGeneration()
            syncWorkScheduler.cancelAndAwait()
            localDataCleaner.clearAllUserData()
            synchronized(credentialLock) {
                sessionManager.clearAuthTokens()
                sessionManager.saveBaseUrl(baseUrl)
            }
        }
    }

    suspend fun reconcileAtStartup() = withContext(ioDispatcher) {
        transitionMutex.withLock {
            val generation = synchronized(credentialLock) {
                if (!sessionManager.isLoggedIn()) {
                    sessionManager.invalidateSyncGeneration()
                    null
                } else {
                    sessionManager.getSyncGeneration() ?: sessionManager.rotateSyncGeneration()
                }
            }
            if (generation == null) {
                syncWorkScheduler.cancelAndAwait()
            } else {
                syncWorkScheduler.schedule(generation)
            }
        }
    }

    /**
     * Handles an unrecoverable 401 without blocking the OkHttp interceptor thread.
     *
     * [expectedAccessToken] prevents an old request from terminating a newer login.
     */
    fun terminateSessionAsync(expectedAccessToken: String) {
        val invalidated = synchronized(credentialLock) {
            if (sessionManager.getToken() != expectedAccessToken) {
                false
            } else {
                sessionManager.clearAuthTokens()
                true
            }
        }
        if (!invalidated) return

        applicationScope.launch {
            transitionMutex.withLock {
                val stillTerminated = synchronized(credentialLock) {
                    !sessionManager.isLoggedIn() && sessionManager.getSyncGeneration() == null
                }
                if (!stillTerminated) return@withLock

                syncWorkScheduler.cancelAndAwait()
                localDataCleaner.clearAllUserData()
            }
        }
    }

    private fun invalidateGeneration() {
        synchronized(credentialLock) {
            sessionManager.invalidateSyncGeneration()
        }
    }
}
