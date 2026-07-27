package com.pledgerio.app.ui.onboarding

import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.util.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
class Verify2FactorViewModelTest {

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val currencyRepository = mockk<CurrencyRepository>(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepository.hasPendingMfa() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `verify success syncs currency and invokes callback`() = runTest {
        coEvery { authRepository.verifyTwoFactor("123456") } returns Resource.Success(Unit)
        coEvery { currencyRepository.sync() } returns true

        val viewModel = Verify2FactorViewModel(authRepository, currencyRepository)
        var success = false
        viewModel.onCodeChanged("123456")
        viewModel.verify { success = true }
        advanceUntilIdle()

        assertTrue(success)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
        coVerify(exactly = 1) { currencyRepository.sync() }
    }

    @Test
    fun `verify error surfaces message`() = runTest {
        coEvery { authRepository.verifyTwoFactor("000000") } returns
            Resource.Error("Invalid verification code")

        val viewModel = Verify2FactorViewModel(authRepository, currencyRepository)
        viewModel.onCodeChanged("000000")
        viewModel.verify { }
        advanceUntilIdle()

        assertEquals("Invalid verification code", viewModel.uiState.value.error)
    }

    @Test
    fun `cancel clears pending MFA`() {
        val viewModel = Verify2FactorViewModel(authRepository, currencyRepository)
        viewModel.cancel()
        verify(exactly = 1) { authRepository.clearPendingMfa() }
    }
}
