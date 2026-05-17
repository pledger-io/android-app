package com.pledgerio.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.repository.AuthRepository
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerSetupUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val isValidated: Boolean = false,
    val error: String? = null,
    val changeServerMode: Boolean = false,
)

@HiltViewModel
class ServerSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val changeServerMode: Boolean = savedStateHandle.get<Boolean>("changeServer") ?: false

    private val _uiState = MutableStateFlow(
        ServerSetupUiState(
            serverUrl = sessionManager.getBaseUrl() ?: "",
            changeServerMode = changeServerMode,
        ),
    )
    val uiState: StateFlow<ServerSetupUiState> = _uiState.asStateFlow()

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null, isValidated = false) }
    }

    fun validateServer(onSuccess: () -> Unit) {
        val url = _uiState.value.serverUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a server URL") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = if (changeServerMode) {
                authRepository.changeServerUrl(url)
            } else {
                authRepository.validateServer(url)
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, isValidated = true) }
                    onSuccess()
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
}
