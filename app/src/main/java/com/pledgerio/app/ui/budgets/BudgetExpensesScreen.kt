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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.activity.compose.BackHandler
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
import com.pledgerio.app.domain.model.Budget
import com.pledgerio.app.domain.model.BudgetListState
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.WarningAmber
import com.pledgerio.app.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetExpensesScreen(
    onNavigateBack: () -> Unit,
    onBudgetListUpdated: (BudgetListState) -> Unit = {},
    viewModel: BudgetExpensesViewModel = hiltViewModel(),
) {
    val navigateBack: () -> Unit = {
        viewModel.peekPendingBudgetListSync()?.let(onBudgetListUpdated)
        onNavigateBack()
    }
    BackHandler(onBack = navigateBack)
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.formVisible) {
        ExpenseGroupFormSheet(
            isEditing = uiState.isEditing,
            name = uiState.formName,
            amount = uiState.formAmount,
            error = uiState.formError,
            isSaving = uiState.isSaving,
            onNameChange = viewModel::onFormNameChange,
            onAmountChange = viewModel::onFormAmountChange,
            onDismiss = viewModel::dismissForm,
            onSave = viewModel::saveForm,
        )
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = stringResource(R.string.budget_expense_manage_title),
                subtitle = stringResource(R.string.budget_expense_manage_subtitle),
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && uiState.error == null) {
                FloatingActionButton(
                    onClick = viewModel::openCreateForm,
                    containerColor = EmeraldGreen,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.budget_expense_add))
                }
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                uiState.error != null -> ErrorScreen(
                    message = uiState.error ?: "",
                    onRetry = viewModel::refresh,
                )
                uiState.expenseGroups.isEmpty() -> {
                    EmptyScreen(
                        icon = Icons.Default.PieChart,
                        title = stringResource(R.string.budget_expense_empty_title),
                        message = stringResource(R.string.budget_expense_empty_message),
                        actionLabel = stringResource(R.string.budget_expense_add),
                        onAction = viewModel::openCreateForm,
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
                        items(uiState.expenseGroups, key = { it.id }) { group ->
                            ExpenseGroupCard(
                                group = group,
                                onEdit = { viewModel.openEditForm(group.id) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseGroupCard(
    group: Budget,
    onEdit: () -> Unit,
) {
    PledgerCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${group.spent.formatCurrency()} spent · ${group.amount.formatCurrency()} budgeted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.budget_expense_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { group.percentUsed.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                group.percentUsed > 0.9f -> ExpenseRed
                group.percentUsed > 0.7f -> WarningAmber
                else -> EmeraldGreen
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.budget_expense_remaining,
                group.remaining.formatCurrency(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
