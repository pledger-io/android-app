package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.LoginResult
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var loginUseCase: LoginUseCase

    @Before
    fun setup() {
        authRepository = mockk()
        loginUseCase = LoginUseCase(authRepository)
    }

    @Test
    fun `login with empty username returns error`() = runTest {
        val result = loginUseCase("", "password")
        assertTrue(result is Resource.Error)
        assertEquals("Username cannot be empty", (result as Resource.Error).message)
    }

    @Test
    fun `login with empty password returns error`() = runTest {
        val result = loginUseCase("user", "")
        assertTrue(result is Resource.Error)
        assertEquals("Password cannot be empty", (result as Resource.Error).message)
    }

    @Test
    fun `login with valid credentials returns success`() = runTest {
        coEvery { authRepository.login("user", "pass") } returns
            Resource.Success(LoginResult.FullyAuthenticated)

        val result = loginUseCase("user", "pass")
        assertTrue(result is Resource.Success)
        assertEquals(LoginResult.FullyAuthenticated, (result as Resource.Success).data)
    }

    @Test
    fun `login with MFA required returns MfaRequired`() = runTest {
        coEvery { authRepository.login("user", "pass") } returns
            Resource.Success(LoginResult.MfaRequired)

        val result = loginUseCase("user", "pass")
        assertEquals(LoginResult.MfaRequired, (result as Resource.Success).data)
    }

    @Test
    fun `login with invalid credentials returns error`() = runTest {
        coEvery { authRepository.login("user", "wrong") } returns Resource.Error("Login failed: 401")

        val result = loginUseCase("user", "wrong")
        assertTrue(result is Resource.Error)
    }
}
