package com.pledgerio.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountType
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAdd: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Accounts", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = EmeraldGreen,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(paddingValues),
        ) {
            when {
                uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                uiState.error != null && uiState.accounts.isEmpty() -> {
                    ErrorScreen(
                        message = uiState.error ?: "",
                        onRetry = viewModel::refresh,
                    )
                }
                uiState.accounts.isEmpty() -> {
                    EmptyScreen(
                        icon = Icons.Default.AccountBalance,
                        title = "No accounts yet",
                        message = "Add your first account to start tracking",
                        actionLabel = "Add Account",
                        onAction = onNavigateToAdd,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        val grouped = uiState.accounts.groupBy { it.typeCode }
                        grouped.forEach { (typeCode, accounts) ->
                            item {
                                Text(
                                    text = accounts.firstOrNull()?.typeDisplayName
                                        ?: typeCode.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(accounts, key = { it.id }) { account ->
                                AccountCard(
                                    account = account,
                                    onClick = { onNavigateToDetail(account.id) },
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    onClick: () -> Unit,
) {
    PledgerCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = account.type.icon(),
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                account.iban?.let {
                    Text(
                        text = it.take(8) + "****",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = account.balance.formatCurrency(account.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (account.balance >= 0) IncomeGreen else ExpenseRed,
            )
        }
    }
}

private fun AccountType.icon(): ImageVector = when (this) {
    AccountType.CHECKING -> Icons.Default.AccountBalance
    AccountType.SAVINGS -> Icons.Default.Savings
    AccountType.CREDIT_CARD -> Icons.Default.CreditCard
    AccountType.CASH -> Icons.Default.Wallet
    else -> Icons.Default.AccountBalance
}

private fun AccountType.displayName(): String = when (this) {
    AccountType.CHECKING -> "Checking Accounts"
    AccountType.SAVINGS -> "Savings"
    AccountType.CREDIT_CARD -> "Credit Cards"
    AccountType.CASH -> "Cash"
    AccountType.LIABILITY -> "Liabilities"
    AccountType.LOAN -> "Loans"
    AccountType.INVESTMENT -> "Investments"
    AccountType.MORTGAGE -> "Mortgages"
    AccountType.DEBTOR -> "Debtor"
    AccountType.CREDITOR -> "Creditor"
    AccountType.OTHER -> "Other"
}
