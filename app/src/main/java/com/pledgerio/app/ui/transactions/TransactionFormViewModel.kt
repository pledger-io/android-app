package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeCodes
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionTemplate
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.ui.transactions.form.TransactionFormLabels
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.TransactionTemplateStore
import com.pledgerio.app.util.UserPreferences
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

enum class OwnedAccountPickerSide {
    SOURCE,
    TARGET,
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
    val isEditing: Boolean = false,
    val editingTransactionId: Long? = null,
    val moreOptionsExpanded: Boolean = false,
    val ownedAccountPickerSide: OwnedAccountPickerSide? = null,
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
    val categoryQuery: String = "",
    val categorySelected: FilterOption? = null,
    val categorySuggestions: List<FilterOption> = emptyList(),
    val isSearchingCategory: Boolean = false,
    val expenseQuery: String = "",
    val expenseSelected: FilterOption? = null,
    val expenseSuggestions: List<FilterOption> = emptyList(),
    val isSearchingExpense: Boolean = false,
    val contractQuery: String = "",
    val contractSelected: FilterOption? = null,
    val contractSuggestions: List<FilterOption> = emptyList(),
    val isSearchingContract: Boolean = false,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val templates: List<TransactionTemplate> = emptyList(),
    val showSaveTemplateDialog: Boolean = false,
    val saveTemplateName: String = "",
) {
    val sourceInputKind: AccountInputKind
        get() = inputKindForSource(type)

    val targetInputKind: AccountInputKind
        get() = inputKindForTarget(type)

    val sourceLabel: String get() = TransactionFormLabels.sourceLabel(type)
    val targetLabel: String get() = TransactionFormLabels.targetLabel(type)
    val flowHelperText: String get() = TransactionFormLabels.flowHelperText(type)
    val typeSubtitle: String get() = TransactionFormLabels.typeSubtitle(type)
    val screenTitle: String get() = if (isEditing) "Edit transaction" else "New transaction"
    val submitLabel: String get() = if (isEditing) "Save changes" else "Create transaction"

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
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val contractRepository: ContractRepository,
    private val userPreferences: UserPreferences,
    private val transactionTemplateStore: TransactionTemplateStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editTransactionId: Long? = savedStateHandle.get<Long>("transactionId")

    private val _uiState = MutableStateFlow(
        TransactionFormUiState(
            isEditing = editTransactionId != null,
            editingTransactionId = editTransactionId,
        ),
    )
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    private var sourceSearchJob: Job? = null
    private var targetSearchJob: Job? = null
    private var categorySearchJob: Job? = null
    private var expenseSearchJob: Job? = null
    private var contractSearchJob: Job? = null

    init {
        loadOwnedAccounts()
        loadCurrencies()
        observeTemplates()
        if (editTransactionId != null) {
            loadTransactionForEdit(editTransactionId)
        }
    }

    private fun observeTemplates() {
        viewModelScope.launch {
            transactionTemplateStore.templates.collect { templates ->
                _uiState.update { it.copy(templates = templates) }
            }
        }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { it.copy(description = value, error = null) }
    }

    fun onAmountChanged(value: String) {
        _uiState.update { it.copy(amount = value, error = null) }
    }

    fun onTypeChanged(type: TransactionType) {
        viewModelScope.launch {
            userPreferences.setLastTransactionType(type)
        }
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

    fun toggleMoreOptions() {
        _uiState.update { it.copy(moreOptionsExpanded = !it.moreOptionsExpanded) }
    }

    fun onTagInputChanged(value: String) {
        if (value.endsWith(",")) {
            val tag = value.dropLast(1).trim()
            if (tag.isNotEmpty()) addTag(tag)
            _uiState.update { it.copy(tagInput = "") }
        } else {
            _uiState.update { it.copy(tagInput = value) }
        }
    }

    fun addTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        _uiState.update { state ->
            if (state.tags.any { it.equals(tag, ignoreCase = true) }) {
                state.copy(tagInput = "")
            } else {
                state.copy(tags = state.tags + tag, tagInput = "")
            }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags.filterNot { existing -> existing == tag }) }
    }

    fun showSaveTemplateDialog() {
        val state = _uiState.value
        val defaultName = state.description.trim().ifBlank { "New template" }
        _uiState.update {
            it.copy(
                showSaveTemplateDialog = true,
                saveTemplateName = defaultName,
            )
        }
    }

    fun dismissSaveTemplateDialog() {
        _uiState.update { it.copy(showSaveTemplateDialog = false, saveTemplateName = "") }
    }

    fun onSaveTemplateNameChanged(name: String) {
        _uiState.update { it.copy(saveTemplateName = name) }
    }

    fun confirmSaveTemplate() {
        val state = _uiState.value
        val name = state.saveTemplateName.trim()
        if (name.isEmpty()) return

        viewModelScope.launch {
            transactionTemplateStore.saveFromForm(
                name = name,
                description = state.description,
                amount = state.amount,
                type = state.type.name,
                currency = state.currency,
                sourceAccountId = state.sourceAccountId,
                sourceAccountName = resolveAccountName(
                    state.sourceAccountId,
                    state.sourceSelected,
                    state.ownedAccounts,
                ),
                targetAccountId = state.targetAccountId,
                targetAccountName = resolveAccountName(
                    state.targetAccountId,
                    state.targetSelected,
                    state.ownedAccounts,
                ),
                tags = state.tags,
            )
            _uiState.update { it.copy(showSaveTemplateDialog = false, saveTemplateName = "") }
        }
    }

    fun applyTemplate(template: TransactionTemplate) {
        val type = template.typeEnum ?: return
        viewModelScope.launch {
            userPreferences.setLastTransactionType(type)
            val sourceKind = TransactionFormUiState.inputKindForSource(type)
            val targetKind = TransactionFormUiState.inputKindForTarget(type)
            _uiState.update { state ->
                val sourceId = template.sourceAccountId?.takeIf { id ->
                    sourceKind == AccountInputKind.OWNED_DROPDOWN &&
                        state.ownedAccounts.any { it.id == id }
                }
                val targetId = template.targetAccountId?.takeIf { id ->
                    targetKind == AccountInputKind.OWNED_DROPDOWN &&
                        state.ownedAccounts.any { it.id == id }
                }
                state.copy(
                    type = type,
                    description = template.description,
                    amount = template.amount,
                    currency = if (state.currencies.contains(template.currency)) {
                        template.currency
                    } else {
                        state.currency
                    },
                    tags = template.tags,
                    tagInput = "",
                    sourceAccountId = sourceId,
                    sourceSelected = if (
                        sourceKind != AccountInputKind.OWNED_DROPDOWN &&
                        template.sourceAccountId != null
                    ) {
                        FilterOption(template.sourceAccountId, template.sourceAccountName)
                    } else {
                        null
                    },
                    sourceQuery = template.sourceAccountName,
                    targetAccountId = targetId,
                    targetSelected = if (
                        targetKind != AccountInputKind.OWNED_DROPDOWN &&
                        template.targetAccountId != null
                    ) {
                        FilterOption(template.targetAccountId, template.targetAccountName)
                    } else {
                        null
                    },
                    targetQuery = template.targetAccountName,
                    moreOptionsExpanded = template.tags.isNotEmpty(),
                    error = null,
                )
            }
        }
    }

    fun openOwnedAccountPicker(side: OwnedAccountPickerSide) {
        _uiState.update { it.copy(ownedAccountPickerSide = side) }
    }

    fun dismissOwnedAccountPicker() {
        _uiState.update { it.copy(ownedAccountPickerSide = null) }
    }

    fun partyTypeCodeForNewAccount(isSource: Boolean): String? {
        val kind = if (isSource) _uiState.value.sourceInputKind else _uiState.value.targetInputKind
        return when (kind) {
            AccountInputKind.CREDITOR_AUTOCOMPLETE -> AccountTypeCodes.CREDITOR
            AccountInputKind.DEBTOR_AUTOCOMPLETE -> AccountTypeCodes.DEBTOR
            AccountInputKind.OWNED_DROPDOWN -> null
        }
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
                categorySelected = option,
                categoryQuery = option.label,
                categorySuggestions = emptyList(),
            )
        }
    }

    fun clearCategory() {
        _uiState.update {
            it.copy(categorySelected = null, categoryQuery = "", categorySuggestions = emptyList())
        }
    }

    fun selectExpense(option: FilterOption) {
        _uiState.update {
            it.copy(
                expenseSelected = option,
                expenseQuery = option.label,
                expenseSuggestions = emptyList(),
            )
        }
    }

    fun clearExpense() {
        _uiState.update {
            it.copy(expenseSelected = null, expenseQuery = "", expenseSuggestions = emptyList())
        }
    }

    fun selectContract(option: FilterOption) {
        _uiState.update {
            it.copy(
                contractSelected = option,
                contractQuery = option.label,
                contractSuggestions = emptyList(),
            )
        }
    }

    fun clearContract() {
        _uiState.update {
            it.copy(contractSelected = null, contractQuery = "", contractSuggestions = emptyList())
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
                ownedAccountPickerSide = null,
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
                ownedAccountPickerSide = null,
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
                id = state.editingTransactionId ?: 0,
                description = state.description.trim(),
                amount = state.amount.toDouble(),
                currency = state.currency,
                type = state.type,
                date = state.date,
                sourceAccountId = state.sourceAccountId,
                sourceAccountName = sourceName,
                destinationAccountId = state.targetAccountId,
                destinationAccountName = targetName,
                categoryId = state.categorySelected?.id,
                expenseId = state.expenseSelected?.id,
                contractId = state.contractSelected?.id,
                tags = state.tags,
            )

            val result = if (state.isEditing && state.editingTransactionId != null) {
                transactionRepository.updateTransaction(transaction)
            } else {
                transactionRepository.createTransaction(transaction)
            }

            when (result) {
                is Resource.Success -> {
                    userPreferences.setLastTransactionType(state.type)
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

    private suspend fun searchCategories(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(categorySuggestions = emptyList(), isSearchingCategory = false) }
            return
        }
        _uiState.update { it.copy(isSearchingCategory = true) }
        when (val result = categoryRepository.searchCategories(query)) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isSearchingCategory = false,
                        categorySuggestions = result.data.map { c -> FilterOption(c.id, c.name) },
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isSearchingCategory = false, categorySuggestions = emptyList()) }
            }
            is Resource.Loading -> {}
        }
    }

    private suspend fun searchExpenses(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(expenseSuggestions = emptyList(), isSearchingExpense = false) }
            return
        }
        _uiState.update { it.copy(isSearchingExpense = true) }
        when (val result = budgetRepository.searchExpenses(query)) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isSearchingExpense = false,
                        expenseSuggestions = result.data.map { e -> FilterOption(e.id, e.name) },
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isSearchingExpense = false, expenseSuggestions = emptyList()) }
            }
            is Resource.Loading -> {}
        }
    }

    private suspend fun searchContracts(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(contractSuggestions = emptyList(), isSearchingContract = false) }
            return
        }
        _uiState.update { it.copy(isSearchingContract = true) }
        when (val result = contractRepository.searchContracts(query)) {
            is Resource.Success -> {
                _uiState.update {
                    it.copy(
                        isSearchingContract = false,
                        contractSuggestions = result.data.map { c -> FilterOption(c.id, c.name) },
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isSearchingContract = false, contractSuggestions = emptyList()) }
            }
            is Resource.Loading -> {}
        }
    }

    private fun loadTransactionForEdit(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = transactionRepository.getTransaction(id)) {
                is Resource.Success -> {
                    val tx = result.data
                    val sourceKind = TransactionFormUiState.inputKindForSource(tx.type)
                    val targetKind = TransactionFormUiState.inputKindForTarget(tx.type)
                    _uiState.update { state ->
                        val sourceSelected = if (
                            sourceKind != AccountInputKind.OWNED_DROPDOWN &&
                            tx.sourceAccountId != null
                        ) {
                            FilterOption(tx.sourceAccountId, tx.sourceAccountName)
                        } else {
                            null
                        }
                        val targetSelected = if (
                            targetKind != AccountInputKind.OWNED_DROPDOWN &&
                            tx.destinationAccountId != null
                        ) {
                            FilterOption(tx.destinationAccountId, tx.destinationAccountName)
                        } else {
                            null
                        }
                        state.copy(
                            isLoading = false,
                            type = tx.type,
                            description = tx.description,
                            amount = tx.amount.toString(),
                            currency = tx.currency,
                            date = tx.date,
                            sourceAccountId = tx.sourceAccountId,
                            sourceSelected = sourceSelected,
                            sourceQuery = tx.sourceAccountName,
                            targetAccountId = tx.destinationAccountId,
                            targetSelected = targetSelected,
                            targetQuery = tx.destinationAccountName,
                            categoryQuery = tx.categoryName.orEmpty(),
                            expenseQuery = tx.budgetName.orEmpty(),
                            contractQuery = tx.contractName.orEmpty(),
                            tags = tx.tags,
                            moreOptionsExpanded = tx.tags.isNotEmpty() ||
                                tx.categoryName != null ||
                                tx.budgetName != null ||
                                tx.contractName != null,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadOwnedAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = accountRepository.refreshOwnedAccounts()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(ownedAccounts = result.data) }
                    if (editTransactionId == null) {
                        applyLastTransactionType()
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private suspend fun applyLastTransactionType() {
        val lastType = userPreferences.getLastTransactionType()
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                type = lastType ?: state.type,
            )
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
