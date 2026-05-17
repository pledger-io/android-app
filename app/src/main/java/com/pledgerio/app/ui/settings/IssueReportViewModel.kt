package com.pledgerio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.IssueReportResult
import com.pledgerio.app.domain.repository.IssueReportRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IssueReportUiState(
    val showDialog: Boolean = false,
    val title: String = "",
    val description: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val readyToOpen: IssueReportResult? = null,
)

@HiltViewModel
class IssueReportViewModel @Inject constructor(
    private val issueReportRepository: IssueReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IssueReportUiState())
    val uiState: StateFlow<IssueReportUiState> = _uiState.asStateFlow()

    fun openDialog() {
        _uiState.update {
            IssueReportUiState(
                showDialog = true,
                title = it.title,
                description = it.description,
            )
        }
    }

    fun dismissDialog() {
        _uiState.value = IssueReportUiState()
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value, error = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, readyToOpen = null) }
            when (val result = issueReportRepository.submitBugReport(state.title, state.description)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            readyToOpen = result.data,
                            showDialog = false,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, error = result.message)
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun clearReadyToOpen() {
        _uiState.update { it.copy(readyToOpen = null) }
    }
}
