package com.pledgerio.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.domain.model.AccountTypeOption
import com.pledgerio.app.domain.model.Currency
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.util.Resource
import android.content.Context
import com.pledgerio.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.pledgerio.app.ui.transactions.finiteDoubleOrNull
import com.pledgerio.app.ui.transactions.positiveMoneyOrNull
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

const val INTEREST_PERIODICITY_MONTHS = "MONTHS"
const val INTEREST_PERIODICITY_YEARS = "YEARS"

/** Formats a fractional rate (0.035) as an editable percentage ("3.5"). */
internal fun formatInterestPercent(fraction: Double): String =
    BigDecimal.valueOf(fraction).movePointRight(2).stripTrailingZeros().toPlainString()

data class AccountFormUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val name: String = "",
    val description: String = "",
    val typeCode: String = "default",
    val currency: String = "EUR",
    val iban: String = "",
    val bic: String = "",
    val accountNumber: String = "",
    /** Interest rate as entered, in percent. */
    val interestPercent: String = "",
    val interestPeriodicity: String = INTEREST_PERIODICITY_MONTHS,
    val openingAmount: String = "",
    val openingDate: LocalDate = LocalDate.now(),
    val showOpeningDatePicker: Boolean = false,
    val availableCurrencies: List<Currency> = emptyList(),
    val ownedAccountTypes: List<AccountTypeOption> = emptyList(),
    val counterpartyAccountTypes: List<AccountTypeOption> = emptyList(),
) {
    val isLiability: Boolean get() = AccountTypeCatalog.isLiability(typeCode)

    /** The starting amount is only recorded on creation, mirroring the web UI. */
    val showOpeningAmount: Boolean get() = isLiability && !isEditing

    val interestError: Boolean
        get() = isLiability && interestPercent.isNotBlank() &&
            interestPercent.trim().finiteDoubleOrNull()?.takeIf { it >= 0.0 } == null

    val openingAmountError: Boolean
        get() = showOpeningAmount && openingAmount.isNotBlank() &&
            openingAmount.trim().positiveMoneyOrNull() == null

    val isValid: Boolean
        get() = name.isNotBlank() && currency.isNotBlank() && typeCode.isNotBlank() &&
            !interestError && !openingAmountError

    val allAccountTypes: List<AccountTypeOption>
        get() = ownedAccountTypes + counterpartyAccountTypes

    val typeMetadata get() = AccountTypeCatalog.metadataFor(typeCode)

    val ownedPickerEntries: List<AccountTypePickerEntry>
        get() = AccountTypePicker.ownedPickerEntries(
            ownedAccountTypes.filterNot { AccountTypeCatalog.isLiability(it.code) },
        )

    val liabilityAccountTypes: List<AccountTypeOption>
        get() = ownedAccountTypes.filter { AccountTypeCatalog.isLiability(it.code) }

    val typeVariantChoice: AccountTypeVariantChoice?
        get() = AccountTypePicker.variantChoice(typeCode, ownedAccountTypes)
}

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private var accountId: Long? = savedStateHandle.get<Long>("accountId")
    private val preselectedType: String = savedStateHandle.get<String>("type").orEmpty()

    private val _uiState = MutableStateFlow(
        AccountFormUiState(
            isEditing = accountId != null,
            typeCode = preselectedType.ifBlank { "default" },
        ),
    )
    val uiState: StateFlow<AccountFormUiState> = _uiState.asStateFlow()

    private var pendingAccountSync: Account? = null

    /** Holds server-owned fields of the edited account that the form does not expose. */
    private var loadedAccount: Account? = null

    fun peekPendingAccountSync(): Account? =
        pendingAccountSync.also { pendingAccountSync = null }

    init {
        loadCurrencies()
        loadAccountTypes()
        accountId?.let(::loadAccount)
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onTypeChanged(typeCode: String) {
        _uiState.update { it.copy(typeCode = typeCode) }
    }

    fun onOwnershipVariantChanged(joint: Boolean) {
        val variant = _uiState.value.typeVariantChoice ?: return
        val code = if (joint) variant.jointTypeCode else variant.soloTypeCode
        onTypeChanged(code)
    }

    fun onCurrencyChanged(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun onIbanChanged(iban: String) {
        _uiState.update { it.copy(iban = iban) }
    }

    fun onBicChanged(bic: String) {
        _uiState.update { it.copy(bic = bic) }
    }

    fun onAccountNumberChanged(number: String) {
        _uiState.update { it.copy(accountNumber = number) }
    }

    fun onInterestChanged(percent: String) {
        _uiState.update { it.copy(interestPercent = percent) }
    }

    fun onInterestPeriodicityChanged(periodicity: String) {
        _uiState.update { it.copy(interestPeriodicity = periodicity) }
    }

    fun onOpeningAmountChanged(amount: String) {
        _uiState.update { it.copy(openingAmount = amount) }
    }

    fun showOpeningDatePicker() {
        _uiState.update { it.copy(showOpeningDatePicker = true) }
    }

    fun dismissOpeningDatePicker() {
        _uiState.update { it.copy(showOpeningDatePicker = false) }
    }

    fun onOpeningDateChanged(date: LocalDate) {
        _uiState.update { it.copy(openingDate = date, showOpeningDatePicker = false) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.saveSuccess) return
        if (state.name.isBlank() || state.currency.isBlank() || state.typeCode.isBlank()) {
            _uiState.update {
                it.copy(error = context.getString(R.string.account_error_name_required))
            }
            return
        }
        if (!state.isValid) {
            _uiState.update {
                it.copy(error = context.getString(R.string.account_error_invalid_number))
            }
            return
        }
        // Claimed before suspending so a second tap in the same frame cannot start a second save.
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            val interest = if (state.isLiability) {
                state.interestPercent.trim().finiteDoubleOrNull()?.let { it / 100.0 } ?: 0.0
            } else {
                loadedAccount?.interest
            }
            val account = Account(
                id = accountId ?: 0L,
                name = state.name.trim(),
                description = state.description.trim(),
                currency = state.currency,
                typeCode = state.typeCode,
                iban = state.iban.trim().ifBlank { null },
                bic = state.bic.trim().ifBlank { null },
                openingBalance = loadedAccount?.openingBalance ?: 0.0,
                accountNumber = if (state.isLiability) {
                    state.accountNumber.trim().ifBlank { null }
                } else {
                    loadedAccount?.accountNumber
                },
                interest = interest,
                interestPeriodicity = if (state.isLiability) {
                    state.interestPeriodicity
                } else {
                    loadedAccount?.interestPeriodicity
                },
            )

            val result = if (state.isEditing) {
                accountRepository.updateAccount(account)
            } else {
                accountRepository.createAccount(account)
            }

            when (result) {
                is Resource.Success -> {
                    pendingAccountSync = result.data
                    val openingAmount = state.openingAmount.trim().positiveMoneyOrNull()
                    if (state.showOpeningAmount && openingAmount != null) {
                        val opening = accountRepository.recordOpeningBalance(
                            account = result.data,
                            amount = openingAmount,
                            date = state.openingDate,
                        )
                        if (opening is Resource.Error) {
                            // The account exists now: stay on the form in edit mode so a retry cannot create it twice.
                            accountId = result.data.id
                            loadedAccount = result.data
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    isEditing = true,
                                    error = context.getString(
                                        R.string.account_error_opening_balance_failed,
                                        opening.message,
                                    ),
                                )
                            }
                            return@launch
                        }
                    }
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadCurrencies() {
        viewModelScope.launch {
            val currencies = currencyRepository.getCurrencies().first()
            _uiState.update { it.copy(availableCurrencies = currencies) }
        }
    }

    private fun loadAccountTypes() {
        viewModelScope.launch {
            when (val result = accountRepository.getAccountTypes()) {
                is Resource.Success -> {
                    val owned = result.data.filter { !it.isCounterparty }
                    val counterparty = result.data.filter { it.isCounterparty }
                    _uiState.update {
                        val typeCode = when {
                            it.isEditing -> it.typeCode
                            preselectedType.isNotBlank() -> preselectedType
                            it.typeCode.isNotBlank() -> it.typeCode
                            else -> owned.firstOrNull()?.code ?: "default"
                        }
                        it.copy(
                            ownedAccountTypes = owned,
                            counterpartyAccountTypes = counterparty,
                            typeCode = typeCode,
                        )
                    }
                }
                is Resource.Error -> {}
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadAccount(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = accountRepository.getAccount(id)) {
                is Resource.Success -> {
                    val account = result.data
                    loadedAccount = account
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = account.name,
                            description = account.description,
                            typeCode = account.typeCode,
                            currency = account.currency,
                            iban = account.iban ?: "",
                            bic = account.bic ?: "",
                            accountNumber = account.accountNumber ?: "",
                            interestPercent = account.interest
                                ?.takeIf { account.isLiability }
                                ?.let { formatInterestPercent(it) }
                                ?: "",
                            interestPeriodicity = account.interestPeriodicity
                                ?: INTEREST_PERIODICITY_MONTHS,
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
}
