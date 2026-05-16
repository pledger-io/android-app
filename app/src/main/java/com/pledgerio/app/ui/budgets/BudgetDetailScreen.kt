package com.pledgerio.app.ui.budgets

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.BudgetListState
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
fun BudgetDetailScreen(
    onNavigateBack: () -> Unit,
    onBudgetListUpdated: (BudgetListState) -> Unit = {},
    viewModel: BudgetDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.budgetListUpdates.collect(onBudgetListUpdated)
    }

    if (uiState.formVisible) {
        ExpenseGroupFormSheet(
            isEditing = true,
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
                title = uiState.budget?.name ?: "Budget",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.budget != null) {
                        IconButton(onClick = viewModel::openEditForm) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.budget_expense_edit),
                            )
                        }
                    }
                },
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            uiState.error != null -> ErrorScreen(
                message = uiState.error ?: "",
                modifier = Modifier.padding(paddingValues),
            )
            uiState.budget != null -> {
                val budget = uiState.budget!!
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        PledgerCard {
                            Text("Budget Progress", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("Spent", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        budget.spent.formatCurrency(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed,
                                    )
                                }
                                Column {
                                    Text("Budget", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        budget.amount.formatCurrency(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { budget.percentUsed.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = when {
                                    budget.percentUsed > 0.9f -> ExpenseRed
                                    budget.percentUsed > 0.7f -> WarningAmber
                                    else -> EmeraldGreen
                                },
                            )
                        }
                    }

                    if (budget.expenses.isNotEmpty()) {
                        item {
                            Text(
                                "Expense Categories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(budget.expenses) { expense ->
                            PledgerCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(expense.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${expense.amount.formatCurrency()} / ${expense.expected.formatCurrency()}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
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
