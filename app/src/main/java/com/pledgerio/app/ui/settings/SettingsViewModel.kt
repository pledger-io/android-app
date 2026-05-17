package com.pledgerio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.AppLocale
import com.pledgerio.app.domain.model.Currency
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.util.LocaleManager
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.util.BiometricAuthenticator
import com.pledgerio.app.util.BiometricAvailability
import com.pledgerio.app.util.BiometricLockManager
import com.pledgerio.app.util.CurrencyProvider
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.UserPreferences
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String? = null,
    val username: String? = null,
    val biometricEnabled: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.NotAvailable,
    val displayCurrencyCode: String = "EUR",
    val displayCurrencyLabel: String = "EUR",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val financeExperienceMode: FinanceExperienceMode = FinanceExperienceMode.GUIDED,
    val appLocale: AppLocale = AppLocale.SYSTEM,
    val currencies: List<Currency> = emptyList(),
    val showCurrencyPicker: Boolean = false,
    val showThemePicker: Boolean = false,
    val showExperiencePicker: Boolean = false,
    val showLanguagePicker: Boolean = false,
    val isLoggingOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    private val currencyRepository: CurrencyRepository,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val biometricLockManager: BiometricLockManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            serverUrl = sessionManager.getBaseUrl(),
            username = sessionManager.getUsername(),
            biometricEnabled = sessionManager.isBiometricEnabled(),
            biometricAvailability = biometricAuthenticator.getAvailability(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.displayCurrencyCode.collect { code ->
                _uiState.update {
                    it.copy(
                        displayCurrencyCode = code,
                        displayCurrencyLabel = currencyLabel(code),
                    )
                }
            }
        }
        viewModelScope.launch {
            userPreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            userPreferences.financeExperienceMode.collect { mode ->
                _uiState.update { it.copy(financeExperienceMode = mode) }
            }
        }
        viewModelScope.launch {
            userPreferences.appLocale.collect { locale ->
                _uiState.update { it.copy(appLocale = locale) }
            }
        }
        loadCurrencies()
    }

    fun toggleBiometric(enabled: Boolean) {
        if (enabled) return
        sessionManager.setBiometricEnabled(false)
        biometricLockManager.onBiometricDisabled()
        _uiState.update { it.copy(biometricEnabled = false) }
    }

    fun enableBiometric(
        activity: FragmentActivity,
        enableTitle: String,
        enableSubtitle: String,
        cancelLabel: String,
        onError: (String) -> Unit,
    ) {
        if (!_uiState.value.biometricAvailability.canEnable) return
        biometricAuthenticator.authenticate(
            activity = activity,
            title = enableTitle,
            subtitle = enableSubtitle,
            negativeButtonText = cancelLabel,
            onSuccess = {
                sessionManager.setBiometricEnabled(true)
                biometricLockManager.onUnlocked()
                _uiState.update { it.copy(biometricEnabled = true) }
            },
            onError = onError,
        )
    }

    fun openCurrencyPicker() {
        _uiState.update { it.copy(showCurrencyPicker = true) }
    }

    fun dismissCurrencyPicker() {
        _uiState.update { it.copy(showCurrencyPicker = false) }
    }

    fun openThemePicker() {
        _uiState.update { it.copy(showThemePicker = true) }
    }

    fun dismissThemePicker() {
        _uiState.update { it.copy(showThemePicker = false) }
    }

    fun openExperiencePicker() {
        _uiState.update { it.copy(showExperiencePicker = true) }
    }

    fun dismissExperiencePicker() {
        _uiState.update { it.copy(showExperiencePicker = false) }
    }

    fun openLanguagePicker() {
        _uiState.update { it.copy(showLanguagePicker = true) }
    }

    fun dismissLanguagePicker() {
        _uiState.update { it.copy(showLanguagePicker = false) }
    }

    fun selectAppLocale(locale: AppLocale, onLocaleApplied: () -> Unit) {
        viewModelScope.launch {
            userPreferences.setAppLocale(locale)
            withContext(Dispatchers.Main) {
                LocaleManager.apply(locale)
                dismissLanguagePicker()
                onLocaleApplied()
            }
        }
    }

    fun selectCurrency(code: String) {
        viewModelScope.launch {
            userPreferences.setDisplayCurrency(code)
            dismissCurrencyPicker()
        }
    }

    fun selectTheme(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
            dismissThemePicker()
        }
    }

    fun selectFinanceExperienceMode(mode: FinanceExperienceMode) {
        viewModelScope.launch {
            userPreferences.setFinanceExperienceMode(mode)
            dismissExperiencePicker()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            authRepository.logout()
            _uiState.update { it.copy(isLoggingOut = false) }
            onLoggedOut()
        }
    }

    private fun loadCurrencies() {
        viewModelScope.launch {
            currencyRepository.sync()
            val currencies = currencyRepository.getCurrencies().first()
            _uiState.update { state ->
                state.copy(
                    currencies = currencies,
                    displayCurrencyLabel = currencyLabel(state.displayCurrencyCode, currencies),
                )
            }
        }
    }

    private fun currencyLabel(
        code: String,
        currencies: List<Currency> = _uiState.value.currencies,
    ): String {
        val currency = currencies.find { it.code == code }
            ?: CurrencyProvider.getInstance()?.get(code)
        return if (currency != null) {
            "${currency.code} — ${currency.name} (${currency.symbol})"
        } else {
            code
        }
    }
}
