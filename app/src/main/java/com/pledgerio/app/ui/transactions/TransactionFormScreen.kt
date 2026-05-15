package com.pledgerio.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.ui.components.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Transaction") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.isSaving) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = viewModel::onDescriptionChanged,
                        label = { Text("Description *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = uiState.amount,
                        onValueChange = viewModel::onAmountChanged,
                        label = { Text("Amount *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    OutlinedTextField(
                        value = uiState.date,
                        onValueChange = viewModel::onDateChanged,
                        label = { Text("Date (YYYY-MM-DD) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Type *",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.type == TransactionType.DEBIT,
                            onClick = { viewModel.onTypeChanged(TransactionType.DEBIT) },
                            label = { Text("Income") },
                        )
                        FilterChip(
                            selected = uiState.type == TransactionType.CREDIT,
                            onClick = { viewModel.onTypeChanged(TransactionType.CREDIT) },
                            label = { Text("Expense") },
                        )
                        FilterChip(
                            selected = uiState.type == TransactionType.TRANSFER,
                            onClick = { viewModel.onTypeChanged(TransactionType.TRANSFER) },
                            label = { Text("Transfer") },
                        )
                    }

                    TransactionAccountField(
                        label = "From account *",
                        kind = uiState.sourceInputKind,
                        selectedId = uiState.sourceAccountId,
                        selectedOption = uiState.sourceSelected,
                        query = uiState.sourceQuery,
                        suggestions = uiState.sourceSuggestions,
                        isSearching = uiState.isSearchingSource,
                        ownedAccounts = uiState.ownedAccounts,
                        onQueryChange = viewModel::onSourceQueryChanged,
                        onAutocompleteSelected = viewModel::selectSourceAutocomplete,
                        onAutocompleteClear = viewModel::clearSourceAccount,
                        onDropdownSelected = viewModel::onSourceDropdownSelected,
                    )

                    TransactionAccountField(
                        label = "To account *",
                        kind = uiState.targetInputKind,
                        selectedId = uiState.targetAccountId,
                        selectedOption = uiState.targetSelected,
                        query = uiState.targetQuery,
                        suggestions = uiState.targetSuggestions,
                        isSearching = uiState.isSearchingTarget,
                        ownedAccounts = uiState.ownedAccounts,
                        onQueryChange = viewModel::onTargetQueryChanged,
                        onAutocompleteSelected = viewModel::selectTargetAutocomplete,
                        onAutocompleteClear = viewModel::clearTargetAccount,
                        onDropdownSelected = viewModel::onTargetDropdownSelected,
                    )

                    StringDropdown(
                        label = "Currency *",
                        options = uiState.currencies,
                        selected = uiState.currency,
                        onSelected = viewModel::onCurrencyChanged,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = viewModel::save,
                        enabled = uiState.canSubmit && !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create Transaction")
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TransactionAccountField(
    label: String,
    kind: AccountInputKind,
    selectedId: Long?,
    selectedOption: FilterOption?,
    query: String,
    suggestions: List<FilterOption>,
    isSearching: Boolean,
    ownedAccounts: List<Account>,
    onQueryChange: (String) -> Unit,
    onAutocompleteSelected: (FilterOption) -> Unit,
    onAutocompleteClear: () -> Unit,
    onDropdownSelected: (Long) -> Unit,
) {
    when (kind) {
        AccountInputKind.CREDITOR_AUTOCOMPLETE,
        AccountInputKind.DEBTOR_AUTOCOMPLETE,
        -> {
            FilterAutocompleteField(
                label = label,
                query = query,
                selected = selectedOption,
                suggestions = suggestions,
                isLoading = isSearching,
                onQueryChange = onQueryChange,
                onSelected = onAutocompleteSelected,
                onClear = onAutocompleteClear,
            )
        }
        AccountInputKind.OWNED_DROPDOWN -> {
            OwnedAccountDropdown(
                label = label,
                accounts = ownedAccounts,
                selectedId = selectedId,
                onSelected = onDropdownSelected,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnedAccountDropdown(
    label: String,
    accounts: List<Account>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = accounts.find { it.id == selectedId }?.name ?: "Select account"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (accounts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No accounts available") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            } else {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(account.name)
                                Text(
                                    text = account.typeDisplayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelected(account.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StringDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
