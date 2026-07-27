package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.LoginResponse
import com.pledgerio.app.domain.model.LoginResult
import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.JwtPayload
import com.pledgerio.app.util.LogoutCredential
import com.pledgerio.app.util.PendingMfaSession
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val authenticatedSessionCoordinator = mockk<AuthenticatedSessionCoordinator>(relaxed = true)
    private val pendingMfaSession = mockk<PendingMfaSession>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        mockkObject(JwtPayload)
        repository = AuthRepositoryImpl(
            apiService,
            sessionManager,
            authenticatedSessionCoordinator,
            pendingMfaSession,
            okHttpClient,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(JwtPayload)
    }

    @Test
    fun `logout delegates remote and local lifecycle to coordinator`() = runTest {
        val credential = LogoutCredential("old-token")
        coEvery { apiService.logout("Bearer old-token") } returns Response.success(Unit)
        coEvery { authenticatedSessionCoordinator.logout(any()) } coAnswers {
            firstArg<suspend (LogoutCredential?) -> Unit>().invoke(credential)
        }

        repository.logout()

        verify(exactly = 1) { pendingMfaSession.clear() }
        coVerify(exactly = 1) { authenticatedSessionCoordinator.logout(any()) }
        coVerify(exactly = 1) { apiService.logout("Bearer old-token") }
    }

    @Test
    fun `logout coordinator receives remote failure without repository handling credentials`() = runTest {
        val credential = LogoutCredential("old-token")
        coEvery {
            apiService.logout("Bearer old-token")
        } throws RuntimeException("offline")
        coEvery { authenticatedSessionCoordinator.logout(any()) } coAnswers {
            runCatching {
                firstArg<suspend (LogoutCredential?) -> Unit>().invoke(credential)
            }
            Unit
        }

        repository.logout()

        coVerify(exactly = 1) { authenticatedSessionCoordinator.logout(any()) }
        coVerify(exactly = 0) { sessionManager.clearAuthTokens() }
    }

    @Test
    fun `login activates authenticated session before returning success`() = runTest {
        every { JwtPayload.requiresMfaVerification("new-token") } returns false
        coEvery { apiService.authenticate(any()) } returns Response.success(
            LoginResponse(accessToken = "new-token", expiresIn = 3600, refreshToken = "refresh"),
        )

        val result = repository.login("alice", "secret")

        assertEquals(LoginResult.FullyAuthenticated, (result as Resource.Success).data)
        verify(exactly = 1) { pendingMfaSession.clear() }
        coVerify(exactly = 1) {
            authenticatedSessionCoordinator.activateSession(
                accessToken = "new-token",
                username = "alice",
                refreshToken = "refresh",
                expiresInSeconds = 3600,
            )
        }
        coVerify(exactly = 0) { sessionManager.saveToken(any()) }
    }

    @Test
    fun `login with MFA role stores pending session without activation`() = runTest {
        every { JwtPayload.requiresMfaVerification("pre-token") } returns true
        coEvery { apiService.authenticate(any()) } returns Response.success(
            LoginResponse(accessToken = "pre-token", expiresIn = 120, refreshToken = "refresh"),
        )

        val result = repository.login("alice", "secret")

        assertEquals(LoginResult.MfaRequired, (result as Resource.Success).data)
        verify(exactly = 1) {
            pendingMfaSession.set(
                accessToken = "pre-token",
                refreshToken = "refresh",
                expiresInSeconds = 120,
                username = "alice",
            )
        }
        coVerify(exactly = 0) { authenticatedSessionCoordinator.activateSession(any(), any(), any(), any()) }
    }

    @Test
    fun `verifyTwoFactor activates session and clears pending`() = runTest {
        every { pendingMfaSession.get() } returns PendingMfaSession.Pending(
            accessToken = "pre-token",
            refreshToken = "old-refresh",
            expiresInSeconds = 120,
            username = "alice",
        )
        every { JwtPayload.requiresMfaVerification("full-token") } returns false
        coEvery {
            apiService.verify2Factor("Bearer pre-token", any())
        } returns Response.success(
            LoginResponse(accessToken = "full-token", expiresIn = 3600, refreshToken = "new-refresh"),
        )

        val result = repository.verifyTwoFactor("123456")

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) {
            authenticatedSessionCoordinator.activateSession(
                accessToken = "full-token",
                username = "alice",
                refreshToken = "new-refresh",
                expiresInSeconds = 3600,
            )
        }
        verify(exactly = 1) { pendingMfaSession.clear() }
    }

    @Test
    fun `verifyTwoFactor maps invalid code as error`() = runTest {
        every { pendingMfaSession.get() } returns PendingMfaSession.Pending(
            accessToken = "pre-token",
            refreshToken = null,
            expiresInSeconds = 120,
            username = "alice",
        )
        coEvery {
            apiService.verify2Factor(any(), any())
        } returns Response.error(403, "".toResponseBody(null))

        val result = repository.verifyTwoFactor("000000")

        assertTrue(result is Resource.Error)
        assertEquals("Invalid verification code", (result as Resource.Error).message)
        coVerify(exactly = 0) { authenticatedSessionCoordinator.activateSession(any(), any(), any(), any()) }
    }
}
