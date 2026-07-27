package com.pledgerio.app.ui.settings

import com.pledgerio.app.domain.model.AppLocale
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.domain.model.UserProfile
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.UserSessionRepository
import com.pledgerio.app.util.BiometricAuthenticator
import com.pledgerio.app.util.BiometricAvailability
import com.pledgerio.app.util.BiometricLockManager
import com.pledgerio.app.util.BudgetAlertLogic
import com.pledgerio.app.util.DurableLogoutException
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val authRepository = mockk<AuthRepository>()
    private val userSessionRepository = mockk<UserSessionRepository>()
    private val userPreferences = mockk<UserPreferences>()
    private val currencyRepository = mockk<CurrencyRepository>()
    private val biometricAuthenticator = mockk<BiometricAuthenticator>()
    private val biometricLockManager = mockk<BiometricLockManager>(relaxed = true)

    private val budgetAlertsEnabled = MutableStateFlow(BudgetAlertLogic.DEFAULT_ENABLED)
    private val budgetAlertThreshold =
        MutableStateFlow(BudgetAlertLogic.DEFAULT_THRESHOLD_PERCENT)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { userPreferences.displayCurrencyCode } returns MutableStateFlow("EUR")
        every { userPreferences.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        every {
            userPreferences.financeExperienceMode
        } returns MutableStateFlow(FinanceExperienceMode.GUIDED)
        every { userPreferences.appLocale } returns MutableStateFlow(AppLocale.SYSTEM)
        every { userPreferences.budgetAlertsEnabled } returns budgetAlertsEnabled
        every { userPreferences.budgetAlertThresholdPercent } returns budgetAlertThreshold
        coEvery { userPreferences.setBudgetAlertsEnabled(any()) } coAnswers {
            budgetAlertsEnabled.value = firstArg()
        }
        coEvery { userPreferences.setBudgetAlertThresholdPercent(any()) } coAnswers {
            budgetAlertThreshold.value = BudgetAlertLogic.normalizeThresholdPercent(firstArg())
        }
        every {
            biometricAuthenticator.getAvailability()
        } returns BiometricAvailability.NotAvailable
        coEvery { currencyRepository.sync() } returns true
        every { currencyRepository.getCurrencies() } returns flowOf(emptyList())
        coEvery { userSessionRepository.getProfile() } returns Resource.Success(
            UserProfile(mfa = false),
        )
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

    @Test
    fun `toggle budget alerts updates preferences and ui state`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.budgetAlertsEnabled)

        viewModel.setBudgetAlertsEnabled(false)
        advanceUntilIdle()

        coVerify { userPreferences.setBudgetAlertsEnabled(false) }
        assertFalse(viewModel.uiState.value.budgetAlertsEnabled)

        viewModel.setBudgetAlertsEnabled(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.budgetAlertsEnabled)
    }

    @Test
    fun `select budget alert threshold updates preferences and dismisses picker`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openBudgetAlertThresholdPicker()
        assertTrue(viewModel.uiState.value.showBudgetAlertThresholdPicker)

        viewModel.selectBudgetAlertThreshold(90)
        advanceUntilIdle()

        coVerify { userPreferences.setBudgetAlertThresholdPercent(90) }
        assertEquals(90, viewModel.uiState.value.budgetAlertThresholdPercent)
        assertFalse(viewModel.uiState.value.showBudgetAlertThresholdPicker)
    }

    @Test
    fun `threshold picker stays closed when alerts disabled`() = runTest {
        budgetAlertsEnabled.value = false
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openBudgetAlertThresholdPicker()
        assertFalse(viewModel.uiState.value.showBudgetAlertThresholdPicker)
    }

    @Test
    fun `loads MFA status from profile`() = runTest {
        coEvery { userSessionRepository.getProfile() } returns Resource.Success(
            UserProfile(mfa = true),
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.mfaEnabled)
        coVerify(exactly = 1) { userSessionRepository.getProfile() }
    }

    @Test
    fun `keeps MFA unknown when profile load fails`() = runTest {
        coEvery { userSessionRepository.getProfile() } returns Resource.Error("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.mfaEnabled)
    }

    private fun viewModel() = SettingsViewModel(
        sessionManager = sessionManager,
        authRepository = authRepository,
        userSessionRepository = userSessionRepository,
        userPreferences = userPreferences,
        currencyRepository = currencyRepository,
        biometricAuthenticator = biometricAuthenticator,
        biometricLockManager = biometricLockManager,
    )
}
