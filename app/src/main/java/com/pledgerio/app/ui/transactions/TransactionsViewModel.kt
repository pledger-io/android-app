package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionFilters
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val MONTH_NAV_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val selectedType: TransactionType? = null,
    val currentMonth: YearMonth = YearMonth.now(),
    val currentPage: Int = 0,
    val hasMoreInMonth: Boolean = false,
    val totalRecords: Long = 0,
    val filtersExpanded: Boolean = false,
    val selectedCategory: FilterOption? = null,
    val selectedExpense: FilterOption? = null,
    val selectedContract: FilterOption? = null,
    val categoryQuery: String = "",
    val expenseQuery: String = "",
    val contractQuery: String = "",
    val categorySuggestions: List<FilterOption> = emptyList(),
    val expenseSuggestions: List<FilterOption> = emptyList(),
    val contractSuggestions: List<FilterOption> = emptyList(),
    val isSearchingCategories: Boolean = false,
    val isSearchingExpenses: Boolean = false,
    val isSearchingContracts: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val financeExperienceMode: FinanceExperienceMode = FinanceExperienceMode.GUIDED,
) {
    val hasActiveFilters: Boolean
        get() = selectedCategory != null || selectedExpense != null || selectedContract != null

    fun toTransactionFilters(): TransactionFilters = TransactionFilters(
        categoryId = selectedCategory?.id,
        expenseId = selectedExpense?.id,
        contractId = selectedContract?.id,
    )

    val monthLabel: String
        get() = currentMonth.format(MONTH_NAV_FORMATTER)
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val contractRepository: ContractRepository,
    userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private val categoryQueryFlow = MutableStateFlow("")
    private val expenseQueryFlow = MutableStateFlow("")
    private val contractQueryFlow = MutableStateFlow("")

    companion object {
        private const val PAGE_SIZE = 25
        private const val SEARCH_DEBOUNCE_MS = 250L
    }

    init {
        val now = YearMonth.now()
        _uiState.update { it.copy(currentMonth = now) }
        val expenseId = savedStateHandle.get<Long>("expenseId")?.takeIf { it >= 0 }
        val expenseName = savedStateHandle.get<String>("expenseName").orEmpty()
        if (expenseId != null) {
            _uiState.update {
                it.copy(
                    selectedExpense = FilterOption(expenseId, expenseName),
                    expenseQuery = expenseName,
                    filtersExpanded = true,
                )
            }
        }
        loadFirstPage()
        viewModelScope.launch {
            var appliedModeDefault = false
            userPreferences.financeExperienceMode.collect { mode ->
                _uiState.update { state ->
                    val filtersExpanded = when {
                        expenseId != null -> state.filtersExpanded
                        !appliedModeDefault -> mode == FinanceExperienceMode.POWER
                        else -> state.filtersExpanded
                    }
                    if (!appliedModeDefault) {
                        appliedModeDefault = true
                    }
                    state.copy(
                        financeExperienceMode = mode,
                        filtersExpanded = filtersExpanded,
                    )
                }
            }
        }
        observeSuggestions(
            queryFlow = categoryQueryFlow,
            sourceFlow = { query -> categoryRepository.observeMatching(query).mapToOptions { it.id to it.name } },
            onLoading = { _uiState.update { state -> state.copy(isSearchingCategories = true) } },
            onResult = { options ->
                _uiState.update { state ->
                    state.copy(isSearchingCategories = false, categorySuggestions = options)
                }
            },
        )
        observeSuggestions(
            queryFlow = expenseQueryFlow,
            sourceFlow = { query -> budgetRepository.observeExpenseGroups(query).mapToOptions { it.id to it.name } },
            onLoading = { _uiState.update { state -> state.copy(isSearchingExpenses = true) } },
            onResult = { options ->
                _uiState.update { state ->
                    state.copy(isSearchingExpenses = false, expenseSuggestions = options)
                }
            },
        )
        observeSuggestions(
            queryFlow = contractQueryFlow,
            sourceFlow = { query -> contractRepository.observeMatching(query).mapToOptions { it.id to it.name } },
            onLoading = { _uiState.update { state -> state.copy(isSearchingContracts = true) } },
            onResult = { options ->
                _uiState.update { state ->
                    state.copy(isSearchingContracts = false, contractSuggestions = options)
                }
            },
        )
    }

    private fun observeSuggestions(
        queryFlow: MutableStateFlow<String>,
        sourceFlow: (String) -> Flow<List<FilterOption>>,
        onLoading: () -> Unit,
        onResult: (List<FilterOption>) -> Unit,
    ) {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .onEach { if (it.isNotBlank()) onLoading() }
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(emptyList()) else sourceFlow(query)
                }
                .collect(onResult)
        }
    }

    private inline fun <T> Flow<List<T>>.mapToOptions(
        crossinline extract: (T) -> Pair<Long, String>,
    ): Flow<List<FilterOption>> = map { items ->
        items.map { item ->
            val (id, label) = extract(item)
            FilterOption(id, label)
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadFirstPage()
    }

    fun toggleFiltersExpanded() {
        _uiState.update { it.copy(filtersExpanded = !it.filtersExpanded) }
    }

    fun filterByType(type: TransactionType?) {
        _uiState.update { it.copy(selectedType = type, transactions = emptyList()) }
        loadFirstPage()
    }

    fun onCategoryQueryChanged(query: String) {
        _uiState.update { it.copy(categoryQuery = query) }
        categoryQueryFlow.value = query
    }

    fun onExpenseQueryChanged(query: String) {
        _uiState.update { it.copy(expenseQuery = query) }
        expenseQueryFlow.value = query
    }

    fun onContractQueryChanged(query: String) {
        _uiState.update { it.copy(contractQuery = query) }
        contractQueryFlow.value = query
    }

    fun selectCategory(option: FilterOption) {
        _uiState.update {
            it.copy(
                selectedCategory = option,
                categoryQuery = option.label,
                categorySuggestions = emptyList(),
                transactions = emptyList(),
            )
        }
        loadFirstPage()
    }

    fun selectExpense(option: FilterOption) {
        _uiState.update {
            it.copy(
                selectedExpense = option,
                expenseQuery = option.label,
                expenseSuggestions = emptyList(),
                transactions = emptyList(),
            )
        }
        loadFirstPage()
    }

    fun selectContract(option: FilterOption) {
        _uiState.update {
            it.copy(
                selectedContract = option,
                contractQuery = option.label,
                contractSuggestions = emptyList(),
                transactions = emptyList(),
            )
        }
        loadFirstPage()
    }

    fun clearCategoryFilter() {
        _uiState.update {
            it.copy(
                selectedCategory = null,
                categoryQuery = "",
                categorySuggestions = emptyList(),
                transactions = emptyList(),
            )
        }
        loadFirstPage()
    }

    fun clearExpenseFilter() {
        _uiState.update {
            it.copy(
                selectedExpense = null,
                expenseQuery = "",
                expenseSuggestions = emptyList(),
                transactions = emptyList(),
            )
        }
        loadFirstPage()
    }

    fun clearContractFilter() {
        _uiState.update {
            it.copy(
                selectedContract = null,
                contractQuery = "",
                contractSuggestions = emptyList(),
                transactions = emptyList(),
            )
        }
        loadFirstPage()
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                selectedCategory = null,
                selectedExpense = null,
                selectedContract = null,
                categoryQuery = "",
                expenseQuery = "",
                contractQuery = "",
                categorySuggestions = emptyList(),
                expenseSuggestions = emptyList(),
                contractSuggestions = emptyList(),
                transactions = emptyList(),
            )
        }
        loadFirstPage()
    }

    fun navigateToMonth(month: YearMonth) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                currentMonth = month,
                transactions = emptyList(),
                currentPage = 0,
                hasMoreInMonth = false,
                totalRecords = 0,
                error = null,
            )
        }
        loadFirstPage()
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMoreInMonth) return
        loadNextPage()
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(id)
            _uiState.update { it.copy(transactions = emptyList()) }
            loadFirstPage()
        }
    }

    private fun loadFirstPage() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !_uiState.value.isRefreshing) }

            val state = _uiState.value
            val startDate = state.currentMonth.atDay(1)
            val endDate = state.currentMonth.atEndOfMonth()

            val result = transactionRepository.getTransactionsPage(
                startDate = startDate,
                endDate = endDate,
                type = state.selectedType,
                filters = state.toTransactionFilters(),
                page = 0,
                pageSize = PAGE_SIZE,
            )
            when (result) {
                is Resource.Success -> {
                    val items = result.data.items
                    val totalRecords = result.data.totalRecords
                    val hasMoreInMonth = items.size.toLong() < totalRecords
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            transactions = items,
                            currentPage = 0,
                            hasMoreInMonth = hasMoreInMonth,
                            totalRecords = totalRecords,
                            error = null,
                            lastUpdatedAtMillis = System.currentTimeMillis(),
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.message,
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadNextPage() {
        val state = _uiState.value
        _uiState.update { it.copy(isLoadingMore = true) }
        val nextPage = state.currentPage + 1

        viewModelScope.launch {
            val startDate = state.currentMonth.atDay(1)
            val endDate = state.currentMonth.atEndOfMonth()

            val result = transactionRepository.getTransactionsPage(
                startDate = startDate,
                endDate = endDate,
                type = state.selectedType,
                filters = state.toTransactionFilters(),
                page = nextPage,
                pageSize = PAGE_SIZE,
            )
            when (result) {
                is Resource.Success -> {
                    val allItems = (state.transactions + result.data.items).distinctBy { it.id }
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            transactions = allItems,
                            currentPage = nextPage,
                            hasMoreInMonth = allItems.size.toLong() < result.data.totalRecords,
                            totalRecords = result.data.totalRecords,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
                is Resource.Loading -> {}
            }
        }
    }
}
