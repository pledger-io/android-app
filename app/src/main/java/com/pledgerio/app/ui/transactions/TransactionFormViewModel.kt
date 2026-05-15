package com.pledgerio.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeCodes
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

enum class AccountInputKind {
    CREDITOR_AUTOCOMPLETE,
    DEBTOR_AUTOCOMPLETE,
    OWNED_DROPDOWN,
}

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
    val ownedAccounts: List<Account> = emptyList(),
    val currencies: List<String> = listOf("EUR"),
    val sourceAccountId: Long? = null,
    val sourceSelected: FilterOption? = null,
    val sourceQuery: String = "",
    val sourceSuggestions: List<FilterOption> = emptyList(),
    val isSearchingSource: Boolean = false,
    val targetAccountId: Long? = null,
    val targetSelected: FilterOption? = null,
    val targetQuery: String = "",
    val targetSuggestions: List<FilterOption> = emptyList(),
    val isSearchingTarget: Boolean = false,
) {
    val sourceInputKind: AccountInputKind
        get() = when (type) {
            TransactionType.DEBIT -> AccountInputKind.DEBTOR_AUTOCOMPLETE
            TransactionType.CREDIT, TransactionType.TRANSFER -> AccountInputKind.OWNED_DROPDOWN
        }

    val targetInputKind: AccountInputKind
        get() = when (type) {
            TransactionType.DEBIT, TransactionType.TRANSFER -> AccountInputKind.OWNED_DROPDOWN
            TransactionType.CREDIT -> AccountInputKind.CREDITOR_AUTOCOMPLETE
        }

    val isValid: Boolean
        get() = description.isNotBlank()
            && amount.toDoubleOrNull()?.let { it > 0 } == true
            && sourceAccountId != null
            && targetAccountId != null
            && runCatching { LocalDate.parse(date) }.isSuccess

    val canSubmit: Boolean
        get() = isValid && when {
            sourceInputKind == AccountInputKind.OWNED_DROPDOWN && ownedAccounts.isEmpty() -> false
            targetInputKind == AccountInputKind.OWNED_DROPDOWN && ownedAccounts.isEmpty() -> false
            else -> true
        }
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionFormUiState())
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    private var sourceSearchJob: Job? = null
    private var targetSearchJob: Job? = null

    init {
        loadOwnedAccounts()
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
        _uiState.update {
            it.copy(
                type = type,
                error = null,
                sourceAccountId = null,
                sourceSelected = null,
                sourceQuery = "",
                sourceSuggestions = emptyList(),
                targetAccountId = null,
                targetSelected = null,
                targetQuery = "",
                targetSuggestions = emptyList(),
            )
        }
    }

    fun onCurrencyChanged(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun onSourceQueryChanged(query: String) {
        _uiState.update { it.copy(sourceQuery = query, error = null) }
        sourceSearchJob?.cancel()
        sourceSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchSourceAccounts(query)
        }
    }

    fun onTargetQueryChanged(query: String) {
        _uiState.update { it.copy(targetQuery = query, error = null) }
        targetSearchJob?.cancel()
        targetSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            searchTargetAccounts(query)
        }
    }

    fun selectSourceAutocomplete(option: FilterOption) {
        _uiState.update {
            it.copy(
                sourceSelected = option,
                sourceQuery = option.label,
                sourceAccountId = option.id,
                sourceSuggestions = emptyList(),
                error = null,
            )
        }
    }

    fun selectTargetAutocomplete(option: FilterOption) {
        _uiState.update {
            it.copy(
                targetSelected = option,
                targetQuery = option.label,
                targetAccountId = option.id,
                targetSuggestions = emptyList(),
                error = null,
            )
        }
    }

    fun clearSourceAccount() {
        _uiState.update {
            it.copy(
                sourceSelected = null,
                sourceQuery = "",
                sourceAccountId = null,
                sourceSuggestions = emptyList(),
            )
        }
    }

    fun clearTargetAccount() {
        _uiState.update {
            it.copy(
                targetSelected = null,
                targetQuery = "",
                targetAccountId = null,
                targetSuggestions = emptyList(),
            )
        }
    }

    fun onSourceDropdownSelected(accountId: Long) {
        _uiState.update { it.copy(sourceAccountId = accountId, error = null) }
    }

    fun onTargetDropdownSelected(accountId: Long) {
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

            val sourceName = resolveAccountName(
                accountId = state.sourceAccountId,
                selected = state.sourceSelected,
                ownedAccounts = state.ownedAccounts,
            )
            val targetName = resolveAccountName(
                accountId = state.targetAccountId,
                selected = state.targetSelected,
                ownedAccounts = state.ownedAccounts,
            )

            val transaction = Transaction(
                id = 0,
                description = state.description.trim(),
                amount = state.amount.toDouble(),
                currency = state.currency,
                type = state.type,
                date = date,
                sourceAccountId = state.sourceAccountId,
                sourceAccountName = sourceName,
                destinationAccountId = state.targetAccountId,
                destinationAccountName = targetName,
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

    private fun resolveAccountName(
        accountId: Long?,
        selected: FilterOption?,
        ownedAccounts: List<Account>,
    ): String {
        selected?.label?.let { return it }
        return ownedAccounts.find { it.id == accountId }?.name ?: ""
    }

    private suspend fun searchSourceAccounts(query: String) {
        val typeCode = when (_uiState.value.sourceInputKind) {
            AccountInputKind.CREDITOR_AUTOCOMPLETE -> AccountTypeCodes.CREDITOR
            AccountInputKind.DEBTOR_AUTOCOMPLETE -> AccountTypeCodes.DEBTOR
            AccountInputKind.OWNED_DROPDOWN -> return
        }
        searchCounterpartyAccounts(
            typeCode = typeCode,
            query = query,
            isSource = true,
        )
    }

    private suspend fun searchTargetAccounts(query: String) {
        val typeCode = when (_uiState.value.targetInputKind) {
            AccountInputKind.CREDITOR_AUTOCOMPLETE -> AccountTypeCodes.CREDITOR
            AccountInputKind.DEBTOR_AUTOCOMPLETE -> AccountTypeCodes.DEBTOR
            AccountInputKind.OWNED_DROPDOWN -> return
        }
        searchCounterpartyAccounts(
            typeCode = typeCode,
            query = query,
            isSource = false,
        )
    }

    private suspend fun searchCounterpartyAccounts(
        typeCode: String,
        query: String,
        isSource: Boolean,
    ) {
        if (query.isBlank()) {
            _uiState.update {
                if (isSource) {
                    it.copy(sourceSuggestions = emptyList(), isSearchingSource = false)
                } else {
                    it.copy(targetSuggestions = emptyList(), isSearchingTarget = false)
                }
            }
            return
        }

        _uiState.update {
            if (isSource) it.copy(isSearchingSource = true)
            else it.copy(isSearchingTarget = true)
        }

        when (val result = accountRepository.searchAccounts(typeCode, query)) {
            is Resource.Success -> {
                val options = result.data.map { account -> FilterOption(account.id, account.name) }
                _uiState.update {
                    if (isSource) {
                        it.copy(isSearchingSource = false, sourceSuggestions = options)
                    } else {
                        it.copy(isSearchingTarget = false, targetSuggestions = options)
                    }
                }
            }
            is Resource.Error -> {
                _uiState.update {
                    if (isSource) {
                        it.copy(isSearchingSource = false, sourceSuggestions = emptyList())
                    } else {
                        it.copy(isSearchingTarget = false, targetSuggestions = emptyList())
                    }
                }
            }
            is Resource.Loading -> {}
        }
    }

    private fun loadOwnedAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val ownedTypeCodes = when (val typesResult = accountRepository.getAccountTypes()) {
                is Resource.Success -> typesResult.data
                    .filter { !it.isCounterparty }
                    .map { it.code }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = typesResult.message)
                    }
                    return@launch
                }
                is Resource.Loading -> emptyList()
            }

            when (val accountsResult = accountRepository.getAccountsByTypes(ownedTypeCodes)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            ownedAccounts = accountsResult.data,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = accountsResult.message)
                    }
                }
                is Resource.Loading -> {}
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

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
