package com.pledgerio.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val netWorth: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val currency: String = "EUR",
    val accounts: List<Account> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val currencyRepository: CurrencyRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { currencyRepository.sync() }
        viewModelScope.launch {
            userPreferences.displayCurrencyCode.collect { code ->
                _uiState.update { it.copy(currency = code) }
            }
        }
        loadDashboard()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadDashboard()
    }

    /** Reload recent transactions when returning to the dashboard (e.g. after creating one). */
    fun refreshRecentTransactions() {
        viewModelScope.launch {
            transactionRepository.getRecentTransactions(5).collect { result ->
                when (result) {
                    is Resource.Success -> applyRecentTransactions(result.data)
                    else -> Unit
                }
            }
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            accountRepository.getAccounts().collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        val accounts = result.data
                        val netWorth = accounts.sumOf { it.balance }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                accounts = accounts,
                                netWorth = netWorth,
                                currency = userPreferences.displayCurrencyCode.value,
                                error = null,
                            )
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = result.message,
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            transactionRepository.getRecentTransactions(5).collect { result ->
                when (result) {
                    is Resource.Success -> applyRecentTransactions(result.data)
                    else -> Unit
                }
            }
        }
    }

    private fun applyRecentTransactions(transactions: List<Transaction>) {
        val income = transactions
            .filter { it.type == com.pledgerio.app.domain.model.TransactionType.DEBIT }
            .sumOf { it.amount }
        val expense = transactions
            .filter { it.type == com.pledgerio.app.domain.model.TransactionType.CREDIT }
            .sumOf { it.amount }
        _uiState.update {
            it.copy(
                recentTransactions = transactions,
                monthlyIncome = income,
                monthlyExpense = expense,
            )
        }
    }
}
