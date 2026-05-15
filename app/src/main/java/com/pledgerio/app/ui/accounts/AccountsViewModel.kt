package com.pledgerio.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountListFilter
import com.pledgerio.app.domain.model.AccountSection
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val ownedAccounts: List<Account> = emptyList(),
    val accountTypeOptions: List<AccountTypeOption> = emptyList(),
    val filter: AccountListFilter = AccountListFilter.ALL,
    val counterpartyTotal: Long = 0,
    val counterpartyAccounts: List<Account> = emptyList(),
    val counterpartySearchQuery: String = "",
    val isLoadingCounterparties: Boolean = false,
    val isLoadingMoreCounterparties: Boolean = false,
    val hasMoreCounterparties: Boolean = true,
    val counterpartyError: String? = null,
) {
    val ownedCount: Int get() = ownedAccounts.size

    val sections: List<AccountSection>
        get() = when (filter) {
            AccountListFilter.COUNTERPARTY -> AccountTypeCatalog.sectionAccounts(counterpartyAccounts)
            AccountListFilter.OWNED -> AccountTypeCatalog.sectionAccounts(ownedAccounts)
            AccountListFilter.ALL -> AccountTypeCatalog.sectionAccounts(ownedAccounts)
        }

    val filteredAccounts: List<Account>
        get() = when (filter) {
            AccountListFilter.OWNED -> ownedAccounts
            AccountListFilter.COUNTERPARTY -> counterpartyAccounts
            AccountListFilter.ALL -> ownedAccounts
        }

    val totalBalance: Double
        get() = filteredAccounts.sumOf { it.balance }

    val counterpartyLoadedCount: Int get() = counterpartyAccounts.size

    val showCounterpartyBrowseCard: Boolean
        get() = filter == AccountListFilter.ALL && counterpartyTotal > 0
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private var counterpartySearchJob: Job? = null
    private var counterpartyLoadJob: Job? = null

    init {
        loadAccountTypes()
        loadOwnedAccounts()
        loadCounterpartyTotal()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadOwnedAccounts()
        loadCounterpartyTotal()
        if (_uiState.value.filter == AccountListFilter.COUNTERPARTY) {
            loadCounterpartyPage(reset = true)
        } else {
            _uiState.update {
                it.copy(
                    counterpartyAccounts = emptyList(),
                    hasMoreCounterparties = true,
                )
            }
        }
    }

    fun setFilter(filter: AccountListFilter) {
        _uiState.update { it.copy(filter = filter, error = null) }
        if (filter == AccountListFilter.COUNTERPARTY &&
            _uiState.value.counterpartyAccounts.isEmpty() &&
            !_uiState.value.isLoadingCounterparties
        ) {
            loadCounterpartyPage(reset = true)
        }
    }

    fun onCounterpartySearchChanged(query: String) {
        _uiState.update { it.copy(counterpartySearchQuery = query) }
        counterpartySearchJob?.cancel()
        counterpartySearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadCounterpartyPage(reset = true)
        }
    }

    fun loadMoreCounterparties() {
        val state = _uiState.value
        if (state.isLoadingCounterparties || state.isLoadingMoreCounterparties || !state.hasMoreCounterparties) {
            return
        }
        loadCounterpartyPage(reset = false)
    }

    private fun loadOwnedAccounts() {
        viewModelScope.launch {
            accountRepository.getAccounts().collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            ownedAccounts = result.data,
                            error = null,
                        )
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

    private fun loadCounterpartyTotal() {
        viewModelScope.launch {
            when (
                val result = accountRepository.getCounterpartyAccountsPage(
                    offset = 0,
                    pageSize = 1,
                    nameQuery = "",
                )
            ) {
                is Resource.Success -> {
                    _uiState.update { it.copy(counterpartyTotal = result.data.totalRecords) }
                }
                is Resource.Error, is Resource.Loading -> {}
            }
        }
    }

    private fun loadCounterpartyPage(reset: Boolean) {
        counterpartyLoadJob?.cancel()
        counterpartyLoadJob = viewModelScope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.counterpartyAccounts.size
            _uiState.update {
                it.copy(
                    isLoadingCounterparties = reset,
                    isLoadingMoreCounterparties = !reset,
                    counterpartyError = null,
                    counterpartyAccounts = if (reset) emptyList() else it.counterpartyAccounts,
                    hasMoreCounterparties = if (reset) true else it.hasMoreCounterparties,
                )
            }

            when (
                val result = accountRepository.getCounterpartyAccountsPage(
                    offset = offset,
                    pageSize = COUNTERPARTY_PAGE_SIZE,
                    nameQuery = state.counterpartySearchQuery,
                )
            ) {
                is Resource.Success -> {
                    val page = result.data
                    _uiState.update { current ->
                        val merged = if (reset) {
                            page.items
                        } else {
                            (current.counterpartyAccounts + page.items).distinctBy { it.id }
                        }
                        current.copy(
                            isLoadingCounterparties = false,
                            isLoadingMoreCounterparties = false,
                            counterpartyAccounts = merged,
                            counterpartyTotal = page.totalRecords,
                            hasMoreCounterparties = page.hasMore,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCounterparties = false,
                            isLoadingMoreCounterparties = false,
                            counterpartyError = result.message,
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadAccountTypes() {
        viewModelScope.launch {
            when (val result = accountRepository.getAccountTypes()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(accountTypeOptions = result.data) }
                }
                is Resource.Error, is Resource.Loading -> {}
            }
        }
    }

    companion object {
        private const val COUNTERPARTY_PAGE_SIZE = 50
        private const val SEARCH_DEBOUNCE_MS = 350L
    }
}
