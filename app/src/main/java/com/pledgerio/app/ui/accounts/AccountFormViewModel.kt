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
import javax.inject.Inject

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
    val openingBalance: String = "0.00",
    val availableCurrencies: List<Currency> = emptyList(),
    val ownedAccountTypes: List<AccountTypeOption> = emptyList(),
    val counterpartyAccountTypes: List<AccountTypeOption> = emptyList(),
) {
    val isValid: Boolean
        get() = name.isNotBlank() && currency.isNotBlank() && typeCode.isNotBlank()

    val allAccountTypes: List<AccountTypeOption>
        get() = ownedAccountTypes + counterpartyAccountTypes

    val typeMetadata get() = AccountTypeCatalog.metadataFor(typeCode)

    val ownedPickerEntries: List<AccountTypePickerEntry>
        get() = AccountTypePicker.ownedPickerEntries(ownedAccountTypes)

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

    private val accountId: Long? = savedStateHandle.get<Long>("accountId")
    private val preselectedType: String = savedStateHandle.get<String>("type").orEmpty()

    private val _uiState = MutableStateFlow(
        AccountFormUiState(
            isEditing = accountId != null,
            typeCode = preselectedType.ifBlank { "default" },
        ),
    )
    val uiState: StateFlow<AccountFormUiState> = _uiState.asStateFlow()

    private var pendingAccountSync: Account? = null

    fun peekPendingAccountSync(): Account? =
        pendingAccountSync.also { pendingAccountSync = null }

    init {
        loadCurrencies()
        loadAccountTypes()
        if (accountId != null) {
            loadAccount(accountId)
        }
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

    fun onOpeningBalanceChanged(balance: String) {
        _uiState.update { it.copy(openingBalance = balance) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.isValid) {
            _uiState.update {
                it.copy(error = context.getString(R.string.account_error_name_required))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val account = Account(
                id = accountId ?: 0L,
                name = state.name.trim(),
                description = state.description.trim(),
                currency = state.currency,
                typeCode = state.typeCode,
                iban = state.iban.trim().ifBlank { null },
                bic = state.bic.trim().ifBlank { null },
                openingBalance = state.openingBalance.toDoubleOrNull() ?: 0.0,
            )

            val result = if (state.isEditing) {
                accountRepository.updateAccount(account)
            } else {
                accountRepository.createAccount(account)
            }

            when (result) {
                is Resource.Success -> {
                    pendingAccountSync = result.data
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = account.name,
                            description = account.description,
                            typeCode = account.typeCode,
                            currency = account.currency,
                            iban = account.iban ?: "",
                            bic = account.bic ?: "",
                            openingBalance = account.openingBalance.toString(),
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
