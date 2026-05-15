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
import com.pledgerio.app.ui.transactions.form.TransactionFormLabels
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
import javax.inject.Inject

enum class AccountInputKind {
    CREDITOR_AUTOCOMPLETE,
    DEBTOR_AUTOCOMPLETE,
    OWNED_DROPDOWN,
}

data class TransactionFormFieldErrors(
    val amount: String? = null,
    val source: String? = null,
    val target: String? = null,
    val description: String? = null,
)

data class TransactionFormUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val validationAttempted: Boolean = false,
    val showDatePicker: Boolean = false,
    val description: String = "",
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
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
        get() = inputKindForSource(type)

    val targetInputKind: AccountInputKind
        get() = inputKindForTarget(type)

    val sourceLabel: String get() = TransactionFormLabels.sourceLabel(type)
    val targetLabel: String get() = TransactionFormLabels.targetLabel(type)
    val flowHelperText: String get() = TransactionFormLabels.flowHelperText(type)
    val typeSubtitle: String get() = TransactionFormLabels.typeSubtitle(type)

    val fieldErrors: TransactionFormFieldErrors
        get() = if (!validationAttempted) {
            TransactionFormFieldErrors()
        } else {
            TransactionFormFieldErrors(
                amount = when {
                    amount.toDoubleOrNull()?.let { it > 0 } != true -> "Enter an amount greater than zero"
                    else -> null
                },
                source = if (sourceAccountId == null) "Choose ${sourceLabel.lowercase()}" else null,
                target = if (targetAccountId == null) "Choose ${targetLabel.lowercase()}" else null,
                description = if (description.isBlank()) "Add a short description" else null,
            )
        }

    val isValid: Boolean
        get() = description.isNotBlank()
            && amount.toDoubleOrNull()?.let { it > 0 } == true
            && sourceAccountId != null
            && targetAccountId != null

    val canSubmit: Boolean
        get() = isValid && when {
            sourceInputKind == AccountInputKind.OWNED_DROPDOWN && ownedAccounts.isEmpty() -> false
            targetInputKind == AccountInputKind.OWNED_DROPDOWN && ownedAccounts.isEmpty() -> false
            else -> true
        }

    val validationSummary: String?
        get() {
            if (!validationAttempted || canSubmit) return null
            val messages = listOfNotNull(
                fieldErrors.amount,
                fieldErrors.source,
                fieldErrors.target,
                fieldErrors.description,
            )
            return messages.firstOrNull()
        }

    companion object {
        fun inputKindForSource(type: TransactionType): AccountInputKind = when (type) {
            TransactionType.DEBIT -> AccountInputKind.DEBTOR_AUTOCOMPLETE
            TransactionType.CREDIT, TransactionType.TRANSFER -> AccountInputKind.OWNED_DROPDOWN
        }

        fun inputKindForTarget(type: TransactionType): AccountInputKind = when (type) {
            TransactionType.DEBIT, TransactionType.TRANSFER -> AccountInputKind.OWNED_DROPDOWN
            TransactionType.CREDIT -> AccountInputKind.CREDITOR_AUTOCOMPLETE
        }
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

    fun onTypeChanged(type: TransactionType) {
        _uiState.update { current ->
            val oldSourceKind = current.sourceInputKind
            val oldTargetKind = current.targetInputKind
            val newSourceKind = TransactionFormUiState.inputKindForSource(type)
            val newTargetKind = TransactionFormUiState.inputKindForTarget(type)

            val source = preserveSourceSelection(
                current = current,
                oldKind = oldSourceKind,
                newKind = newSourceKind,
            )
            val target = preserveTargetSelection(
                current = current,
                oldKind = oldTargetKind,
                newKind = newTargetKind,
            )

            current.copy(
                type = type,
                error = null,
                sourceAccountId = source.accountId,
                sourceSelected = source.selected,
                sourceQuery = source.query,
                sourceSuggestions = emptyList(),
                targetAccountId = target.accountId,
                targetSelected = target.selected,
                targetQuery = target.query,
                targetSuggestions = emptyList(),
            )
        }
    }

    fun onCurrencyChanged(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun setDateToday() {
        _uiState.update { it.copy(date = LocalDate.now(), error = null) }
    }

    fun setDateYesterday() {
        _uiState.update { it.copy(date = LocalDate.now().minusDays(1), error = null) }
    }

    fun showDatePicker() {
        _uiState.update { it.copy(showDatePicker = true) }
    }

    fun dismissDatePicker() {
        _uiState.update { it.copy(showDatePicker = false) }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(date = date, showDatePicker = false, error = null) }
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
        _uiState.update { state ->
            val account = state.ownedAccounts.find { it.id == accountId }
            state.copy(
                sourceAccountId = accountId,
                currency = account?.currency ?: state.currency,
                error = null,
            )
        }
    }

    fun onTargetDropdownSelected(accountId: Long) {
        _uiState.update { state ->
            val account = state.ownedAccounts.find { it.id == accountId }
            state.copy(
                targetAccountId = accountId,
                currency = account?.currency ?: state.currency,
                error = null,
            )
        }
    }

    fun submit() {
        _uiState.update { it.copy(validationAttempted = true) }
        val state = _uiState.value
        if (!state.canSubmit) return

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
                date = state.date,
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

    private data class PreservedSide(
        val accountId: Long?,
        val selected: FilterOption?,
        val query: String,
    )

    private fun preserveSourceSelection(
        current: TransactionFormUiState,
        oldKind: AccountInputKind,
        newKind: AccountInputKind,
    ): PreservedSide {
        if (oldKind != newKind) return PreservedSide(null, null, "")
        return when (newKind) {
            AccountInputKind.OWNED_DROPDOWN -> {
                val id = current.sourceAccountId
                if (id != null && current.ownedAccounts.any { it.id == id }) {
                    val account = current.ownedAccounts.first { it.id == id }
                    PreservedSide(id, null, "")
                } else {
                    PreservedSide(null, null, "")
                }
            }
            AccountInputKind.CREDITOR_AUTOCOMPLETE,
            AccountInputKind.DEBTOR_AUTOCOMPLETE,
            -> {
                if (current.sourceSelected != null && current.sourceAccountId != null) {
                    PreservedSide(
                        current.sourceAccountId,
                        current.sourceSelected,
                        current.sourceQuery,
                    )
                } else {
                    PreservedSide(null, null, "")
                }
            }
        }
    }

    private fun preserveTargetSelection(
        current: TransactionFormUiState,
        oldKind: AccountInputKind,
        newKind: AccountInputKind,
    ): PreservedSide {
        if (oldKind != newKind) return PreservedSide(null, null, "")
        return when (newKind) {
            AccountInputKind.OWNED_DROPDOWN -> {
                val id = current.targetAccountId
                if (id != null && current.ownedAccounts.any { it.id == id }) {
                    PreservedSide(id, null, "")
                } else {
                    PreservedSide(null, null, "")
                }
            }
            AccountInputKind.CREDITOR_AUTOCOMPLETE,
            AccountInputKind.DEBTOR_AUTOCOMPLETE,
            -> {
                if (current.targetSelected != null && current.targetAccountId != null) {
                    PreservedSide(
                        current.targetAccountId,
                        current.targetSelected,
                        current.targetQuery,
                    )
                } else {
                    PreservedSide(null, null, "")
                }
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
        searchCounterpartyAccounts(typeCode = typeCode, query = query, isSource = true)
    }

    private suspend fun searchTargetAccounts(query: String) {
        val typeCode = when (_uiState.value.targetInputKind) {
            AccountInputKind.CREDITOR_AUTOCOMPLETE -> AccountTypeCodes.CREDITOR
            AccountInputKind.DEBTOR_AUTOCOMPLETE -> AccountTypeCodes.DEBTOR
            AccountInputKind.OWNED_DROPDOWN -> return
        }
        searchCounterpartyAccounts(typeCode = typeCode, query = query, isSource = false)
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
