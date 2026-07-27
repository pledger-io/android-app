package com.pledgerio.app.ui.settings

import com.pledgerio.app.domain.repository.UserSessionRepository
import com.pledgerio.app.domain.model.UserProfile
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MfaSetupViewModelTest {

    private val userSessionRepository = mockk<UserSessionRepository>()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads enroll QR when MFA disabled`() = runTest {
        coEvery { userSessionRepository.getProfile() } returns Resource.Success(UserProfile(mfa = false))
        coEvery { userSessionRepository.get2FactorQr() } returns Resource.Success(byteArrayOf(9, 8, 7))

        val viewModel = MfaSetupViewModel(userSessionRepository)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.mfaEnabled)
        assertTrue(viewModel.uiState.value.qrPng.contentEquals(byteArrayOf(9, 8, 7)))
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `enable success marks MFA enabled`() = runTest {
        coEvery { userSessionRepository.getProfile() } returns Resource.Success(UserProfile(mfa = false))
        coEvery { userSessionRepository.get2FactorQr() } returns Resource.Success(byteArrayOf(1))
        coEvery { userSessionRepository.enableMfa("123456") } returns Resource.Success(Unit)

        val viewModel = MfaSetupViewModel(userSessionRepository)
        advanceUntilIdle()
        viewModel.onCodeChanged("123456")
        viewModel.enable()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.mfaEnabled)
        assertNull(viewModel.uiState.value.qrPng)
        assertEquals("enabled", viewModel.uiState.value.completedMessage)
        coVerify(exactly = 1) { userSessionRepository.enableMfa("123456") }
    }

    @Test
    fun `confirm disable reloads QR`() = runTest {
        coEvery { userSessionRepository.getProfile() } returns Resource.Success(UserProfile(mfa = true))
        coEvery { userSessionRepository.disableMfa() } returns Resource.Success(Unit)
        coEvery { userSessionRepository.get2FactorQr() } returns Resource.Success(byteArrayOf(2, 2))

        val viewModel = MfaSetupViewModel(userSessionRepository)
        advanceUntilIdle()
        viewModel.confirmDisable()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.mfaEnabled)
        assertTrue(viewModel.uiState.value.qrPng.contentEquals(byteArrayOf(2, 2)))
        assertEquals("disabled", viewModel.uiState.value.completedMessage)
    }
}
