package com.pledgerio.app.ui.settings

import androidx.lifecycle.ViewModel
import com.pledgerio.app.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String? = null,
    val username: String? = null,
    val biometricEnabled: Boolean = false,
    val currency: String = "EUR",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            serverUrl = sessionManager.getBaseUrl(),
            username = sessionManager.getUsername(),
            biometricEnabled = sessionManager.isBiometricEnabled(),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleBiometric(enabled: Boolean) {
        sessionManager.setBiometricEnabled(enabled)
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun logout() {
        sessionManager.clearSession()
    }
}
