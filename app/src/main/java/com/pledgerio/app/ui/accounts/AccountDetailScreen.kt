package com.pledgerio.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.ui.components.AccountIcon
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.util.formatDisplay
import com.pledgerio.app.ui.dashboard.TransactionItem
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTransaction: (Long) -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            onNavigateBack()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            if (uiState.transactions.isEmpty() || !uiState.hasMore || uiState.isLoadingMore || uiState.isLoading) {
                return@derivedStateOf false
            }
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val nearEndOfList = lastVisibleItem >= totalItems - 5
            // Short lists may not scroll; still fetch the next page when more exist server-side.
            val shortListWithMoreRemaining =
                uiState.transactions.size < 25 && totalItems < 20 && uiState.hasMore
            nearEndOfList || shortListWithMoreRemaining
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = uiState.account?.name ?: "Account",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.account?.let { account ->
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete account")
                        }
                        IconButton(onClick = { onNavigateToEdit(account.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit account")
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
            uiState.account != null -> {
                val account = uiState.account!!
                val typeMeta = AccountTypeCatalog.metadataFor(account.typeCode)

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!uiState.isDeleting) showDeleteDialog = false },
                        title = { Text("Delete account?") },
                        text = {
                            Text(
                                "“${account.name}” will be removed from Pledger. " +
                                    "This cannot be undone if the server allows deletion.",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = viewModel::deleteAccount,
                                enabled = !uiState.isDeleting,
                            ) {
                                Text("Delete", color = ExpenseRed)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteDialog = false },
                                enabled = !uiState.isDeleting,
                            ) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    item {
                        PledgerCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AccountIcon(
                                    iconFileCode = account.iconFileCode,
                                    size = 56.dp,
                                    contentDescription = account.name,
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Current Balance",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = account.balance.formatCurrency(account.currency),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (account.balance >= 0) IncomeGreen else ExpenseRed,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        PledgerCard {
                            DetailInfoRow("Type", typeMeta.displayName)
                            Text(
                                text = typeMeta.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            DetailInfoRow("Currency", account.currency)
                            account.iban?.let { DetailInfoRow("IBAN", "${it.take(8)}****") }
                            if (typeMeta.showOpeningBalance) {
                                DetailInfoRow(
                                    "Opening Balance",
                                    account.openingBalance.formatCurrency(account.currency),
                                )
                            }
                        }
                    }

                    if (uiState.transactions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }

                        val uniqueTransactions = uiState.transactions.distinctBy { it.id }
                        val grouped = uniqueTransactions.groupBy { it.date }
                        grouped.forEach { (date, transactions) ->
                            item(key = "date-header-$date") {
                                Text(
                                    text = date.formatDisplay(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(
                                items = transactions.distinctBy { it.id },
                                key = { transaction -> "tx-${date}-${transaction.id}" },
                            ) { transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    onClick = { onNavigateToTransaction(transaction.id) },
                                )
                            }
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
                    } else if (uiState.hasMore) {
                        item(key = "load-more") {
                            TextButton(
                                onClick = { viewModel.loadNextPage() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val loaded = uiState.transactions.size
                                val total = uiState.totalTransactionCount
                                val label = if (total > loaded) {
                                    "Load more ($loaded of $total)"
                                } else {
                                    "Load more"
                                }
                                Text(label)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
