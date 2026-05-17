package com.pledgerio.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteSuccess: Boolean = false,
    val error: String? = null,
    val account: Account? = null,
    val transactions: List<Transaction> = emptyList(),
    val hasMore: Boolean = true,
    val totalTransactionCount: Long = 0,
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<Long>("accountId") ?: 0L

    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

    private var transactionsJob: Job? = null
    private var loadingOffset: Int? = null

    companion object {
        private const val PAGE_SIZE = 25
    }

    init {
        loadAccount()
        loadTransactionsPage(offset = 0)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        loadTransactionsPage(offset = state.transactions.size)
    }

    fun reload() {
        transactionsJob?.cancel()
        loadingOffset = null
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                transactions = emptyList(),
                hasMore = true,
                totalTransactionCount = 0,
            )
        }
        loadAccount()
        loadTransactionsPage(offset = 0)
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, error = null) }
            when (val result = accountRepository.deleteAccount(accountId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isDeleting = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadAccount() {
        viewModelScope.launch {
            when (val result = accountRepository.getAccount(accountId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false, account = result.data) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadTransactionsPage(offset: Int) {
        if (loadingOffset == offset) return
        loadingOffset = offset
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = offset > 0) }

            val now = YearMonth.now()
            val startDate = now.minusMonths(12).atDay(1)
            val endDate = now.atEndOfMonth()

            val result = transactionRepository.getTransactionsPage(
                startDate = startDate,
                endDate = endDate,
                accountId = accountId,
                pageSize = PAGE_SIZE,
                offset = offset,
            )
            when (result) {
                is Resource.Success -> {
                    val previousSize = if (offset == 0) 0 else _uiState.value.transactions.size
                    val newItems = result.data.items
                    val merged = if (offset == 0) {
                        newItems
                    } else {
                        _uiState.value.transactions + newItems
                    }
                    val allItems = merged.distinctBy { it.id }
                    val totalRecords = result.data.totalRecords
                        .coerceAtLeast(allItems.size.toLong())
                    val grew = allItems.size > previousSize
                    val hasMore = allItems.size < totalRecords &&
                        newItems.isNotEmpty() &&
                        (grew || newItems.size >= PAGE_SIZE)
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            transactions = allItems,
                            totalTransactionCount = totalRecords,
                            hasMore = hasMore,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                }
                is Resource.Loading -> {}
            }
            loadingOffset = null
        }
    }
}
