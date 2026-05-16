package com.pledgerio.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionFilters
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val contractRepository: ContractRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var categorySearchJob: Job? = null
    private var expenseSearchJob: Job? = null
    private var contractSearchJob: Job? = null

    companion object {
        private const val PAGE_SIZE = 25
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    init {
        val now = YearMonth.now()
        _uiState.update { it.copy(currentMonth = now) }
        loadFirstPage()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, transactions = emptyList()) }
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
        categorySearchJob?.cancel()
        categorySearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchCategories(query)
        }
    }

    fun onExpenseQueryChanged(query: String) {
        _uiState.update { it.copy(expenseQuery = query) }
        expenseSearchJob?.cancel()
        expenseSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchExpenses(query)
        }
    }

    fun onContractQueryChanged(query: String) {
        _uiState.update { it.copy(contractQuery = query) }
        contractSearchJob?.cancel()
        contractSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchContracts(query)
        }
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

    private suspend fun searchCategories(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(categorySuggestions = emptyList(), isSearchingCategories = false) }
            return
        }
        _uiState.update { it.copy(isSearchingCategories = true) }
        when (val result = categoryRepository.searchCategories(query)) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isSearchingCategories = false,
                        categorySuggestions = result.data.map { c -> FilterOption(c.id, c.name) },
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isSearchingCategories = false, categorySuggestions = emptyList()) }
            }
            is Resource.Loading -> {}
        }
    }

    private suspend fun searchExpenses(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(expenseSuggestions = emptyList(), isSearchingExpenses = false) }
            return
        }
        _uiState.update { it.copy(isSearchingExpenses = true) }
        when (val result = budgetRepository.searchExpenses(query)) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isSearchingExpenses = false,
                        expenseSuggestions = result.data.map { e -> FilterOption(e.id, e.name) },
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isSearchingExpenses = false, expenseSuggestions = emptyList()) }
            }
            is Resource.Loading -> {}
        }
    }

    private suspend fun searchContracts(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(contractSuggestions = emptyList(), isSearchingContracts = false) }
            return
        }
        _uiState.update { it.copy(isSearchingContracts = true) }
        when (val result = contractRepository.searchContracts(query)) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isSearchingContracts = false,
                        contractSuggestions = result.data.map { c -> FilterOption(c.id, c.name) },
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isSearchingContracts = false, contractSuggestions = emptyList()) }
            }
            is Resource.Loading -> {}
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
