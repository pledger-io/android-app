package com.pledgerio.app.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.dashboard.TransactionItem
import com.pledgerio.app.util.formatDisplay
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAdd: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5
                && !uiState.isLoadingMore
                && (uiState.hasMoreInMonth || uiState.hasOlderMonths)
                && !uiState.isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = "Transactions",
                subtitle = "Track income, expenses & transfers",
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = EmeraldGreen,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MonthNavigator(
                monthLabel = uiState.monthLabel,
                canGoNext = uiState.currentMonth < YearMonth.now(),
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.selectedType == null,
                        onClick = { viewModel.filterByType(null) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = uiState.selectedType == TransactionType.DEBIT,
                        onClick = { viewModel.filterByType(TransactionType.DEBIT) },
                        label = { Text("Income") },
                    )
                    FilterChip(
                        selected = uiState.selectedType == TransactionType.CREDIT,
                        onClick = { viewModel.filterByType(TransactionType.CREDIT) },
                        label = { Text("Expense") },
                    )
                    FilterChip(
                        selected = uiState.selectedType == TransactionType.TRANSFER,
                        onClick = { viewModel.filterByType(TransactionType.TRANSFER) },
                        label = { Text("Transfer") },
                    )
                }
                val filtersActive = uiState.filtersExpanded || uiState.hasActiveFilters
                IconButton(
                    onClick = viewModel::toggleFiltersExpanded,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (filtersActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (filtersActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filters",
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.filtersExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterAutocompleteField(
                        label = "Category",
                        query = uiState.categoryQuery,
                        selected = uiState.selectedCategory,
                        suggestions = uiState.categorySuggestions,
                        isLoading = uiState.isSearchingCategories,
                        onQueryChange = viewModel::onCategoryQueryChanged,
                        onSelected = viewModel::selectCategory,
                        onClear = viewModel::clearCategoryFilter,
                    )
                    FilterAutocompleteField(
                        label = "Budget / expense",
                        query = uiState.expenseQuery,
                        selected = uiState.selectedExpense,
                        suggestions = uiState.expenseSuggestions,
                        isLoading = uiState.isSearchingExpenses,
                        onQueryChange = viewModel::onExpenseQueryChanged,
                        onSelected = viewModel::selectExpense,
                        onClear = viewModel::clearExpenseFilter,
                    )
                    FilterAutocompleteField(
                        label = "Contract",
                        query = uiState.contractQuery,
                        selected = uiState.selectedContract,
                        suggestions = uiState.contractSuggestions,
                        isLoading = uiState.isSearchingContracts,
                        onQueryChange = viewModel::onContractQueryChanged,
                        onSelected = viewModel::selectContract,
                        onClear = viewModel::clearContractFilter,
                    )
                    if (uiState.hasActiveFilters) {
                        TextButton(
                            onClick = viewModel::clearAllFilters,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Clear all filters")
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                    uiState.error != null && uiState.transactions.isEmpty() -> {
                        ErrorScreen(
                            message = uiState.error ?: "Unknown error",
                            onRetry = viewModel::refresh,
                        )
                    }
                    uiState.transactions.isEmpty() -> {
                        EmptyScreen(
                            icon = Icons.Default.Receipt,
                            title = "No transactions",
                            message = if (uiState.hasActiveFilters) {
                                "No transactions match your filters"
                            } else {
                                "No transactions found for this month"
                            },
                        )
                    }
                    else -> {
                        val uniqueTransactions = uiState.transactions.distinctBy { it.id }
                        val grouped = uniqueTransactions.groupBy { it.date }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            grouped.forEach { (date, transactions) ->
                                item(key = "header-$date") {
                                    Text(
                                        text = date.formatDisplay(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        ),
                                    )
                                }
                                items(
                                    items = transactions,
                                    key = { "${date}_${it.id}" },
                                ) { transaction ->
                                    TransactionItem(
                                        transaction = transaction,
                                        onClick = { onNavigateToDetail(transaction.id) },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }

                            if (uiState.isLoadingMore) {
                                item(key = "loading-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
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
private fun MonthNavigator(
    monthLabel: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
            )
        }
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = if (canGoNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
            )
        }
    }
}
