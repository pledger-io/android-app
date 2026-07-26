package com.pledgerio.app.data.repository

import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.LoginResponse
import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.LogoutCredential
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val authenticatedSessionCoordinator = mockk<AuthenticatedSessionCoordinator>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        repository = AuthRepositoryImpl(
            apiService,
            sessionManager,
            authenticatedSessionCoordinator,
            okHttpClient,
        )
    }

    @Test
    fun `logout delegates remote and local lifecycle to coordinator`() = runTest {
        val credential = LogoutCredential("old-token")
        coEvery { apiService.logout("Bearer old-token") } returns Response.success(Unit)
        coEvery { authenticatedSessionCoordinator.logout(any()) } coAnswers {
            firstArg<suspend (LogoutCredential?) -> Unit>().invoke(credential)
        }

        repository.logout()

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
        coEvery { apiService.authenticate(any()) } returns Response.success(
            LoginResponse(accessToken = "new-token", expiresIn = 3600, refreshToken = "refresh"),
        )

        repository.login("alice", "secret")

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
}
