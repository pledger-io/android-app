package com.pledgerio.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.LoginResult
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.usecase.LoginUseCase
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val currencyRepository: CurrencyRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(username: String) {
        _uiState.update { it.copy(username = username, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login(
        onSuccess: () -> Unit,
        onMfaRequired: () -> Unit,
    ) {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = loginUseCase(state.username, state.password)) {
                is Resource.Success -> when (result.data) {
                    LoginResult.FullyAuthenticated -> {
                        currencyRepository.sync()
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    }
                    LoginResult.MfaRequired -> {
                        _uiState.update { it.copy(isLoading = false, password = "") }
                        onMfaRequired()
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun cancelPendingMfa() {
        authRepository.clearPendingMfa()
    }
}
