package com.pledgerio.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.ui.theme.WarningAmber
import com.pledgerio.app.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateToDetail: (Long) -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(paddingValues),
        ) {
            when {
                uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                uiState.error != null && uiState.budgets.isEmpty() -> {
                    ErrorScreen(
                        message = uiState.error ?: "",
                        onRetry = viewModel::refresh,
                    )
                }
                uiState.budgets.isEmpty() -> {
                    EmptyScreen(
                        icon = Icons.Default.PieChart,
                        title = "No budgets yet",
                        message = "Create a budget to track your spending",
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

                        // Summary card
                        item {
                            val totalBudgeted = uiState.budgets.sumOf { it.amount }
                            val totalSpent = uiState.budgets.sumOf { it.spent }
                            PledgerCard {
                                Text(
                                    text = "Monthly Overview",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text("Spent", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = totalSpent.formatCurrency(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Budgeted", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = totalBudgeted.formatCurrency(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                val progress = if (totalBudgeted > 0) (totalSpent / totalBudgeted).toFloat().coerceIn(0f, 1f) else 0f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = when {
                                        progress > 0.9f -> ExpenseRed
                                        progress > 0.7f -> WarningAmber
                                        else -> IncomeGreen
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }

                        items(uiState.budgets, key = { it.id }) { budget ->
                            BudgetCard(
                                budget = budget,
                                onClick = { onNavigateToDetail(budget.id) },
                            )
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(budget: Budget, onClick: () -> Unit) {
    PledgerCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = budget.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${budget.spent.formatCurrency()} / ${budget.amount.formatCurrency()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { budget.percentUsed.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                budget.percentUsed > 0.9f -> ExpenseRed
                budget.percentUsed > 0.7f -> WarningAmber
                else -> EmeraldGreen
            },
            trackColor = MaterialTheme.colorScheme.surface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${budget.remaining.formatCurrency()} remaining",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
