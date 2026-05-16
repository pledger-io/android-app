package com.pledgerio.app.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.usecase.SaveBudgetExpenseUseCase
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BudgetDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val budget: Budget? = null,
    val formVisible: Boolean = false,
    val formName: String = "",
    val formAmount: String = "",
    val formError: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class BudgetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
    private val saveBudgetExpenseUseCase: SaveBudgetExpenseUseCase,
) : ViewModel() {

    private val budgetId: Long = savedStateHandle.get<Long>("budgetId") ?: 0L

    private val _uiState = MutableStateFlow(BudgetDetailUiState())
    val uiState: StateFlow<BudgetDetailUiState> = _uiState.asStateFlow()

    private val _budgetListUpdates = Channel<BudgetListState>(Channel.BUFFERED)
    val budgetListUpdates = _budgetListUpdates.receiveAsFlow()

    init {
        loadBudget()
    }

    /**
     * User-initiated refresh. Forces a network fetch of the current-month budgets so the
     * spent amount and group metadata are up-to-date.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val now = LocalDate.now()
            val terminalResult = budgetRepository.getBudgets(now.year, now.monthValue)
                .filterIsInstance<Resource<BudgetListState>>()
                .first { it !is Resource.Loading }
            when (terminalResult) {
                is Resource.Success -> {
                    val match = terminalResult.data.budgets.firstOrNull { it.id == budgetId }
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            budget = match ?: it.budget,
                            error = null,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = if (it.budget == null) terminalResult.message else null,
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun openEditForm() {
        val budget = _uiState.value.budget ?: return
        _uiState.update {
            it.copy(
                formVisible = true,
                formName = budget.name,
                formAmount = formatBudgetAmountInput(budget.amount),
                formError = null,
            )
        }
    }

    fun dismissForm() {
        _uiState.update {
            it.copy(
                formVisible = false,
                formName = "",
                formAmount = "",
                formError = null,
            )
        }
    }

    fun onFormNameChange(value: String) {
        _uiState.update { it.copy(formName = value, formError = null) }
    }

    fun onFormAmountChange(value: String) {
        _uiState.update { it.copy(formAmount = value, formError = null) }
    }

    fun saveForm() {
        val state = _uiState.value
        val validationError = validateExpenseForm(state.formName, state.formAmount)
        if (validationError != null) {
            _uiState.update { it.copy(formError = validationError) }
            return
        }
        val amount = state.formAmount.replace(',', '.').toDouble()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, formError = null) }
            when (
                val result = saveBudgetExpenseUseCase(
                    id = budgetId,
                    name = state.formName,
                    budgetAmount = amount,
                )
            ) {
                is Resource.Success -> {
                    val updated = result.data.budgets.find { it.id == budgetId }
                    _budgetListUpdates.trySend(result.data)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            formVisible = false,
                            formName = "",
                            formAmount = "",
                            budget = updated ?: it.budget,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, formError = result.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
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
