package com.pledgerio.app.ui.budgets

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.LastUpdatedIndicator
import com.pledgerio.app.ui.components.MonthNavigator
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.PledgerThemeExt
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.ui.theme.WarningAmber
import com.pledgerio.app.util.formatCurrency
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.formVisible) {
        ExpenseGroupFormSheet(
            isEditing = uiState.isEditingExpense,
            name = uiState.formName,
            amount = uiState.formAmount,
            error = uiState.formError,
            isSaving = uiState.isSavingExpense,
            onNameChange = viewModel::onExpenseFormNameChange,
            onAmountChange = viewModel::onExpenseFormAmountChange,
            onDismiss = viewModel::dismissExpenseForm,
            onSave = viewModel::saveExpenseForm,
        )
    }

    if (uiState.incomeFormVisible) {
        BudgetIncomeFormSheet(
            amount = uiState.incomeFormAmount,
            error = uiState.incomeFormError,
            isSaving = uiState.isSavingIncome,
            onAmountChange = viewModel::onIncomeFormAmountChange,
            onDismiss = viewModel::dismissIncomeForm,
            onSave = viewModel::saveIncomeForm,
        )
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = "Budgets",
                subtitle = "Plan and track your spending",
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.canAddExpenseGroups) {
                FloatingActionButton(
                    onClick = viewModel::openCreateExpenseForm,
                    containerColor = PledgerThemeExt.brandAccent,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.budget_expense_add),
                    )
                }
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(paddingValues),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!uiState.needsInitialSetup) {
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
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
            when {
                uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                uiState.needsInitialSetup -> {
                    InitialBudgetSetupContent(
                        year = uiState.setupYear,
                        month = uiState.setupMonth,
                        income = uiState.setupIncome,
                        setupError = uiState.setupError,
                        isSubmitting = uiState.isCreatingInitial,
                        onYearChange = viewModel::onSetupYearChange,
                        onMonthChange = viewModel::onSetupMonthChange,
                        onIncomeChange = viewModel::onSetupIncomeChange,
                        onSubmit = viewModel::createInitialBudget,
                    )
                }
                uiState.error != null && uiState.budgets.isEmpty() && uiState.monthlyIncome == null -> {
                    ErrorScreen(
                        message = uiState.error ?: "",
                        onRetry = viewModel::refresh,
                    )
                }
                uiState.budgets.isEmpty() && uiState.monthlyIncome == null -> {
                    EmptyScreen(
                        icon = Icons.Default.PieChart,
                        title = stringResource(R.string.budget_empty_title),
                        message = stringResource(R.string.budget_empty_message),
                        actionLabel = stringResource(R.string.budget_expense_add),
                        onAction = viewModel::openCreateExpenseForm,
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
                            val totalBudgeted = uiState.budgets.sumOf { it.amount }
                            val totalSpent = uiState.budgets.sumOf { it.spent }
                            PledgerCard {
                                Text(
                                    text = stringResource(R.string.budget_overview_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                uiState.monthlyIncome?.let { income ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(
                                                text = stringResource(R.string.budget_overview_income),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Text(
                                                text = income.formatCurrency(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        IconButton(onClick = viewModel::openIncomeForm) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(
                                                    R.string.budget_income_edit,
                                                ),
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.budget_spent),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        Text(
                                            text = totalSpent.formatCurrency(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.budget_budgeted),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        Text(
                                            text = totalBudgeted.formatCurrency(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                val progress = if (totalBudgeted > 0) {
                                    (totalSpent / totalBudgeted).toFloat().coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
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

                        if (uiState.budgets.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.budget_empty_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(uiState.budgets, key = { it.id }) { budget ->
                                BudgetCard(
                                    budget = budget,
                                    onClick = { onNavigateToDetail(budget.id) },
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
                else -> PledgerThemeExt.brandAccent
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
