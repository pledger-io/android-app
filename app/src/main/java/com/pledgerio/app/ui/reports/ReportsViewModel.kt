package com.pledgerio.app.ui.reports

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ReportsUiState(
    val selectedType: ReportType = ReportType.INCOME_EXPENSE,
    val isLoading: Boolean = false,
)

@HiltViewModel
class ReportsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    fun selectReportType(type: ReportType) {
        _uiState.update { it.copy(selectedType = type) }
    }
}
