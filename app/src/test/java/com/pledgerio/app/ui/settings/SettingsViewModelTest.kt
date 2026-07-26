package com.pledgerio.app.ui.settings

import com.pledgerio.app.domain.model.AppLocale
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.util.BiometricAuthenticator
import com.pledgerio.app.util.BiometricAvailability
import com.pledgerio.app.util.BiometricLockManager
import com.pledgerio.app.util.DurableLogoutException
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class SettingsViewModelTest {

    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val authRepository = mockk<AuthRepository>()
    private val userPreferences = mockk<UserPreferences>()
    private val currencyRepository = mockk<CurrencyRepository>()
    private val biometricAuthenticator = mockk<BiometricAuthenticator>()
    private val biometricLockManager = mockk<BiometricLockManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { userPreferences.displayCurrencyCode } returns MutableStateFlow("EUR")
        every { userPreferences.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        every {
            userPreferences.financeExperienceMode
        } returns MutableStateFlow(FinanceExperienceMode.GUIDED)
        every { userPreferences.appLocale } returns MutableStateFlow(AppLocale.SYSTEM)
        every {
            biometricAuthenticator.getAvailability()
        } returns BiometricAvailability.NotAvailable
        coEvery { currencyRepository.sync() } returns true
        every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `durable logout failure exposes error and does not invoke navigation callback`() =
        runTest {
            coEvery {
                authRepository.logout()
            } throws DurableLogoutException(IllegalStateException("disk failure"))
            val viewModel = viewModel()
            var navigated = false

            viewModel.logout {
                navigated = true
            }
            advanceUntilIdle()

            assertFalse(navigated)
            assertFalse(viewModel.uiState.value.isLoggingOut)
            assertTrue(viewModel.uiState.value.logoutFailed)
        }

    private fun viewModel() = SettingsViewModel(
        sessionManager = sessionManager,
        authRepository = authRepository,
        userPreferences = userPreferences,
        currencyRepository = currencyRepository,
        biometricAuthenticator = biometricAuthenticator,
        biometricLockManager = biometricLockManager,
    )
}
