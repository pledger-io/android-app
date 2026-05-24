package com.pledgerio.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val transaction = uiState.transaction

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = transaction?.description?.takeIf { it.isNotBlank() } ?: "Transaction",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (transaction != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToEdit(transaction.id) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text("Edit") },
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
