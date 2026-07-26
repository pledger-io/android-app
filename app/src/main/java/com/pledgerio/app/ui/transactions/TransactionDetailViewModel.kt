package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val transaction: Transaction? = null,
    val sourceAccount: Account? = null,
    val destinationAccount: Account? = null,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
)

data class TransactionDeletedEvent(val transactionId: Long)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>("transactionId") ?: 0L

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()
    private val deletionEvents = Channel<TransactionDeletedEvent>(Channel.BUFFERED)
    val deletedEvents = deletionEvents.receiveAsFlow()

    init {
        loadTransaction()
    }

    fun reload() {
        _uiState.update { it.copy(error = null) }
        loadTransaction()
    }

    fun deleteTransaction() {
        val transaction = _uiState.value.transaction ?: return
        if (_uiState.value.isDeleting) return

        _uiState.update { it.copy(isDeleting = true, deleteFailed = false) }
        viewModelScope.launch {
            try {
                when (
                    transactionRepository.deleteTransaction(
                        id = transaction.id,
                        transactionDate = transaction.date,
                    )
                ) {
                    is Resource.Success -> {
                        _uiState.update { it.copy(isDeleting = false, deleteFailed = false) }
                        deletionEvents.send(TransactionDeletedEvent(transaction.id))
                    }
                    is Resource.Error, is Resource.Loading -> {
                        _uiState.update { it.copy(isDeleting = false, deleteFailed = true) }
                    }
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isDeleting = false) }
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(isDeleting = false, deleteFailed = true) }
            }
        }
    }

    fun clearDeleteError() {
        _uiState.update { it.copy(deleteFailed = false) }
    }

    private fun loadTransaction() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = transactionRepository.getTransaction(transactionId)) {
                is Resource.Success -> {
                    val transaction = result.data
                    val sourceAccount = transaction.sourceAccountId?.let { loadAccount(it) }
                    val destinationAccount = transaction.destinationAccountId?.let { loadAccount(it) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            transaction = transaction,
                            sourceAccount = sourceAccount,
                            destinationAccount = destinationAccount,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private suspend fun loadAccount(accountId: Long): Account? {
        return when (val result = accountRepository.getAccount(accountId)) {
            is Resource.Success -> result.data
            else -> null
        }
    }
}
