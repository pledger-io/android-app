package com.pledgerio.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.usecase.SaveBudgetExpenseUseCase
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job

data class BudgetExpensesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val expenseGroups: List<Budget> = emptyList(),
    val formVisible: Boolean = false,
    val editingExpenseId: Long? = null,
    val formName: String = "",
    val formAmount: String = "",
    val formError: String? = null,
    val isSaving: Boolean = false,
) {
    val isEditing: Boolean get() = editingExpenseId != null
}

@HiltViewModel
class BudgetExpensesViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val saveBudgetExpenseUseCase: SaveBudgetExpenseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetExpensesUiState())
    val uiState: StateFlow<BudgetExpensesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var pendingBudgetListSync: BudgetListState? = null

    init {
        loadExpenseGroups()
    }

    fun peekPendingBudgetListSync(): BudgetListState? =
        pendingBudgetListSync.also { pendingBudgetListSync = null }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadExpenseGroups()
    }

    fun openCreateForm() {
        _uiState.update {
            it.copy(
                formVisible = true,
                editingExpenseId = null,
                formName = "",
                formAmount = "",
                formError = null,
            )
        }
    }

    fun openEditForm(expenseId: Long) {
        val group = _uiState.value.expenseGroups.find { it.id == expenseId } ?: return
        _uiState.update {
            it.copy(
                formVisible = true,
                editingExpenseId = expenseId,
                formName = group.name,
                formAmount = formatAmountInput(group.amount),
                formError = null,
            )
        }
    }

    fun dismissForm() {
        _uiState.update {
            it.copy(
                formVisible = false,
                editingExpenseId = null,
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
        val amount = state.formAmount.replace(',', '.').toDoubleOrNull()
        if (state.formName.isBlank()) {
            _uiState.update { it.copy(formError = "Enter a name for this expense group") }
            return
        }
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(formError = "Enter a valid monthly budget amount") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, formError = null) }
            when (
                val result = saveBudgetExpenseUseCase(
                    id = state.editingExpenseId,
                    name = state.formName,
                    budgetAmount = amount,
                )
            ) {
                is Resource.Success -> {
                    pendingBudgetListSync = result.data
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isLoading = false,
                            formVisible = false,
                            editingExpenseId = null,
                            formName = "",
                            formAmount = "",
                            expenseGroups = result.data.budgets,
                            error = if (result.data.needsInitialSetup) {
                                "Create your budget before adding expense groups."
                            } else {
                                null
                            },
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

    private fun loadExpenseGroups() {
        val now = LocalDate.now()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            budgetRepository.getBudgets(now.year, now.monthValue).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> {
                        val data = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                expenseGroups = data.budgets,
                                error = if (data.needsInitialSetup) {
                                    "Create your budget before adding expense groups."
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    is Resource.Error -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.message,
                        )
                    }
                }
            }
        }
    }

    private fun formatAmountInput(amount: Double): String =
        if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            amount.toString()
        }
}
