package com.pledgerio.app.ui

import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.util.BiometricAuthenticator
import com.pledgerio.app.util.BiometricLockManager
import com.pledgerio.app.util.DurableLogoutException
import com.pledgerio.app.util.NetworkMonitor
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val authRepository = mockk<AuthRepository>()
    private val biometricLockManager = mockk<BiometricLockManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `biometric sign out failure keeps lock active and does not navigate`() = runTest {
        coEvery {
            authRepository.logout()
        } throws DurableLogoutException(IllegalStateException("disk failure"))
        val userPreferences = mockk<UserPreferences>()
        every { userPreferences.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        val networkMonitor = mockk<NetworkMonitor>()
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        val viewModel = AppViewModel(
            sessionManager = mockk<SessionManager>(relaxed = true),
            biometricLockManager = biometricLockManager,
            biometricAuthenticator = mockk<BiometricAuthenticator>(relaxed = true),
            authRepository = authRepository,
            userPreferences = userPreferences,
            networkMonitor = networkMonitor,
        )
        var navigated = false

        viewModel.signOutFromBiometricLock {
            navigated = true
        }
        advanceUntilIdle()

        assertFalse(navigated)
        assertTrue(viewModel.biometricSignOutFailed.value)
        verify(exactly = 0) { biometricLockManager.onBiometricDisabled() }
    }
}
