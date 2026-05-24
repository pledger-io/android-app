package com.pledgerio.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.repository.CurrencyRepository
import com.pledgerio.app.domain.usecase.GetDashboardDataUseCase
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val financeExperienceMode: FinanceExperienceMode = FinanceExperienceMode.GUIDED,
    val accounts: List<Account> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val lastUpdatedAtMillis: Long? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase,
    private val currencyRepository: CurrencyRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var dashboardJob: Job? = null

    init {
        viewModelScope.launch { currencyRepository.sync() }
        viewModelScope.launch {
            userPreferences.displayCurrencyCode.collect { code ->
                _uiState.update { it.copy(currency = code) }
            }
        }
        viewModelScope.launch {
            userPreferences.financeExperienceMode.collect { mode ->
                _uiState.update { it.copy(financeExperienceMode = mode) }
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
        loadDashboard()
    }

    private fun loadDashboard() {
        dashboardJob?.cancel()
        dashboardJob = viewModelScope.launch {
            getDashboardDataUseCase().collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                accounts = result.data.accounts,
                                recentTransactions = result.data.recentTransactions,
                                netWorth = result.data.netWorth,
                                monthlyIncome = result.data.monthlyIncome,
                                monthlyExpense = result.data.monthlyExpense,
                                currency = userPreferences.displayCurrencyCode.value,
                                error = null,
                                lastUpdatedAtMillis = System.currentTimeMillis(),
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
    }
}
