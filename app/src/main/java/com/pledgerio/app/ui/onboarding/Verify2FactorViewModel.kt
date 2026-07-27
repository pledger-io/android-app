package com.pledgerio.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Verify2FactorUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class Verify2FactorViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(Verify2FactorUiState())
    val uiState: StateFlow<Verify2FactorUiState> = _uiState.asStateFlow()

    init {
        if (!authRepository.hasPendingMfa()) {
            _uiState.update {
                it.copy(error = "No pending verification — sign in again")
            }
        }
    }

    fun onCodeChanged(code: String) {
        val digits = code.filter { it.isDigit() }.take(8)
        _uiState.update { it.copy(code = digits, error = null) }
    }

    fun verify(onSuccess: () -> Unit) {
        val code = _uiState.value.code
        if (code.length < 4) {
            _uiState.update { it.copy(error = "Enter the code from your authenticator app") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.verifyTwoFactor(code)) {
                is Resource.Success -> {
                    currencyRepository.sync()
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun cancel() {
        authRepository.clearPendingMfa()
    }
}
