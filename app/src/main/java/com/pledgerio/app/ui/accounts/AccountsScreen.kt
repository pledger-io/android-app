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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.AccountListFilter
import com.pledgerio.app.domain.model.AccountSection
import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.ui.components.AccountIcon
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.ui.components.EmptyScreen
import com.pledgerio.app.ui.components.LastUpdatedIndicator
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
    onNavigateToSettings: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAddMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    val accountsSubtitle = accountsListSubtitle(uiState)
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
                title = stringResource(R.string.accounts_title),
                subtitle = accountsSubtitle,
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
                    LastUpdatedIndicator(
                        lastUpdatedAtMillis = uiState.lastUpdatedAtMillis,
                        isRefreshing = uiState.isRefreshing,
                    )
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
                                val guided =
                                    uiState.financeExperienceMode == FinanceExperienceMode.GUIDED
                                EmptyScreen(
                                    icon = Icons.Default.AccountBalance,
                                    title = when (uiState.filter) {
                                        AccountListFilter.COUNTERPARTY ->
                                            stringResource(R.string.empty_parties_title)
                                        else -> stringResource(R.string.empty_accounts_title)
                                    },
                                    message = when (uiState.filter) {
                                        AccountListFilter.COUNTERPARTY ->
                                            stringResource(R.string.empty_parties_message)
                                        else -> stringResource(R.string.empty_accounts_message)
                                    },
                                    actionLabel = if (guided) {
                                        stringResource(R.string.empty_add_account)
                                    } else {
                                        null
                                    },
                                    onAction = if (guided) {
                                        { showAddMenu = true }
                                    } else {
                                        null
                                    },
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
                                                    AccountListFilter.ALL -> stringResource(
                                                        R.string.accounts_summary_owned_accounts,
                                                    )
                                                    else -> stringResource(R.string.accounts_summary_in_view)
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
                                        text = stringResource(R.string.accounts_empty_in_view),
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
private fun accountsListSubtitle(uiState: AccountsUiState): String = when (uiState.filter) {
    AccountListFilter.ALL -> stringResource(
        R.string.accounts_subtitle_all,
        uiState.ownedCount,
        uiState.counterpartyTotal,
    )
    AccountListFilter.OWNED -> stringResource(
        R.string.accounts_subtitle_owned,
        uiState.ownedCount,
    )
    AccountListFilter.COUNTERPARTY -> when {
        uiState.counterpartySearchQuery.isNotBlank() ->
            stringResource(R.string.accounts_subtitle_search_results)
        uiState.counterpartyTotal > 0 -> {
            val loaded = uiState.counterpartyLoadedCount
            val total = uiState.counterpartyTotal
            if (loaded < total) {
                stringResource(R.string.accounts_subtitle_showing_parties, loaded, total)
            } else {
                stringResource(R.string.accounts_subtitle_parties_count, total)
            }
        }
        else -> stringResource(R.string.accounts_subtitle_creditors_debtors)
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
            label = { Text(stringResource(R.string.accounts_filter_all)) },
        )
        FilterChip(
            selected = filter == AccountListFilter.OWNED,
            onClick = { onFilterSelected(AccountListFilter.OWNED) },
            label = { Text(stringResource(R.string.accounts_filter_owned, ownedCount)) },
        )
        FilterChip(
            selected = filter == AccountListFilter.COUNTERPARTY,
            onClick = { onFilterSelected(AccountListFilter.COUNTERPARTY) },
            label = {
                Text(
                    if (counterpartyTotal > 0) {
                        stringResource(R.string.accounts_filter_parties_count, counterpartyTotal)
                    } else {
                        stringResource(R.string.accounts_filter_parties)
                    },
                )
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
        placeholder = { Text(stringResource(R.string.accounts_search_parties)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.transaction_tags_clear_search),
                    )
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
                text = stringResource(R.string.accounts_counterparties_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (total > 0 && loaded < total) {
                    stringResource(R.string.accounts_counterparties_showing, loaded, total)
                } else {
                    stringResource(R.string.accounts_counterparties_loaded, loaded)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.accounts_counterparties_hint),
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
                    text = stringResource(R.string.accounts_counterparties_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.accounts_counterparties_browse, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.content_description_browse_parties),
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
    subtitle: String,
) {
    PledgerCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.accounts_total_balance),
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
                    text = stringResource(R.string.accounts_count, accountCount),
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
                    text = section.group.localizedTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = section.group.localizedDescription(),
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
                    text = meta.localizedDisplayName(),
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
