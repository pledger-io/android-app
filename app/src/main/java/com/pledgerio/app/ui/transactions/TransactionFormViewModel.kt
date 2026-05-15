package com.pledgerio.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class TransactionFormUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val description: String = "",
    val amount: String = "",
    val date: String = LocalDate.now().toString(),
    val type: TransactionType = TransactionType.CREDIT,
    val currency: String = "EUR",
    val sourceAccountId: Long? = null,
    val targetAccountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val currencies: List<String> = listOf("EUR"),
) {
    val isValid: Boolean
        get() = description.isNotBlank()
            && amount.toDoubleOrNull()?.let { it > 0 } == true
            && sourceAccountId != null
            && targetAccountId != null
            && runCatching { LocalDate.parse(date) }.isSuccess
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionFormUiState())
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
        loadCurrencies()
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { it.copy(description = value, error = null) }
    }

    fun onAmountChanged(value: String) {
        _uiState.update { it.copy(amount = value, error = null) }
    }

    fun onDateChanged(value: String) {
        _uiState.update { it.copy(date = value, error = null) }
    }

    fun onTypeChanged(type: TransactionType) {
        _uiState.update { it.copy(type = type, error = null) }
    }

    fun onCurrencyChanged(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun onSourceAccountChanged(accountId: Long) {
        _uiState.update { it.copy(sourceAccountId = accountId, error = null) }
    }

    fun onTargetAccountChanged(accountId: Long) {
        _uiState.update { it.copy(targetAccountId = accountId, error = null) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.isValid) {
            _uiState.update { it.copy(error = "Please fill in all required fields") }
            return
        }

        val date = try {
            LocalDate.parse(state.date)
        } catch (_: DateTimeParseException) {
            _uiState.update { it.copy(error = "Invalid date format (use YYYY-MM-DD)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val sourceAccount = state.accounts.find { it.id == state.sourceAccountId }
            val targetAccount = state.accounts.find { it.id == state.targetAccountId }

            val transaction = Transaction(
                id = 0,
                description = state.description.trim(),
                amount = state.amount.toDouble(),
                currency = state.currency,
                type = state.type,
                date = date,
                sourceAccountId = state.sourceAccountId,
                sourceAccountName = sourceAccount?.name ?: "",
                destinationAccountId = state.targetAccountId,
                destinationAccountName = targetAccount?.name ?: "",
            )

            when (val result = transactionRepository.createTransaction(transaction)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            accountRepository.getAccounts().collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val accounts = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                accounts = accounts,
                                sourceAccountId = it.sourceAccountId ?: accounts.firstOrNull()?.id,
                                targetAccountId = it.targetAccountId ?: accounts.firstOrNull()?.id,
                            )
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = result.message)
                        }
                    }
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }

    private fun loadCurrencies() {
        viewModelScope.launch {
            val currencies = currencyRepository.getCurrencies().first()
            val codes = currencies.map { it.code }
            if (codes.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        currencies = codes,
                        currency = if (codes.contains(it.currency)) it.currency else codes.first(),
                    )
                }
            }
        }
    }
}
