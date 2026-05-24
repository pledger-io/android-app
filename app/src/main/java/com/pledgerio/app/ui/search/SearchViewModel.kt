package com.pledgerio.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SearchDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val error: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(SearchDefaults.DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    _uiState.update { it.copy(query = query) }
                    if (query.isBlank()) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                transactions = emptyList(),
                                accounts = emptyList(),
                                categories = emptyList(),
                            )
                        }
                    } else {
                        search(query)
                    }
                }
        }
    }

    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }

    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            val month = YearMonth.now()
            val txResult = transactionRepository.getTransactionsPage(
                startDate = month.atDay(1).minusMonths(6),
                endDate = month.atEndOfMonth(),
                filters = com.pledgerio.app.domain.model.TransactionFilters(description = query),
                page = 0,
                pageSize = 20,
            )
            val ownedAccounts = when (val result = accountRepository.refreshOwnedAccounts()) {
                is Resource.Success -> result.data.filter {
                    it.name.contains(query, ignoreCase = true)
                }
                else -> emptyList()
            }
            val partyAccounts = when (
                val result = accountRepository.getCounterpartyAccountsPage(
                    offset = 0,
                    pageSize = 25,
                    nameQuery = query,
                )
            ) {
                is Resource.Success -> result.data.items
                else -> emptyList()
            }
            val accounts = (ownedAccounts + partyAccounts).distinctBy { it.id }
            val categories = categoryRepository.searchCategories(query).let { result ->
                when (result) {
                    is Resource.Success -> result.data
                    else -> emptyList()
                }
            }
            when (txResult) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            transactions = txResult.data.items,
                            accounts = accounts,
                            categories = categories,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            error = txResult.message,
                            accounts = accounts,
                            categories = categories,
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }
}
