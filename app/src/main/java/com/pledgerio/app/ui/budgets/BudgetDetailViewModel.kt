package com.pledgerio.app.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val budget: Budget? = null,
)

@HiltViewModel
class BudgetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val budgetId: Long = savedStateHandle.get<Long>("budgetId") ?: 0L

    private val _uiState = MutableStateFlow(BudgetDetailUiState())
    val uiState: StateFlow<BudgetDetailUiState> = _uiState.asStateFlow()

    init {
        loadBudget()
    }

    private fun loadBudget() {
        viewModelScope.launch {
            when (val result = budgetRepository.getBudget(budgetId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, budget = result.data) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }
}
