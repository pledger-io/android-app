package com.pledgerio.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.ui.components.AccountIcon
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.util.CurrencyProvider
import com.pledgerio.app.util.formatCurrency
import com.pledgerio.app.util.formatDisplay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                title = "Transaction Details",
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
                modifier = Modifier.padding(paddingValues),
            )
            transaction != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = transaction.description.ifBlank { "Transaction" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${if (transaction.type == TransactionType.DEBIT) "+" else "-"}${transaction.amount.formatCurrency(transaction.currency)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == TransactionType.DEBIT) IncomeGreen else ExpenseRed,
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader("Details")
                    DetailRow("Date", transaction.date.formatDisplay())
                    DetailRow("Type", transaction.type.name.lowercase().replaceFirstChar { it.uppercase() })
                    val currencyInfo = CurrencyProvider.getInstance()?.get(transaction.currency)
                    val currencyDisplay = if (currencyInfo != null) {
                        "${currencyInfo.name} (${currencyInfo.symbol})"
                    } else {
                        transaction.currency
                    }
                    DetailRow("Currency", currencyDisplay)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader("Accounts")
                    AccountDetailRow(
                        label = "From",
                        accountName = transaction.sourceAccountName,
                        account = uiState.sourceAccount,
                    )
                    AccountDetailRow(
                        label = "To",
                        accountName = transaction.destinationAccountName,
                        account = uiState.destinationAccount,
                    )

                    if (transaction.budgetName != null || transaction.categoryName != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        SectionHeader("Classification")
                        transaction.budgetName?.let { DetailRow("Category", it) }
                        transaction.categoryName?.let { DetailRow("Sub category", it) }
                    }

                    if (transaction.hasSplit) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        SectionHeader("Split")
                        transaction.split.forEach { part ->
                            DetailRow(
                                label = part.description.ifBlank { "Part" },
                                value = part.amount.formatCurrency(transaction.currency),
                            )
                        }
                        DetailRow(
                            label = "Split total",
                            value = transaction.splitTotal.formatCurrency(transaction.currency),
                        )
                    }

                    if (transaction.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        SectionHeader("Tags")
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            transaction.tags.forEach { tag ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }

                    transaction.contractName?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        DetailRow("Contract", it)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AccountDetailRow(
    label: String,
    accountName: String,
    account: Account?,
) {
    val displayName = accountName.ifBlank { account?.name ?: "—" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountIcon(
                iconFileCode = account?.iconFileCode,
                size = 32.dp,
                contentDescription = displayName,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
