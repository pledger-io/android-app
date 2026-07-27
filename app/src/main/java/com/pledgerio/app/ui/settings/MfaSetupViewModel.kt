package com.pledgerio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.repository.UserSessionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MfaSetupUiState(
    val isLoading: Boolean = true,
    val mfaEnabled: Boolean? = null,
    val qrPng: ByteArray? = null,
    val code: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val showDisableConfirm: Boolean = false,
    val completedMessage: String? = null,
)

@HiltViewModel
class MfaSetupViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MfaSetupUiState())
    val uiState: StateFlow<MfaSetupUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, completedMessage = null)
            }
            when (val profile = userSessionRepository.getProfile()) {
                is Resource.Success -> {
                    val enabled = profile.data.mfa
                    if (enabled) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mfaEnabled = true,
                                qrPng = null,
                                code = "",
                                error = null,
                            )
                        }
                    } else {
                        loadQr(enabled = false)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            mfaEnabled = null,
                            error = profile.message,
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private suspend fun loadQr(enabled: Boolean) {
        when (val qr = userSessionRepository.get2FactorQr()) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        mfaEnabled = enabled,
                        qrPng = qr.data,
                        error = null,
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        mfaEnabled = enabled,
                        qrPng = null,
                        error = qr.message,
                    )
                }
            }
            is Resource.Loading -> Unit
        }
    }

    fun onCodeChanged(code: String) {
        val digits = code.filter { it.isDigit() }.take(8)
        _uiState.update { it.copy(code = digits, error = null) }
    }

    fun enable() {
        val code = _uiState.value.code
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            when (val result = userSessionRepository.enableMfa(code)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            mfaEnabled = true,
                            qrPng = null,
                            code = "",
                            completedMessage = "enabled",
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = result.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun requestDisable() {
        _uiState.update { it.copy(showDisableConfirm = true) }
    }

    fun dismissDisableConfirm() {
        _uiState.update { it.copy(showDisableConfirm = false) }
    }

    fun confirmDisable() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(showDisableConfirm = false, isSaving = true, error = null)
            }
            when (val result = userSessionRepository.disableMfa()) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            mfaEnabled = false,
                            completedMessage = "disabled",
                        )
                    }
                    loadQr(enabled = false)
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, error = result.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun consumeCompletedMessage() {
        _uiState.update { it.copy(completedMessage = null) }
    }
}
