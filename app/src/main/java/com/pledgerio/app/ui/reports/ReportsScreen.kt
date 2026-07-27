package com.pledgerio.app.ui.reports

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LastUpdatedIndicator
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.MonthNavigator
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.util.localizedTitle
import java.time.YearMonth

enum class ReportType {
    OVERVIEW,
    INCOME_EXPENSE,
    CATEGORY,
    BUDGET,
    NET_WORTH,
    BALANCE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateToSettings: () -> Unit,
    onCategoryClick: (categoryId: Long, categoryName: String, yearMonth: YearMonth) -> Unit = { _, _, _ -> },
    onAccountClick: (accountId: Long) -> Unit = {},
    onBudgetExpenseClick: (expenseId: Long, expenseName: String, yearMonth: YearMonth) -> Unit = { _, _, _ -> },
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = stringResource(R.string.reports_title),
                subtitle = stringResource(R.string.reports_subtitle),
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
                        label = { Text(type.localizedTitle()) },
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
                    uiState.error != null &&
                        uiState.overview == null &&
                        uiState.incomeExpense == null &&
                        uiState.partitions.isEmpty() &&
                        uiState.budgetItems.isEmpty() &&
                        uiState.netWorthTrend.isEmpty() -> {
                        ErrorScreen(
                            message = uiState.error ?: "",
                            onRetry = viewModel::refresh,
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
                            item {
                                when (uiState.selectedType) {
                                    ReportType.OVERVIEW -> {
                                        uiState.overview?.let { overview ->
                                            ReportsOverviewContent(
                                                overview = overview,
                                                yearMonth = uiState.currentMonth,
                                                onCategoryClick = onCategoryClick,
                                            )
                                        }
                                    }
                                    ReportType.INCOME_EXPENSE -> {
                                        uiState.incomeExpense?.let { summary ->
                                            IncomeExpenseCard(summary = summary)
                                        }
                                    }
                                    ReportType.CATEGORY -> {
                                        PartitionList(
                                            partitions = uiState.partitions.map { it.toUi() },
                                            onItemClick = { item ->
                                                val id = item.id ?: return@PartitionList
                                                onCategoryClick(id, item.label, uiState.currentMonth)
                                            },
                                            itemContentDescription = { item ->
                                                stringResource(R.string.reports_open_category, item.label)
                                            },
                                        )
                                    }
                                    ReportType.BALANCE -> {
                                        PartitionList(
                                            partitions = uiState.partitions.map { it.toUi() },
                                            onItemClick = { item ->
                                                val id = item.id ?: return@PartitionList
                                                onAccountClick(id)
                                            },
                                            itemContentDescription = { item ->
                                                stringResource(R.string.reports_open_account, item.label)
                                            },
                                        )
                                    }
                                    ReportType.BUDGET -> {
                                        BudgetPerformanceList(
                                            items = uiState.budgetItems,
                                            onItemClick = { item ->
                                                val expenseId = item.expenseId
                                                    ?: return@BudgetPerformanceList
                                                onBudgetExpenseClick(
                                                    expenseId,
                                                    item.name,
                                                    uiState.currentMonth,
                                                )
                                            },
                                        )
                                    }
                                    ReportType.NET_WORTH -> {
                                        NetWorthSection(points = uiState.netWorthTrend)
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
