package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val transaction: Transaction? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>("transactionId") ?: 0L

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        loadTransaction()
    }

    private fun loadTransaction() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = transactionRepository.getTransaction(transactionId)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, transaction = result.data)
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
}
