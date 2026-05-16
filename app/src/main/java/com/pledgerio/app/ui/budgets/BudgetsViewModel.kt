package com.pledgerio.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.usecase.CreateInitialBudgetUseCase
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.usecase.SaveBudgetExpenseUseCase
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.pledgerio.app.domain.model.BudgetListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val budgets: List<Budget> = emptyList(),
    val needsInitialSetup: Boolean = false,
    val isCreatingInitial: Boolean = false,
    val setupYear: Int = LocalDate.now().year,
    val setupMonth: Int = LocalDate.now().monthValue,
    val setupIncome: String = "",
    val setupError: String? = null,
    val formVisible: Boolean = false,
    val editingExpenseId: Long? = null,
    val formName: String = "",
    val formAmount: String = "",
    val formError: String? = null,
    val isSavingExpense: Boolean = false,
) {
    val isEditingExpense: Boolean get() = editingExpenseId != null

    val canAddExpenseGroups: Boolean get() = !needsInitialSetup && !isLoading
}

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val createInitialBudgetUseCase: CreateInitialBudgetUseCase,
    private val saveBudgetExpenseUseCase: SaveBudgetExpenseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetsUiState())
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadBudgets()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadBudgets()
    }

    fun applyBudgetListState(state: BudgetListState) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                budgets = state.budgets,
                needsInitialSetup = state.needsInitialSetup,
                error = null,
            )
        }
    }

    fun onScreenVisible() {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        loadBudgets()
    }

    fun onSetupYearChange(year: Int) {
        _uiState.update { it.copy(setupYear = year, setupError = null) }
    }

    fun onSetupMonthChange(month: Int) {
        _uiState.update { it.copy(setupMonth = month, setupError = null) }
    }

    fun onSetupIncomeChange(income: String) {
        _uiState.update { it.copy(setupIncome = income, setupError = null) }
    }

    fun openCreateExpenseForm() {
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

    fun dismissExpenseForm() {
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

    fun onExpenseFormNameChange(value: String) {
        _uiState.update { it.copy(formName = value, formError = null) }
    }

    fun onExpenseFormAmountChange(value: String) {
        _uiState.update { it.copy(formAmount = value, formError = null) }
    }

    fun saveExpenseForm() {
        val state = _uiState.value
        val validationError = validateExpenseForm(state.formName, state.formAmount)
        if (validationError != null) {
            _uiState.update { it.copy(formError = validationError) }
            return
        }
        val amount = state.formAmount.replace(',', '.').toDouble()

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingExpense = true, formError = null) }
            when (
                val result = saveBudgetExpenseUseCase(
                    id = state.editingExpenseId,
                    name = state.formName,
                    budgetAmount = amount,
                )
            ) {
                is Resource.Success -> {
                    applyBudgetListState(result.data)
                    _uiState.update {
                        it.copy(
                            isSavingExpense = false,
                            formVisible = false,
                            editingExpenseId = null,
                            formName = "",
                            formAmount = "",
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isSavingExpense = false, formError = result.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun createInitialBudget() {
        val state = _uiState.value
        val income = state.setupIncome.replace(',', '.').toDoubleOrNull()
        if (income == null || income < 0) {
            _uiState.update { it.copy(setupError = "Enter a valid monthly net income") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingInitial = true, setupError = null) }
            when (
                val result = createInitialBudgetUseCase(
                    year = state.setupYear,
                    month = state.setupMonth,
                    income = income,
                )
            ) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isCreatingInitial = false,
                            needsInitialSetup = false,
                            setupIncome = "",
                        )
                    }
                    loadBudgets()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isCreatingInitial = false,
                            setupError = result.message,
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun loadBudgets() {
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
                                budgets = data.budgets,
                                needsInitialSetup = data.needsInitialSetup,
                                error = null,
                                setupYear = if (data.needsInitialSetup) now.year else it.setupYear,
                                setupMonth = if (data.needsInitialSetup) now.monthValue else it.setupMonth,
                            )
                        }
                    }
                    is Resource.Error -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.message,
                            needsInitialSetup = false,
                        )
                    }
                }
            }
        }
    }
}
