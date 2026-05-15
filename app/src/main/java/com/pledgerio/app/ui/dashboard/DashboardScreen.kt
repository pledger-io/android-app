package com.pledgerio.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit,
    onNavigateToTransaction: (Long) -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = "Pledger.io",
                subtitle = "Your finances at a glance",
                branded = true,
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                uiState.error != null && uiState.accounts.isEmpty() -> {
                    ErrorScreen(
                        message = uiState.error ?: "Unknown error",
                        onRetry = viewModel::refresh,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        // Net Worth Card
                        item {
                            NetWorthCard(
                                netWorth = uiState.netWorth,
                                currency = uiState.currency,
                            )
                        }

                        // Income vs Expense
                        item {
                            IncomeExpenseRow(
                                income = uiState.monthlyIncome,
                                expense = uiState.monthlyExpense,
                                currency = uiState.currency,
                            )
                        }

                        // Recent Transactions Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Recent Transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                TextButton(onClick = onNavigateToTransactions) {
                                    Text("View all")
                                }
                            }
                        }

                        // Recent Transactions
                        if (uiState.recentTransactions.isEmpty() && !uiState.isLoading) {
                            item {
                                Text(
                                    text = "No recent transactions",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        } else {
                            items(uiState.recentTransactions) { transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    onClick = { onNavigateToTransaction(transaction.id) },
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }

            DashboardAddFabMenu(
                expanded = showAddMenu,
                onExpandedChange = { showAddMenu = it },
                onAddTransaction = onNavigateToAddTransaction,
                onAddAccount = onNavigateToAddAccount,
            )
        }
    }
}

@Composable
private fun NetWorthCard(netWorth: Double, currency: String) {
    PledgerCard {
        Text(
            text = "Net Worth",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = netWorth.formatCurrency(currency),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun IncomeExpenseRow(income: Double, expense: Double, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PledgerCard(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(IncomeGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Income", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = income.formatCurrency(currency),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen,
            )
        }

        PledgerCard(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ExpenseRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = ExpenseRed,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Expenses", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = expense.formatCurrency(currency),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed,
            )
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when (transaction.type) {
                        TransactionType.DEBIT -> IncomeGreen.copy(alpha = 0.15f)
                        TransactionType.CREDIT -> ExpenseRed.copy(alpha = 0.15f)
                        TransactionType.TRANSFER -> EmeraldGreen.copy(alpha = 0.15f)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (transaction.type) {
                    TransactionType.DEBIT -> Icons.Default.TrendingUp
                    TransactionType.CREDIT -> Icons.Default.TrendingDown
                    TransactionType.TRANSFER -> Icons.Default.TrendingUp
                },
                contentDescription = null,
                tint = when (transaction.type) {
                    TransactionType.DEBIT -> IncomeGreen
                    TransactionType.CREDIT -> ExpenseRed
                    TransactionType.TRANSFER -> EmeraldGreen
                },
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description.ifBlank { "Transaction" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = transaction.categoryName ?: transaction.displayAccountName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "${if (transaction.type == TransactionType.DEBIT) "+" else "-"}${transaction.amount.formatCurrency()}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = when (transaction.type) {
                TransactionType.DEBIT -> IncomeGreen
                TransactionType.CREDIT -> ExpenseRed
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
