package com.pledgerio.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.R
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTransaction: (Long) -> Unit,
    onNavigateToAccount: (Long) -> Unit,
    onNavigateToCategory: (Long, String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val hasResults = uiState.transactions.isNotEmpty() ||
        uiState.accounts.isNotEmpty() ||
        uiState.categories.isNotEmpty()

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = stringResource(R.string.search_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                uiState.query.isBlank() -> {
                    Text(
                        text = stringResource(R.string.search_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.isSearching && !hasResults -> {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.error != null) {
                            item {
                                Text(
                                    text = stringResource(R.string.search_transactions_error),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        if (uiState.transactions.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.search_section_transactions),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(uiState.transactions, key = { it.id }) { tx ->
                                PledgerCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToTransaction(tx.id) },
                                ) {
                                    Text(
                                        tx.description.ifBlank { "Transaction #${tx.id}" },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        tx.amount.formatCurrency(tx.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (uiState.accounts.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.search_section_accounts),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(uiState.accounts, key = { it.id }) { account ->
                                PledgerCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToAccount(account.id) },
                                ) {
                                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        if (uiState.categories.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.search_section_categories),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(uiState.categories, key = { it.id }) { category ->
                                PledgerCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onNavigateToCategory(category.id, category.name)
                                        },
                                ) {
                                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        if (!hasResults && uiState.error == null) {
                            item {
                                Text(
                                    stringResource(R.string.search_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
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
