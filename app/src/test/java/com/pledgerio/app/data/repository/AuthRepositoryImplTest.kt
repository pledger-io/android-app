package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.LocalDataCleaner
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.LoginResponse
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryImplTest {

    private val apiService = mockk<PledgerApiService>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val localDataCleaner = mockk<LocalDataCleaner>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        coEvery { localDataCleaner.clearAllUserData() } just runs
        repository = AuthRepositoryImpl(apiService, sessionManager, localDataCleaner, okHttpClient)
    }

    @Test
    fun `logout clears local caches before auth tokens`() = runTest {
        every { sessionManager.isLoggedIn() } returns true
        coEvery { apiService.logout() } returns Response.success(Unit)

        repository.logout()

        coVerifyOrder {
            localDataCleaner.clearAllUserData()
            sessionManager.clearAuthTokens()
        }
    }

    @Test
    fun `logout clears local caches when server logout fails`() = runTest {
        every { sessionManager.isLoggedIn() } returns true
        coEvery { apiService.logout() } throws RuntimeException("offline")

        repository.logout()

        coVerify { localDataCleaner.clearAllUserData() }
        coVerify { sessionManager.clearAuthTokens() }
    }

    @Test
    fun `login clears local caches before saving new session`() = runTest {
        coEvery { apiService.authenticate(any()) } returns Response.success(
            LoginResponse(accessToken = "new-token", expiresIn = 3600, refreshToken = "refresh"),
        )

        repository.login("alice", "secret")

        coVerifyOrder {
            localDataCleaner.clearAllUserData()
            sessionManager.saveToken("new-token")
        }
    }
}
