package com.pledgerio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.ApiSession
import com.pledgerio.app.domain.repository.UserSessionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CreateSessionFormState(
    val description: String = "",
    val expires: LocalDate = LocalDate.now().plusYears(1),
    val descriptionError: Boolean = false,
    val serverError: String? = null,
    val showDatePicker: Boolean = false,
)

data class ApiSessionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val sessions: List<ApiSession> = emptyList(),
    val error: String? = null,
    val createForm: CreateSessionFormState? = null,
    val createdToken: String? = null,
    val pendingRevoke: ApiSession? = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class ApiSessionsViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiSessionsUiState())
    val uiState: StateFlow<ApiSessionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hadSessions = _uiState.value.sessions.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !hadSessions,
                    isRefreshing = hadSessions,
                    error = null,
                )
            }
            when (val result = userSessionRepository.listSessions()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        sessions = result.data,
                        error = null,
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = result.message,
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun openCreateSheet() {
        _uiState.update {
            it.copy(
                createForm = CreateSessionFormState(),
                pendingRevoke = null,
                error = null,
            )
        }
    }

    fun dismissCreateSheet() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(createForm = null) }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { state ->
            val form = state.createForm ?: return@update state
            state.copy(
                createForm = form.copy(
                    description = value,
                    descriptionError = false,
                    serverError = null,
                ),
            )
        }
    }

    fun showDatePicker() {
        _uiState.update { state ->
            val form = state.createForm ?: return@update state
            state.copy(createForm = form.copy(showDatePicker = true))
        }
    }

    fun dismissDatePicker() {
        _uiState.update { state ->
            val form = state.createForm ?: return@update state
            state.copy(createForm = form.copy(showDatePicker = false))
        }
    }

    fun onExpiresSelected(date: LocalDate) {
        _uiState.update { state ->
            val form = state.createForm ?: return@update state
            state.copy(
                createForm = form.copy(expires = date, showDatePicker = false, serverError = null),
            )
        }
    }

    fun createSession() {
        val form = _uiState.value.createForm ?: return
        val trimmed = form.description.trim()
        if (trimmed.length < ApiSession.MIN_DESCRIPTION_LENGTH) {
            _uiState.update {
                it.copy(createForm = form.copy(descriptionError = true, serverError = null))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (
                val result = userSessionRepository.createSession(trimmed, form.expires)
            ) {
                is Resource.Success -> {
                    val token = result.data.token
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            createForm = null,
                            createdToken = token,
                        )
                    }
                    refresh()
                }
                is Resource.Error -> _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        createForm = state.createForm?.copy(serverError = result.message),
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun dismissCreatedToken() {
        _uiState.update { it.copy(createdToken = null) }
    }

    fun requestRevoke(session: ApiSession) {
        _uiState.update {
            it.copy(pendingRevoke = session, createForm = null)
        }
    }

    fun dismissRevoke() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(pendingRevoke = null) }
    }

    fun confirmRevoke() {
        val session = _uiState.value.pendingRevoke ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = userSessionRepository.revokeSession(session.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, pendingRevoke = null)
                    }
                    refresh()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        pendingRevoke = null,
                        snackbarMessage = result.message,
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
