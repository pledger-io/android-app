package com.pledgerio.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.transactions.detail.DetailInfoRow
import com.pledgerio.app.ui.transactions.detail.TransactionDetailClassificationCard
import com.pledgerio.app.ui.transactions.detail.TransactionDetailFlowCard
import com.pledgerio.app.ui.transactions.detail.TransactionDetailHeroCard
import com.pledgerio.app.ui.transactions.detail.TransactionDetailSplitCard
import com.pledgerio.app.ui.transactions.detail.TransactionDetailTagsCard
import com.pledgerio.app.ui.theme.ExpenseRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit = {},
    onDeleted: (Long) -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val transaction = uiState.transaction
    var showDeleteDialog by remember { mutableStateOf(false) }
    val deleteDescription = stringResource(R.string.transaction_delete_content_description)
    val deletingDescription = stringResource(R.string.transaction_delete_in_progress)

    LaunchedEffect(viewModel) {
        viewModel.deletedEvents.collect { event ->
            onDeleted(event.transactionId)
        }
    }

    if (showDeleteDialog && transaction != null) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeleting) {
                    showDeleteDialog = false
                    viewModel.clearDeleteError()
                }
            },
            title = { Text(stringResource(R.string.transaction_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            R.string.transaction_delete_message,
                            transaction.description.ifBlank {
                                stringResource(R.string.transaction_detail_fallback_title)
                            },
                        ),
                    )
                    if (uiState.deleteFailed) {
                        Text(
                            text = stringResource(R.string.transaction_delete_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteTransaction,
                    enabled = !uiState.isDeleting,
                ) {
                    if (uiState.isDeleting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.transaction_delete_in_progress))
                        }
                    } else {
                        Text(
                            text = stringResource(
                                if (uiState.deleteFailed) {
                                    R.string.transaction_delete_retry
                                } else {
                                    R.string.action_delete
                                },
                            ),
                            color = ExpenseRed,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.clearDeleteError()
                    },
                    enabled = !uiState.isDeleting,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = transaction?.description?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.transaction_detail_fallback_title),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !uiState.isDeleting,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                actions = {
                    if (transaction != null) {
                        IconButton(
                            onClick = {
                                viewModel.clearDeleteError()
                                showDeleteDialog = true
                            },
                            enabled = !uiState.isDeleting,
                            modifier = Modifier.semantics {
                                stateDescription = if (uiState.isDeleting) {
                                    deletingDescription
                                } else {
                                    deleteDescription
                                }
                            },
                        ) {
                            if (uiState.isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .semantics {
                                            stateDescription = deletingDescription
                                        },
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = deleteDescription,
                                    tint = ExpenseRed,
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (transaction != null && !uiState.isDeleting) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToEdit(transaction.id) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_edit)) },
                )
            }
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            uiState.error != null -> ErrorScreen(
                message = uiState.error ?: "",
                onRetry = viewModel::reload,
                modifier = Modifier.padding(paddingValues),
            )
            transaction != null -> {
                val hasClassification = transaction.budgetName != null ||
                    transaction.categoryName != null ||
                    transaction.contractName != null

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    item {
                        TransactionDetailHeroCard(transaction = transaction)
                    }

                    item {
                        TransactionDetailFlowCard(
                            transaction = transaction,
                            sourceAccount = uiState.sourceAccount,
                            destinationAccount = uiState.destinationAccount,
                        )
                    }

                    if (hasClassification) {
                        item {
                            TransactionDetailClassificationCard(
                                budgetName = transaction.budgetName,
                                categoryName = transaction.categoryName,
                                contractName = transaction.contractName,
                            )
                        }
                    }

                    if (transaction.hasSplit) {
                        item {
                            TransactionDetailSplitCard(transaction = transaction)
                        }
                    }

                    if (transaction.tags.isNotEmpty()) {
                        item {
                            TransactionDetailTagsCard(tags = transaction.tags)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }
    }
}
