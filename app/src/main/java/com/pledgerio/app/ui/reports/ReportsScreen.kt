package com.pledgerio.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.BudgetPerformanceItem
import com.pledgerio.app.domain.model.PartitionAmount
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LastUpdatedIndicator
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.MonthNavigator
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.util.formatCurrency
import java.time.YearMonth
import kotlin.math.max

enum class ReportType(val title: String) {
    INCOME_EXPENSE("Income vs Expenses"),
    CATEGORY("Category Breakdown"),
    BUDGET("Budget Performance"),
    NET_WORTH("Net Worth"),
    BALANCE("Account Balance"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = "Reports",
                subtitle = "Insights into your money",
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportType.entries.forEach { type ->
                    FilterChip(
                        selected = uiState.selectedType == type,
                        onClick = { viewModel.selectReportType(type) },
                        label = { Text(type.title) },
                    )
                }
            }
            MonthNavigator(
                monthLabel = uiState.monthLabel,
                canGoNext = uiState.currentMonth < YearMonth.now(),
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
            LastUpdatedIndicator(
                lastUpdatedAtMillis = uiState.lastUpdatedAtMillis,
                isRefreshing = uiState.isRefreshing,
            )
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                    uiState.error != null -> ErrorScreen(
                        message = uiState.error ?: "",
                        onRetry = viewModel::refresh,
                    )
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                            item {
                                when (uiState.selectedType) {
                                    ReportType.INCOME_EXPENSE -> {
                                        val summary = uiState.incomeExpense
                                        if (summary != null) {
                                            IncomeExpenseCard(
                                                income = summary.income,
                                                expense = summary.expense,
                                            )
                                        }
                                    }
                                    ReportType.CATEGORY, ReportType.BALANCE -> {
                                        PartitionList(uiState.partitions)
                                    }
                                    ReportType.BUDGET -> {
                                        BudgetPerformanceList(uiState.budgetItems)
                                    }
                                    ReportType.NET_WORTH -> {
                                        NetWorthTrendList(uiState.netWorthTrend.map { it.date to it.amount })
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeExpenseCard(income: Double, expense: Double) {
    val total = max(income + expense, 0.01)
    PledgerCard {
        Text(
            text = "Income vs expenses",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Income", style = MaterialTheme.typography.labelSmall)
                Text(
                    income.formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    color = IncomeGreen,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Expenses", style = MaterialTheme.typography.labelSmall)
                Text(
                    expense.formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    color = ExpenseRed,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (income / total).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = IncomeGreen,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (expense / total).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = ExpenseRed,
        )
    }
}

@Composable
private fun PartitionList(partitions: List<PartitionAmount>) {
    if (partitions.isEmpty()) {
        Text(
            stringResource(R.string.reports_no_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val maxAmount = partitions.maxOf { it.amount }.coerceAtLeast(0.01)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        partitions.take(12).forEach { item ->
            PledgerCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        item.amount.formatCurrency(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (item.amount / maxAmount).toFloat().coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BudgetPerformanceList(items: List<BudgetPerformanceItem>) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.reports_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            PledgerCard {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${item.spent.formatCurrency()} / ${item.budgeted.formatCurrency()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.spent > item.budgeted) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.budgeted > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (item.spent / item.budgeted).toFloat().coerceIn(0f, 1.5f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (item.spent > item.budgeted) ExpenseRed else IncomeGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetWorthTrendList(points: List<Pair<String, Double>>) {
    if (points.isEmpty()) {
        Text(stringResource(R.string.reports_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.take(15).forEach { (date, amount) ->
            PledgerCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(date, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        amount.formatCurrency(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
