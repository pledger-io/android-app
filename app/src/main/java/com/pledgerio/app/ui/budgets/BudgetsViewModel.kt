package com.pledgerio.app.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.domain.usecase.CreateInitialBudgetUseCase
import com.pledgerio.app.domain.usecase.GetBudgetsUseCase
import com.pledgerio.app.domain.usecase.SaveBudgetExpenseUseCase
import com.pledgerio.app.domain.usecase.UpdateBudgetIncomeUseCase
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val BUDGET_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val budgets: List<Budget> = emptyList(),
    val monthlyIncome: Double? = null,
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
    val incomeFormVisible: Boolean = false,
    val incomeFormAmount: String = "",
    val incomeFormError: String? = null,
    val isSavingIncome: Boolean = false,
    val currentMonth: YearMonth = YearMonth.now(),
    val lastUpdatedAtMillis: Long? = null,
) {
    val monthLabel: String get() = currentMonth.format(BUDGET_MONTH_FORMATTER)
    val isEditingExpense: Boolean get() = editingExpenseId != null

    val canAddExpenseGroups: Boolean get() = !needsInitialSetup && !isLoading
}

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getBudgetsUseCase: GetBudgetsUseCase,
    private val createInitialBudgetUseCase: CreateInitialBudgetUseCase,
    private val saveBudgetExpenseUseCase: SaveBudgetExpenseUseCase,
    private val updateBudgetIncomeUseCase: UpdateBudgetIncomeUseCase,
) : ViewModel() {

    private val deepLinkYear = savedStateHandle.get<Int>("year")?.takeIf { it > 0 }
    private val deepLinkMonth = savedStateHandle.get<Int>("month")?.takeIf { it in 1..12 }
    private val initialMonth = if (deepLinkYear != null && deepLinkMonth != null) {
        YearMonth.of(deepLinkYear, deepLinkMonth)
    } else {
        YearMonth.now()
    }

    private val _uiState = MutableStateFlow(BudgetsUiState(currentMonth = initialMonth))
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    init {
        loadBudgets()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadBudgets()
    }

    fun previousMonth() {
        navigateToMonth(_uiState.value.currentMonth.minusMonths(1))
    }

    fun nextMonth() {
        val next = _uiState.value.currentMonth.plusMonths(1)
        if (next <= YearMonth.now()) {
            navigateToMonth(next)
        }
    }

    private fun navigateToMonth(month: YearMonth) {
        loadJob?.cancel()
        mutationJob?.cancel()
        _uiState.update {
            it.copy(
                currentMonth = month,
                budgets = emptyList(),
                monthlyIncome = null,
                formVisible = false,
                incomeFormVisible = false,
                isSavingExpense = false,
                isSavingIncome = false,
                error = null,
            )
        }
        loadBudgets()
    }

    fun applyBudgetListState(state: BudgetListState) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                budgets = state.budgets,
                monthlyIncome = state.income ?: it.monthlyIncome,
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

    fun openIncomeForm() {
        val income = _uiState.value.monthlyIncome ?: return
        _uiState.update {
            it.copy(
                incomeFormVisible = true,
                incomeFormAmount = formatBudgetAmountInput(income),
                incomeFormError = null,
            )
        }
    }

    fun dismissIncomeForm() {
        _uiState.update {
            it.copy(
                incomeFormVisible = false,
                incomeFormAmount = "",
                incomeFormError = null,
            )
        }
    }

    fun onIncomeFormAmountChange(value: String) {
        _uiState.update { it.copy(incomeFormAmount = value, incomeFormError = null) }
    }

    fun saveIncomeForm() {
        val state = _uiState.value
        val validationError = validateIncomeForm(state.incomeFormAmount)
        if (validationError != null) {
            _uiState.update { it.copy(incomeFormError = validationError) }
            return
        }
        val income = state.incomeFormAmount.replace(',', '.').toDouble()
        val month = state.currentMonth

        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            _uiState.update { it.copy(isSavingIncome = true, incomeFormError = null) }
            when (
                val result = updateBudgetIncomeUseCase(
                    year = month.year,
                    month = month.monthValue,
                    income = income,
                )
            ) {
                is Resource.Success -> {
                    applyBudgetListState(result.data)
                    _uiState.update {
                        it.copy(
                            isSavingIncome = false,
                            incomeFormVisible = false,
                            incomeFormAmount = "",
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isSavingIncome = false, incomeFormError = result.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun saveExpenseForm() {
        val state = _uiState.value
        val validationError = validateExpenseForm(state.formName, state.formAmount)
        if (validationError != null) {
            _uiState.update { it.copy(formError = validationError) }
            return
        }
        val amount = state.formAmount.replace(',', '.').toDouble()
        val month = state.currentMonth

        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            _uiState.update { it.copy(isSavingExpense = true, formError = null) }
            when (
                val result = saveBudgetExpenseUseCase(
                    id = state.editingExpenseId,
                    name = state.formName,
                    budgetAmount = amount,
                    year = month.year,
                    month = month.monthValue,
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

        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
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
        val month = _uiState.value.currentMonth
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            getBudgetsUseCase(month.year, month.monthValue).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> {
                        val data = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                budgets = data.budgets,
                                monthlyIncome = data.income ?: it.monthlyIncome,
                                needsInitialSetup = data.needsInitialSetup,
                                error = null,
                                setupYear = if (data.needsInitialSetup) month.year else it.setupYear,
                                setupMonth = if (data.needsInitialSetup) month.monthValue else it.setupMonth,
                                lastUpdatedAtMillis = System.currentTimeMillis(),
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
