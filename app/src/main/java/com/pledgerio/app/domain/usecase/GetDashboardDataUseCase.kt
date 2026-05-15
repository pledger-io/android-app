package com.pledgerio.app.domain.usecase

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.repository.AccountRepository
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class DashboardData(
    val accounts: List<Account>,
    val recentTransactions: List<Transaction>,
    val netWorth: Double,
    val monthlyIncome: Double,
    val monthlyExpense: Double,
)

class GetDashboardDataUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(): Flow<Resource<DashboardData>> {
        return combine(
            accountRepository.getAccounts(),
            transactionRepository.getRecentTransactions(5),
        ) { accountsResult, transactionsResult ->
            when {
                accountsResult is Resource.Loading || transactionsResult is Resource.Loading -> {
                    Resource.Loading
                }
                accountsResult is Resource.Success && transactionsResult is Resource.Success -> {
                    val accounts = accountsResult.data
                    val transactions = transactionsResult.data
                    Resource.Success(
                        DashboardData(
                            accounts = accounts,
                            recentTransactions = transactions,
                            netWorth = accounts.sumOf { it.balance },
                            monthlyIncome = transactions
                                .filter { it.type == com.pledgerio.app.domain.model.TransactionType.DEBIT }
                                .sumOf { it.amount },
                            monthlyExpense = transactions
                                .filter { it.type == com.pledgerio.app.domain.model.TransactionType.CREDIT }
                                .sumOf { it.amount },
                        )
                    )
                }
                accountsResult is Resource.Error -> {
                    Resource.Error(accountsResult.message)
                }
                transactionsResult is Resource.Error -> {
                    Resource.Error(transactionsResult.message)
                }
                else -> Resource.Loading
            }
        }
    }
}
