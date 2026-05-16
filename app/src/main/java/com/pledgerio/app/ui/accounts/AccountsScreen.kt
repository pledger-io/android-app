package com.pledgerio.app.ui.accounts

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountListFilter
import com.pledgerio.app.domain.model.AccountSection
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.ui.components.AccountIcon
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.ErrorScreen
import com.pledgerio.app.ui.components.LoadingScreen
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.components.PledgerTopBar
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.ExpenseRed
import com.pledgerio.app.ui.theme.IncomeGreen
import com.pledgerio.app.util.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAdd: (String?) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val shouldLoadMoreCounterparties by remember {
        derivedStateOf {
            if (uiState.filter != AccountListFilter.COUNTERPARTY) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 5 &&
                !uiState.isLoadingCounterparties &&
                !uiState.isLoadingMoreCounterparties &&
                uiState.hasMoreCounterparties
        }
    }

    LaunchedEffect(shouldLoadMoreCounterparties) {
        if (shouldLoadMoreCounterparties) {
            viewModel.loadMoreCounterparties()
        }
    }

    val hasOwned = uiState.ownedAccounts.isNotEmpty()
    val hasPartiesData = uiState.counterpartyTotal > 0 || uiState.counterpartyAccounts.isNotEmpty()
    val showEmpty = !uiState.isLoading &&
        when (uiState.filter) {
            AccountListFilter.COUNTERPARTY -> !uiState.isLoadingCounterparties &&
                uiState.counterpartyAccounts.isEmpty() &&
                uiState.counterpartyError == null
            AccountListFilter.OWNED -> !hasOwned
            AccountListFilter.ALL -> !hasOwned && !hasPartiesData
        }

    Scaffold(
        topBar = {
            PledgerTopBar(
                title = "Accounts",
                subtitle = when (uiState.filter) {
                    AccountListFilter.ALL -> buildString {
                        append("${uiState.ownedCount} owned")
                        if (uiState.counterpartyTotal > 0) {
                            append(" · ${uiState.counterpartyTotal} parties")
                        }
                    }
                    AccountListFilter.OWNED -> "${uiState.ownedCount} wallets you hold"
                    AccountListFilter.COUNTERPARTY -> when {
                        uiState.counterpartySearchQuery.isNotBlank() -> "Search results"
                        uiState.counterpartyTotal > 0 -> {
                            val loaded = uiState.counterpartyLoadedCount
                            val total = uiState.counterpartyTotal
                            if (loaded < total) "Showing $loaded of $total" else "$total parties"
                        }
                        else -> "Creditors & debtors"
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AccountFilterChipsRow(
                        filter = uiState.filter,
                        ownedCount = uiState.ownedCount,
                        counterpartyTotal = uiState.counterpartyTotal,
                        onFilterSelected = viewModel::setFilter,
                    )
                    if (uiState.filter == AccountListFilter.COUNTERPARTY) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CounterpartySearchField(
                            query = uiState.counterpartySearchQuery,
                            onQueryChange = viewModel::onCounterpartySearchChanged,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when {
                            uiState.isLoading && !uiState.isRefreshing -> LoadingScreen()
                            uiState.error != null && !hasOwned &&
                                uiState.filter != AccountListFilter.COUNTERPARTY -> {
                                ErrorScreen(
                                    message = uiState.error ?: "",
                                    onRetry = viewModel::refresh,
                                )
                            }
                            showEmpty -> {
                                EmptyScreen(
                                    icon = Icons.Default.AccountBalance,
                                    title = when (uiState.filter) {
                                        AccountListFilter.COUNTERPARTY -> "No parties found"
                                        else -> "No accounts yet"
                                    },
                                    message = when (uiState.filter) {
                                        AccountListFilter.COUNTERPARTY -> if (
                                            uiState.counterpartySearchQuery.isNotBlank()
                                        ) {
                                            "Try a different search term."
                                        } else {
                                            "Add creditors and debtors you pay or receive money from."
                                        }
                                        else -> "Add checking, savings, credit, or counterparty accounts."
                                    },
                                    actionLabel = if (uiState.filter == AccountListFilter.COUNTERPARTY) {
                                        "Add party"
                                    } else {
                                        "Add account"
                                    },
                                    onAction = { showAddMenu = true },
                                )
                            }
                            else -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (uiState.filter != AccountListFilter.COUNTERPARTY) {
                                        item {
                                            AccountsSummaryCard(
                                                accountCount = uiState.filteredAccounts.size,
                                                totalBalance = uiState.totalBalance,
                                                currency = uiState.filteredAccounts.firstOrNull()?.currency
                                                    ?: "EUR",
                                                subtitle = when (uiState.filter) {
                                                    AccountListFilter.ALL -> "owned accounts"
                                                    else -> "in this view"
                                                },
                                            )
                                        }
                                    } else {
                                        item {
                                            CounterpartiesSummaryCard(
                                                loaded = uiState.counterpartyLoadedCount,
                                                total = uiState.counterpartyTotal,
                                            )
                                        }
                                    }

                                    if (uiState.filter == AccountListFilter.COUNTERPARTY) {
                                        if (uiState.isLoadingCounterparties) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(32.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    CircularProgressIndicator(color = EmeraldGreen)
                                                }
                                            }
                                        }

                                        uiState.counterpartyError?.let { error ->
                                            item {
                                                Text(
                                                    text = error,
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }

                                    if (uiState.showCounterpartyBrowseCard) {
                                item {
                                    CounterpartiesBrowseCard(
                                        total = uiState.counterpartyTotal,
                                        onBrowse = { viewModel.setFilter(AccountListFilter.COUNTERPARTY) },
                                    )
                                }
                            }

                            if (uiState.filter == AccountListFilter.COUNTERPARTY &&
                                !uiState.isLoadingCounterparties &&
                                uiState.counterpartyAccounts.isEmpty() &&
                                uiState.counterpartyError == null
                            ) {
                                // Handled by showEmpty above
                            } else if (
                                uiState.filter != AccountListFilter.COUNTERPARTY &&
                                uiState.filteredAccounts.isEmpty()
                            ) {
                                item {
                                    Text(
                                        text = "No accounts in this view.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp),
                                    )
                                }
                            } else {
                                uiState.sections.forEach { section ->
                                    item(key = "header-${section.group.name}") {
                                        AccountSectionHeader(section = section)
                                    }
                                    items(
                                        items = section.accounts,
                                        key = { account -> "${section.group.name}_${account.id}" },
                                    ) { account ->
                                        AccountCard(
                                            account = account,
                                            onClick = { onNavigateToDetail(account.id) },
                                        )
                                    }
                                }
                            }

                            if (uiState.isLoadingMoreCounterparties) {
                                item(key = "loading-more-counterparties") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = EmeraldGreen,
                                        )
                                    }
                                }
                            }

                                    item { Spacer(modifier = Modifier.height(88.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            AccountAddFabMenu(
                expanded = showAddMenu,
                onExpandedChange = { showAddMenu = it },
                accountTypeOptions = uiState.accountTypeOptions,
                onAddAccount = { typeCode -> onNavigateToAdd(typeCode) },
            )
        }
    }
}

@Composable
private fun AccountFilterChipsRow(
    filter: AccountListFilter,
    ownedCount: Int,
    counterpartyTotal: Long,
    onFilterSelected: (AccountListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == AccountListFilter.ALL,
            onClick = { onFilterSelected(AccountListFilter.ALL) },
            label = { Text("All") },
        )
        FilterChip(
            selected = filter == AccountListFilter.OWNED,
            onClick = { onFilterSelected(AccountListFilter.OWNED) },
            label = { Text("Owned ($ownedCount)") },
        )
        FilterChip(
            selected = filter == AccountListFilter.COUNTERPARTY,
            onClick = { onFilterSelected(AccountListFilter.COUNTERPARTY) },
            label = {
                Text(if (counterpartyTotal > 0) "Parties ($counterpartyTotal)" else "Parties")
            },
        )
    }
}

@Composable
private fun CounterpartySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search parties…") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun CounterpartiesSummaryCard(loaded: Int, total: Long) {
    PledgerCard {
        Column {
            Text(
                text = "Counterparties",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (total > 0 && loaded < total) {
                    "Showing $loaded of $total"
                } else {
                    "$loaded parties"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Search or scroll to load more. Balances are loaded when you open an account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CounterpartiesBrowseCard(
    total: Long,
    onBrowse: () -> Unit,
) {
    PledgerCard(
        modifier = Modifier.clickable(onClick = onBrowse),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Counterparties",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$total creditors & debtors — search and scroll to browse",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Browse parties",
                tint = EmeraldGreen,
            )
        }
    }
}

@Composable
private fun AccountsSummaryCard(
    accountCount: Int,
    totalBalance: Double,
    currency: String,
    subtitle: String = "in this view",
) {
    PledgerCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Total balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = totalBalance.formatCurrency(currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (totalBalance >= 0) IncomeGreen else ExpenseRed,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$accountCount accounts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountSectionHeader(section: AccountSection) {
    val currency = section.accounts.firstOrNull()?.currency ?: "EUR"
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = section.group.icon(),
                contentDescription = null,
                tint = EmeraldGreen,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = section.group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = section.totalBalance.formatCurrency(currency),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (section.totalBalance >= 0) IncomeGreen else ExpenseRed,
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    onClick: () -> Unit,
) {
    val meta = AccountTypeCatalog.metadataFor(account.typeCode)
    PledgerCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountIcon(
                iconFileCode = account.iconFileCode,
                size = 44.dp,
                contentDescription = account.name,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = meta.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                account.iban?.let {
                    Text(
                        text = it.take(8) + "****",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = account.balance.formatCurrency(account.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (account.balance >= 0) IncomeGreen else ExpenseRed,
            )
        }
    }
}
