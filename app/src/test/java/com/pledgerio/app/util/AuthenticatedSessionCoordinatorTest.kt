package com.pledgerio.app.util

import com.pledgerio.app.data.local.LocalDataCleaner
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatedSessionCoordinatorTest {

    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val localDataCleaner = mockk<LocalDataCleaner>(relaxed = true)
    private val scheduler = mockk<SyncWorkScheduler>(relaxed = true)

    @Test
    fun `successful activation cancels and clears old session before scheduling new generation`() =
        runTest {
            every { sessionManager.rotateSyncGeneration() } returns "opaque-generation"
            val coordinator = coordinator()

            coordinator.activateSession(
                accessToken = "access",
                username = "alice",
                refreshToken = "refresh",
                expiresInSeconds = 3600,
            )

            coVerifyOrder {
                sessionManager.invalidateSyncGeneration()
                scheduler.cancelAndAwait()
                localDataCleaner.clearAllUserData()
                sessionManager.clearAuthTokens()
                sessionManager.saveToken("access")
                sessionManager.saveUsername("alice")
                sessionManager.saveRefreshToken("refresh")
                sessionManager.saveTokenExpiry(3600)
                sessionManager.rotateSyncGeneration()
                scheduler.schedule("opaque-generation")
            }
            coVerify(exactly = 1) { scheduler.schedule(any()) }
        }

    @Test
    fun `logout invalidates and awaits cancellation before remote logout and local cleanup`() =
        runTest {
            val events = mutableListOf<String>()
            every { sessionManager.invalidateSyncGeneration() } answers {
                events += "invalidate"
            }
            coEvery { scheduler.cancelAndAwait() } coAnswers {
                events += "cancel"
            }
            coEvery { localDataCleaner.clearAllUserData() } coAnswers {
                events += "clean"
            }
            every { sessionManager.clearAuthTokens() } answers {
                events += "clear-auth"
            }
            val coordinator = coordinator()

            coordinator.logout {
                events += "remote"
                error("server unavailable")
            }

            assertEquals(
                listOf("invalidate", "cancel", "remote", "clean", "clear-auth"),
                events,
            )
        }

    @Test
    fun `server switch cancels old work before clearing credentials and changing base url`() =
        runTest {
            every { sessionManager.rotateSyncGeneration() } returns "unused"
            val coordinator = coordinator()

            coordinator.switchServer("https://new.example")

            coVerifyOrder {
                sessionManager.invalidateSyncGeneration()
                scheduler.cancelAndAwait()
                localDataCleaner.clearAllUserData()
                sessionManager.clearAuthTokens()
                sessionManager.saveBaseUrl("https://new.example")
            }
            coVerify(exactly = 0) { scheduler.schedule(any()) }
        }

    @Test
    fun `startup schedules existing authenticated generation exactly once`() = runTest {
        every { sessionManager.isLoggedIn() } returns true
        every { sessionManager.getSyncGeneration() } returns "existing-generation"
        val coordinator = coordinator()

        coordinator.reconcileAtStartup()

        coVerify(exactly = 1) { scheduler.schedule("existing-generation") }
        coVerify(exactly = 0) { scheduler.cancelAndAwait() }
        verify(exactly = 0) { sessionManager.rotateSyncGeneration() }
    }

    @Test
    fun `startup creates opaque generation for migrated authenticated session`() = runTest {
        every { sessionManager.isLoggedIn() } returns true
        every { sessionManager.getSyncGeneration() } returns null
        every { sessionManager.rotateSyncGeneration() } returns "migrated-generation"
        val coordinator = coordinator()

        coordinator.reconcileAtStartup()

        verify(exactly = 1) { sessionManager.rotateSyncGeneration() }
        coVerify(exactly = 1) { scheduler.schedule("migrated-generation") }
    }

    @Test
    fun `startup cancels work and invalidates generation when logged out`() = runTest {
        every { sessionManager.isLoggedIn() } returns false
        val coordinator = coordinator()

        coordinator.reconcileAtStartup()

        verify(exactly = 1) { sessionManager.invalidateSyncGeneration() }
        coVerify(exactly = 1) { scheduler.cancelAndAwait() }
        coVerify(exactly = 0) { scheduler.schedule(any()) }
    }

    @Test
    fun `terminal failure invalidates synchronously then cancels and cleans asynchronously`() =
        runTest {
            every { sessionManager.getToken() } returns "failed-token"
            every { sessionManager.clearAuthTokens() } just Runs
            every { sessionManager.isLoggedIn() } returns false
            every { sessionManager.getSyncGeneration() } returns null
            val coordinator = coordinator()

            coordinator.terminateSessionAsync("failed-token")

            verify(exactly = 1) { sessionManager.clearAuthTokens() }
            coVerify(exactly = 0) { scheduler.cancelAndAwait() }

            advanceUntilIdle()

            coVerify(exactly = 1) { scheduler.cancelAndAwait() }
            coVerify(exactly = 1) { localDataCleaner.clearAllUserData() }
        }

    @Test
    fun `terminal cleanup cannot cancel or clear a newer session`() = runTest {
        every { sessionManager.getToken() } returns "failed-token"
        every { sessionManager.clearAuthTokens() } just Runs
        every { sessionManager.isLoggedIn() } returns true
        every { sessionManager.getSyncGeneration() } returns "new-generation"
        val coordinator = coordinator()

        coordinator.terminateSessionAsync("failed-token")
        advanceUntilIdle()

        coVerify(exactly = 0) { scheduler.cancelAndAwait() }
        coVerify(exactly = 0) { localDataCleaner.clearAllUserData() }
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator() =
        AuthenticatedSessionCoordinator(
            sessionManager = sessionManager,
            localDataCleaner = localDataCleaner,
            syncWorkScheduler = scheduler,
            applicationScope = this,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
}
