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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.ui.components.AccountIcon
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.LazyListPaginationEffect
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

    LazyListPaginationEffect(
        listState = listState,
        enabled = uiState.transactions.isNotEmpty() &&
            uiState.hasMore &&
            !uiState.isLoadingMore &&
            !uiState.isLoading,
        alsoLoadWhen = {
            uiState.transactions.size < 25 &&
                listState.layoutInfo.totalItemsCount < 20 &&
                uiState.hasMore
        },
        onLoadMore = viewModel::loadNextPage,
    )

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = uiState.account?.name ?: stringResource(R.string.account_detail_fallback_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                actions = {
                    uiState.account?.let { account ->
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.content_description_delete_account),
                            )
                        }
                        IconButton(onClick = { onNavigateToEdit(account.id) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.content_description_edit_account),
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
                onRetry = viewModel::reload,
                modifier = Modifier.padding(paddingValues),
            )
            uiState.account != null -> {
                val account = uiState.account!!
                val typeMeta = AccountTypeCatalog.metadataFor(account.typeCode)

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!uiState.isDeleting) showDeleteDialog = false },
                        title = { Text(stringResource(R.string.account_delete_title)) },
                        text = {
                            Text(stringResource(R.string.account_delete_confirm_message, account.name))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = viewModel::deleteAccount,
                                enabled = !uiState.isDeleting,
                            ) {
                                Text(stringResource(R.string.action_delete), color = ExpenseRed)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteDialog = false },
                                enabled = !uiState.isDeleting,
                            ) {
                                Text(stringResource(R.string.cancel))
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
                                        text = stringResource(R.string.account_current_balance),
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
                            DetailInfoRow(
                                stringResource(R.string.account_detail_type),
                                typeMeta.localizedDisplayName(),
                            )
                            Text(
                                text = typeMeta.localizedDescription(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            DetailInfoRow(
                                stringResource(R.string.account_detail_currency),
                                account.currency,
                            )
                            account.iban?.let {
                                DetailInfoRow(
                                    stringResource(R.string.account_iban_label),
                                    "${it.take(8)}****",
                                )
                            }
                            if (typeMeta.showOpeningBalance) {
                                DetailInfoRow(
                                    stringResource(R.string.account_detail_opening_balance),
                                    account.openingBalance.formatCurrency(account.currency),
                                )
                            }
                        }
                    }

                    if (uiState.transactions.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.account_detail_transactions),
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
                                    stringResource(R.string.account_load_more_progress, loaded, total)
                                } else {
                                    stringResource(R.string.account_load_more)
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
