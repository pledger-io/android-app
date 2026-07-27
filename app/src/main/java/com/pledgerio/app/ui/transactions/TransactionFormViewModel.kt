package com.pledgerio.app.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeCodes
import com.pledgerio.app.domain.model.CreateOutcome
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionSplit
import com.pledgerio.app.domain.model.TransactionTemplate
import com.pledgerio.app.domain.model.TransactionType
import java.util.UUID
import kotlin.math.abs
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.BudgetRepository
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.domain.repository.ContractRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TagRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.ui.transactions.form.splitValidationIssue
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SearchDefaults
import com.pledgerio.app.util.TransactionTemplateStore
import com.pledgerio.app.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

data class TransactionSplitLineUi(
    val id: String,
    val description: String = "",
    val amount: String = "",
)

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
    val savedOffline: Boolean = false,
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
    val tagSuggestions: List<String> = emptyList(),
    val isSearchingTags: Boolean = false,
    val isAddingTag: Boolean = false,
    val tagError: String? = null,
    val catalogTagNames: Set<String> = emptySet(),
    val templates: List<TransactionTemplate> = emptyList(),
    val showSaveTemplateDialog: Boolean = false,
    val saveTemplateName: String = "",
    val splitSectionExpanded: Boolean = false,
    val splitLines: List<TransactionSplitLineUi> = emptyList(),
    val originalSplitSnapshot: List<TransactionSplit> = emptyList(),
    val financeExperienceMode: FinanceExperienceMode = FinanceExperienceMode.GUIDED,
    val moreOptionsManuallyToggled: Boolean = false,
    val isAutoClassifying: Boolean = false,
    val autoClassifyStatus: com.pledgerio.app.ui.transactions.form.AutoClassifyStatus? = null,
) {
    val splitTotal: Double
        get() = splitLines.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

    val splitRemaining: Double
        get() = (amount.toDoubleOrNull() ?: 0.0) - splitTotal

    val hasSplitChanges: Boolean
        get() = splitLines.toDomainSplits() != originalSplitSnapshot
    val sourceInputKind: AccountInputKind
        get() = inputKindForSource(type)

    val targetInputKind: AccountInputKind
        get() = inputKindForTarget(type)

    val showTemplatesSection: Boolean
        get() = !isEditing && financeExperienceMode == FinanceExperienceMode.POWER
    val canAutoClassify: Boolean
        get() = !isEditing && (description.isNotBlank() || amount.toDoubleOrNull() != null)

    val isValid: Boolean
        get() = description.isNotBlank()
            && amount.toDoubleOrNull()?.let { it > 0 } == true
            && sourceAccountId != null
            && targetAccountId != null

    val canSubmit: Boolean
        get() = isValid &&
            splitValidationIssue() == null &&
            when {
                sourceInputKind == AccountInputKind.OWNED_DROPDOWN && ownedAccounts.isEmpty() -> false
                targetInputKind == AccountInputKind.OWNED_DROPDOWN && ownedAccounts.isEmpty() -> false
                else -> true
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

private data class PrefillDraft(
    val description: String?,
    val amount: String?,
    val currency: String?,
    val date: LocalDate?,
    val type: TransactionType?,
    val sourceName: String?,
    val targetName: String?,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val contractRepository: ContractRepository,
    private val tagRepository: TagRepository,
    private val userPreferences: UserPreferences,
    private val transactionTemplateStore: TransactionTemplateStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editTransactionId: Long? = savedStateHandle.get<Long>("transactionId")
    private val prefillDraft: PrefillDraft? = if (editTransactionId == null) {
        savedStateHandle.toPrefillDraft()
    } else {
        null
    }

    private val _uiState = MutableStateFlow(
        TransactionFormUiState(
            isEditing = editTransactionId != null,
            editingTransactionId = editTransactionId,
        ),
    )
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    private var sourceSearchJob: Job? = null
    private var targetSearchJob: Job? = null
    private var tagCatalogLoadJob: Job? = null

    private val categoryQueryFlow = MutableStateFlow("")
    private val expenseQueryFlow = MutableStateFlow("")
    private val contractQueryFlow = MutableStateFlow("")
    private val tagQueryFlow = MutableStateFlow("")

    init {
        observeExperienceMode()
        loadOwnedAccounts()
        loadCurrencies()
        observeTemplates()
        observeSuggestions(
            queryFlow = categoryQueryFlow,
            sourceFlow = { query -> categoryRepository.observeMatching(query).mapToOptions { it.id to it.name } },
            onLoading = { _uiState.update { state -> state.copy(isSearchingCategory = true) } },
            onResult = { options ->
                _uiState.update { state -> state.copy(isSearchingCategory = false, categorySuggestions = options) }
            },
        )
        observeSuggestions(
            queryFlow = expenseQueryFlow,
            sourceFlow = { query -> budgetRepository.observeExpenseGroups(query).mapToOptions { it.id to it.name } },
            onLoading = { _uiState.update { state -> state.copy(isSearchingExpense = true) } },
            onResult = { options ->
                _uiState.update { state -> state.copy(isSearchingExpense = false, expenseSuggestions = options) }
            },
        )
        observeSuggestions(
            queryFlow = contractQueryFlow,
            sourceFlow = { query -> contractRepository.observeMatching(query).mapToOptions { it.id to it.name } },
            onLoading = { _uiState.update { state -> state.copy(isSearchingContract = true) } },
            onResult = { options ->
                _uiState.update { state -> state.copy(isSearchingContract = false, contractSuggestions = options) }
            },
        )
        observeTagSuggestions()
        if (editTransactionId != null) {
            loadTransactionForEdit(editTransactionId)
        }
    }

    private fun observeSuggestions(
        queryFlow: MutableStateFlow<String>,
        sourceFlow: (String) -> Flow<List<FilterOption>>,
        onLoading: () -> Unit,
        onResult: (List<FilterOption>) -> Unit,
    ) {
        viewModelScope.launch {
            queryFlow
                .debounce(SearchDefaults.DEBOUNCE_MS)
                .distinctUntilChanged()
                .onEach { if (it.isNotBlank()) onLoading() }
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(emptyList()) else sourceFlow(query)
                }
                .collect(onResult)
        }
    }

    private inline fun <T> Flow<List<T>>.mapToOptions(
        crossinline extract: (T) -> Pair<Long, String>,
    ): Flow<List<FilterOption>> = map { items ->
        items.map { item ->
            val (id, label) = extract(item)
            FilterOption(id, label)
        }
    }

    private fun observeTagSuggestions() {
        viewModelScope.launch {
            tagQueryFlow
                .debounce(SearchDefaults.DEBOUNCE_MS)
                .distinctUntilChanged()
                .onEach { query ->
                    if (query.isNotBlank()) {
                        _uiState.update { it.copy(isSearchingTags = true) }
                    }
                }
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        tagRepository.observeMatching(query).map { tags -> tags.map { it.name } }
                    }
                }
                .collect { names -> applyTagSuggestions(names) }
        }
    }

    private fun applyTagSuggestions(names: List<String>) {
        _uiState.update { state ->
            val filtered = names.filter { name ->
                state.tags.none { selected -> selected.equals(name, ignoreCase = true) }
            }
            if (!state.isSearchingTags && state.tagSuggestions == filtered) {
                state
            } else {
                state.copy(isSearchingTags = false, tagSuggestions = filtered)
            }
        }
    }

    fun ensureTagCatalogLoaded() {
        if (_uiState.value.catalogTagNames.isNotEmpty()) {
            if (tagQueryFlow.value.isBlank()) {
                applyTagSuggestions(_uiState.value.catalogTagNames.sorted())
            }
            return
        }
        if (tagCatalogLoadJob?.isActive == true) return
        tagCatalogLoadJob = viewModelScope.launch {
            when (val result = tagRepository.refreshTags()) {
                is Resource.Success -> {
                    val names = result.data.map { it.name }
                    _uiState.update { it.copy(catalogTagNames = names.toSet()) }
                    if (tagQueryFlow.value.isBlank()) {
                        applyTagSuggestions(names)
                    }
                }
                is Resource.Error, is Resource.Loading -> Unit
            }
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
        _uiState.update { it.copy(description = value, error = null, autoClassifyStatus = null) }
    }

    fun onAmountChanged(value: String) {
        _uiState.update { it.copy(amount = value, error = null, autoClassifyStatus = null) }
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

            val source = preserveAccountSelection(
                accountId = current.sourceAccountId,
                selected = current.sourceSelected,
                query = current.sourceQuery,
                ownedAccounts = current.ownedAccounts,
                oldKind = oldSourceKind,
                newKind = newSourceKind,
            )
            val target = preserveAccountSelection(
                accountId = current.targetAccountId,
                selected = current.targetSelected,
                query = current.targetQuery,
                ownedAccounts = current.ownedAccounts,
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
        val willExpand = !_uiState.value.moreOptionsExpanded
        _uiState.update {
            it.copy(
                moreOptionsExpanded = willExpand,
                moreOptionsManuallyToggled = true,
            )
        }
        if (willExpand) {
            ensureTagCatalogLoaded()
        }
    }

    fun toggleSplitSection() {
        _uiState.update { it.copy(splitSectionExpanded = !it.splitSectionExpanded) }
    }

    fun addSplitLine() {
        _uiState.update {
            it.copy(
                splitLines = it.splitLines + TransactionSplitLineUi(id = UUID.randomUUID().toString()),
                splitSectionExpanded = true,
            )
        }
    }

    fun removeSplitLine(lineId: String) {
        _uiState.update { it.copy(splitLines = it.splitLines.filterNot { line -> line.id == lineId }) }
    }

    fun onSplitLineDescriptionChanged(lineId: String, value: String) {
        _uiState.update { state ->
            state.copy(
                splitLines = state.splitLines.map { line ->
                    if (line.id == lineId) line.copy(description = value) else line
                },
            )
        }
    }

    fun onSplitLineAmountChanged(lineId: String, value: String) {
        _uiState.update { state ->
            state.copy(
                splitLines = state.splitLines.map { line ->
                    if (line.id == lineId) line.copy(amount = value) else line
                },
            )
        }
    }

    fun onTagInputChanged(value: String) {
        if (value.endsWith(",")) {
            val tag = value.dropLast(1).trim()
            if (tag.isNotEmpty()) addTag(tag)
            else {
                _uiState.update { it.copy(tagInput = "", tagError = null) }
                tagQueryFlow.value = ""
            }
        } else {
            _uiState.update { it.copy(tagInput = value, tagError = null) }
            tagQueryFlow.value = value
        }
    }

    fun addTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return

        val state = _uiState.value
        if (state.tags.any { it.equals(tag, ignoreCase = true) }) {
            _uiState.update {
                it.copy(
                    tagInput = "",
                    tagError = null,
                    tagSuggestions = filterTagSuggestions(it.tagSuggestions, it.tags),
                )
            }
            tagQueryFlow.value = ""
            return
        }

        if (state.catalogTagNames.any { it.equals(tag, ignoreCase = true) }) {
            appendTagToForm(tag)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTag = true, tagError = null) }
            when (val result = tagRepository.createTag(tag)) {
                is Resource.Success -> appendTagToForm(result.data.name)
                is Resource.Error -> {
                    _uiState.update { it.copy(isAddingTag = false, tagError = result.message) }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun selectTagFromSuggestion(name: String) {
        appendTagToForm(name.trim())
    }

    private fun appendTagToForm(tag: String) {
        if (tag.isEmpty()) return
        _uiState.update { state ->
            if (state.tags.any { it.equals(tag, ignoreCase = true) }) {
                state.copy(tagInput = "", tagError = null, isAddingTag = false)
            } else {
                val newTags = state.tags + tag
                state.copy(
                    tags = newTags,
                    tagInput = "",
                    tagError = null,
                    isAddingTag = false,
                    tagSuggestions = filterTagSuggestions(state.tagSuggestions, newTags),
                )
            }
        }
        tagQueryFlow.value = ""
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            val newTags = state.tags.filterNot { existing -> existing == tag }
            state.copy(
                tags = newTags,
                tagSuggestions = filterTagSuggestions(state.tagSuggestions, newTags, reinclude = tag),
            )
        }
    }

    private fun filterTagSuggestions(
        suggestions: List<String>,
        selected: List<String>,
        reinclude: String? = null,
    ): List<String> {
        val filtered = suggestions.filter { name ->
            selected.none { it.equals(name, ignoreCase = true) }
        }
        return if (reinclude != null && filtered.none { it.equals(reinclude, ignoreCase = true) }) {
            filtered + reinclude
        } else {
            filtered
        }
    }

    fun showSaveTemplateDialog(defaultNameIfBlank: String) {
        val state = _uiState.value
        val defaultName = state.description.trim().ifBlank { defaultNameIfBlank }
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
                sourceAccountName = resolveAccountDisplayName(
                    state.sourceAccountId,
                    state.sourceSelected,
                    state.ownedAccounts,
                ),
                targetAccountId = state.targetAccountId,
                targetAccountName = resolveAccountDisplayName(
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
                    moreOptionsManuallyToggled = template.tags.isNotEmpty(),
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
        _uiState.update { it.copy(sourceQuery = query, error = null, autoClassifyStatus = null) }
        sourceSearchJob?.cancel()
        sourceSearchJob = viewModelScope.launch {
            delay(SearchDefaults.DEBOUNCE_MS)
            searchSourceAccounts(query)
        }
    }

    fun onTargetQueryChanged(query: String) {
        _uiState.update { it.copy(targetQuery = query, error = null, autoClassifyStatus = null) }
        targetSearchJob?.cancel()
        targetSearchJob = viewModelScope.launch {
            delay(SearchDefaults.DEBOUNCE_MS)
            searchTargetAccounts(query)
        }
    }

    fun onCategoryQueryChanged(query: String) {
        _uiState.update { it.copy(categoryQuery = query) }
        categoryQueryFlow.value = query
    }

    fun onExpenseQueryChanged(query: String) {
        _uiState.update { it.copy(expenseQuery = query) }
        expenseQueryFlow.value = query
    }

    fun onContractQueryChanged(query: String) {
        _uiState.update { it.copy(contractQuery = query) }
        contractQueryFlow.value = query
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

    fun autoClassify() {
        val state = _uiState.value
        if (state.isEditing || state.isAutoClassifying) return

        val amount = state.amount.toDoubleOrNull()
        val description = state.description.trim().ifBlank { null }
        if (description == null && amount == null) {
            _uiState.update {
                it.copy(autoClassifyStatus = com.pledgerio.app.ui.transactions.form.AutoClassifyStatus.NeedInput)
            }
            return
        }

        val sourceName = resolveAccountDisplayName(
            accountId = state.sourceAccountId,
            selected = state.sourceSelected,
            ownedAccounts = state.ownedAccounts,
        ).ifBlank { state.sourceQuery.trim() }.takeIf { it.isNotBlank() }
        val targetName = resolveAccountDisplayName(
            accountId = state.targetAccountId,
            selected = state.targetSelected,
            ownedAccounts = state.ownedAccounts,
        ).ifBlank { state.targetQuery.trim() }.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _uiState.update { it.copy(isAutoClassifying = true, autoClassifyStatus = null) }
            when (
                val result = transactionRepository.suggestClassifications(
                    amount = amount,
                    description = description,
                    source = sourceName,
                    destination = targetName,
                )
            ) {
                is Resource.Success -> {
                    val suggestion = result.data
                    val suggestedCategory = suggestion.category
                    val suggestedExpense = suggestion.budget

                    val categoryOption = suggestedCategory
                        .orEmpty()
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { resolveCategoryOptionByName(it) }
                    val expenseOption = suggestedExpense
                        .orEmpty()
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { resolveExpenseOptionByName(it) }
                    val applyResult = applyAutoClassifySuggestion(
                        current = _uiState.value,
                        suggestedCategoryRaw = suggestedCategory,
                        suggestedExpenseRaw = suggestedExpense,
                        suggestedTagsRaw = suggestion.tags,
                        categoryOption = categoryOption,
                        expenseOption = expenseOption,
                    )
                    _uiState.update { applyResult.updatedState }
                    applyResult.unresolvedCategoryQuery?.let { categoryQueryFlow.value = it }
                    applyResult.unresolvedExpenseQuery?.let { expenseQueryFlow.value = it }
                }

                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isAutoClassifying = false,
                            autoClassifyStatus = com.pledgerio.app.ui.transactions.form.AutoClassifyStatus.Error(
                                result.message ?: "",
                            ),
                        )
                    }
                }

                is Resource.Loading -> Unit
            }
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
                autoClassifyStatus = null,
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
                autoClassifyStatus = null,
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
                autoClassifyStatus = null,
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
                autoClassifyStatus = null,
            )
        }
    }

    fun submit() {
        _uiState.update { it.copy(validationAttempted = true) }
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val sourceName = resolveAccountDisplayName(
                accountId = state.sourceAccountId,
                selected = state.sourceSelected,
                ownedAccounts = state.ownedAccounts,
            )
            val targetName = resolveAccountDisplayName(
                accountId = state.targetAccountId,
                selected = state.targetSelected,
                ownedAccounts = state.ownedAccounts,
            )
            val categoryId = resolveCategoryId(state)
            val expenseId = resolveExpenseId(state)
            val contractId = resolveContractId(state)
            val transaction = buildTransactionForSubmit(
                state = state,
                sourceName = sourceName,
                targetName = targetName,
                categoryId = categoryId,
                expenseId = expenseId,
                contractId = contractId,
            )

            if (state.isEditing && state.editingTransactionId != null) {
                val transactionId = state.editingTransactionId
                when (val putResult = transactionRepository.updateTransaction(transaction)) {
                    is Resource.Success -> {
                        if (state.hasSplitChanges) {
                            when (
                                val patchResult = transactionRepository.patchTransactionSplits(
                                    transactionId,
                                    state.splitLines.toDomainSplits(),
                                )
                            ) {
                                is Resource.Success -> finishSave(state.type)
                                is Resource.Error -> {
                                    _uiState.update {
                                        it.copy(isSaving = false, error = patchResult.message)
                                    }
                                }
                                is Resource.Loading -> {}
                            }
                        } else {
                            finishSave(state.type)
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSaving = false, error = putResult.message) }
                    }
                    is Resource.Loading -> {}
                }
            } else {
                when (val result = transactionRepository.createTransactionOrEnqueue(transaction)) {
                    is Resource.Success -> when (val outcome = result.data) {
                        is CreateOutcome.Synced -> finishSave(state.type, savedOffline = false)
                        is CreateOutcome.Queued -> finishSave(state.type, savedOffline = true)
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSaving = false, error = result.message) }
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    private suspend fun finishSave(type: TransactionType, savedOffline: Boolean = false) {
        userPreferences.setLastTransactionType(type)
        _uiState.update {
            it.copy(isSaving = false, saveSuccess = true, savedOffline = savedOffline)
        }
    }

    private suspend fun resolveCategoryOptionByName(name: String): FilterOption? {
        return when (val result = categoryRepository.searchCategories(name)) {
            is Resource.Success -> {
                val options = result.data.map { FilterOption(id = it.id, label = it.name) }
                bestMatchOption(name = name, options = options)
            }

            is Resource.Error, is Resource.Loading -> null
        }
    }

    private suspend fun resolveExpenseOptionByName(name: String): FilterOption? {
        return when (val result = budgetRepository.searchExpenses(name)) {
            is Resource.Success -> {
                val options = result.data.map { FilterOption(id = it.id, label = it.name) }
                bestMatchOption(name = name, options = options)
            }

            is Resource.Error, is Resource.Loading -> null
        }
    }

    private suspend fun resolveContractOptionByName(name: String): FilterOption? {
        return when (val result = contractRepository.searchContracts(name)) {
            is Resource.Success -> {
                val options = result.data.map { FilterOption(id = it.id, label = it.name) }
                bestMatchOption(name = name, options = options)
            }

            is Resource.Error, is Resource.Loading -> null
        }
    }

    private suspend fun resolveCategoryId(state: TransactionFormUiState): Long? {
        return resolveOptionalSelectionId(
            selected = state.categorySelected,
            query = state.categoryQuery,
            resolveByName = ::resolveCategoryOptionByName,
        )
    }

    private suspend fun resolveExpenseId(state: TransactionFormUiState): Long? {
        return resolveOptionalSelectionId(
            selected = state.expenseSelected,
            query = state.expenseQuery,
            resolveByName = ::resolveExpenseOptionByName,
        )
    }

    private suspend fun resolveContractId(state: TransactionFormUiState): Long? {
        return resolveOptionalSelectionId(
            selected = state.contractSelected,
            query = state.contractQuery,
            resolveByName = ::resolveContractOptionByName,
        )
    }

    private fun bestMatchOption(name: String, options: List<FilterOption>): FilterOption? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        return options.firstOrNull { it.label.equals(trimmed, ignoreCase = true) }
            ?: options.firstOrNull { it.label.contains(trimmed, ignoreCase = true) }
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
            _uiState.update { clearCounterpartySearchState(it, isSource = isSource) }
            return
        }

        _uiState.update { markCounterpartySearchInProgress(it, isSource = isSource) }

        when (val result = accountRepository.searchAccounts(typeCode, query)) {
            is Resource.Success -> {
                val options = result.data.map { account -> FilterOption(account.id, account.name) }
                _uiState.update {
                    applyCounterpartySearchSuccess(it, isSource = isSource, options = options)
                }
            }
            is Resource.Error -> {
                _uiState.update { clearCounterpartySearchState(it, isSource = isSource) }
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
                    val categorySelected = tx.categoryName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { resolveCategoryOptionByName(it) }
                    val expenseSelected = tx.budgetName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { resolveExpenseOptionByName(it) }
                    val contractSelected = tx.contractName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { resolveContractOptionByName(it) }
                    val sourceKind = TransactionFormUiState.inputKindForSource(tx.type)
                    val targetKind = TransactionFormUiState.inputKindForTarget(tx.type)
                    _uiState.update { state ->
                        buildStateAfterEditLoad(
                            current = state,
                            tx = tx,
                            sourceKind = sourceKind,
                            targetKind = targetKind,
                            categorySelected = categorySelected,
                            expenseSelected = expenseSelected,
                            contractSelected = contractSelected,
                            splitLines = tx.split.toSplitLineUi(),
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
                        applyPrefillIfPresent()
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

    private fun applyPrefillIfPresent() {
        val prefill = prefillDraft ?: return
        _uiState.update { state ->
            val type = prefill.type ?: state.type
            state.copy(
                type = type,
                description = prefill.description ?: state.description,
                amount = prefill.amount ?: state.amount,
                currency = prefill.currency
                    ?.takeIf { state.currencies.contains(it) }
                    ?: state.currency,
                date = prefill.date ?: state.date,
                sourceAccountId = null,
                sourceSelected = null,
                sourceQuery = prefill.sourceName.orEmpty(),
                sourceSuggestions = emptyList(),
                targetAccountId = null,
                targetSelected = null,
                targetQuery = prefill.targetName.orEmpty(),
                targetSuggestions = emptyList(),
                error = null,
            )
        }

        viewModelScope.launch {
            val current = _uiState.value
            if (
                current.sourceInputKind != AccountInputKind.OWNED_DROPDOWN &&
                current.sourceQuery.isNotBlank()
            ) {
                searchSourceAccounts(current.sourceQuery)
            }
            if (
                current.targetInputKind != AccountInputKind.OWNED_DROPDOWN &&
                current.targetQuery.isNotBlank()
            ) {
                searchTargetAccounts(current.targetQuery)
            }
        }
    }

    private fun observeExperienceMode() {
        viewModelScope.launch {
            userPreferences.financeExperienceMode.collect { mode ->
                val previous = _uiState.value
                val shouldExpandMoreOptions = resolveMoreOptionsExpansion(previous, mode)
                if (
                    previous.financeExperienceMode == mode &&
                    previous.moreOptionsExpanded == shouldExpandMoreOptions
                ) {
                    return@collect
                }
                _uiState.update {
                    it.copy(
                        financeExperienceMode = mode,
                        moreOptionsExpanded = shouldExpandMoreOptions,
                    )
                }
                if (shouldExpandMoreOptions && !previous.moreOptionsExpanded) {
                    ensureTagCatalogLoaded()
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

    private fun List<TransactionSplit>.toSplitLineUi(): List<TransactionSplitLineUi> {
        return map { split ->
            TransactionSplitLineUi(
                id = UUID.randomUUID().toString(),
                description = split.description,
                amount = split.amount.toString(),
            )
        }
    }

}

internal fun List<TransactionSplitLineUi>.toDomainSplits(): List<TransactionSplit> {
    return mapNotNull { line ->
        val amountValue = line.amount.toDoubleOrNull() ?: return@mapNotNull null
        val description = line.description.trim()
        if (description.isBlank()) return@mapNotNull null
        TransactionSplit(description = description, amount = amountValue)
    }
}

private fun SavedStateHandle.toPrefillDraft(): PrefillDraft? {
    val description = get<String>("prefillDescription")?.trim().orEmpty().ifBlank { null }
    val amount = get<String>("prefillAmount")?.trim().orEmpty().ifBlank { null }
    val currency = get<String>("prefillCurrency")?.trim().orEmpty().ifBlank { null }
    val date = get<String>("prefillDate")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
    val type = get<String>("prefillType")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::toTransactionTypeOrNull)
    val source = get<String>("prefillSource")?.trim().orEmpty().ifBlank { null }
    val target = get<String>("prefillTarget")?.trim().orEmpty().ifBlank { null }

    return if (
        description == null &&
        amount == null &&
        currency == null &&
        date == null &&
        type == null &&
        source == null &&
        target == null
    ) {
        null
    } else {
        PrefillDraft(
            description = description,
            amount = amount,
            currency = currency,
            date = date,
            type = type,
            sourceName = source,
            targetName = target,
        )
    }
}

private fun toTransactionTypeOrNull(raw: String): TransactionType? {
    return when (raw.uppercase()) {
        "CREDIT", "EXPENSE" -> TransactionType.CREDIT
        "DEBIT", "INCOME" -> TransactionType.DEBIT
        "TRANSFER" -> TransactionType.TRANSFER
        else -> null
    }
}
