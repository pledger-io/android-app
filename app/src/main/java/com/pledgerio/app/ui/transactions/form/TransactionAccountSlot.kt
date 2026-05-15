package com.pledgerio.app.ui.transactions.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.ui.components.AccountIcon
import com.pledgerio.app.ui.transactions.AccountInputKind
import com.pledgerio.app.ui.transactions.FilterAutocompleteField
import com.pledgerio.app.util.formatCurrency

@Composable
fun TransactionAccountSlot(
    label: String,
    kind: AccountInputKind,
    selectedId: Long?,
    selectedOption: FilterOption?,
    query: String,
    suggestions: List<FilterOption>,
    isSearching: Boolean,
    ownedAccounts: List<Account>,
    error: String?,
    onQueryChange: (String) -> Unit,
    onAutocompleteSelected: (FilterOption) -> Unit,
    onAutocompleteClear: () -> Unit,
    onDropdownSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (kind) {
            AccountInputKind.CREDITOR_AUTOCOMPLETE,
            AccountInputKind.DEBTOR_AUTOCOMPLETE,
            -> {
                FilterAutocompleteField(
                    label = "",
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
                OwnedAccountPicker(
                    accounts = ownedAccounts,
                    selectedId = selectedId,
                    isError = error != null,
                    onSelected = onDropdownSelected,
                )
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnedAccountPicker(
    accounts: List<Account>,
    selectedId: Long?,
    isError: Boolean,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Choose account") },
            isError = isError,
            leadingIcon = selected?.let { account ->
                {
                    AccountIcon(
                        iconFileCode = account.iconFileCode,
                        size = 32.dp,
                        contentDescription = null,
                    )
                }
            },
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
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("No accounts available") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            } else {
                accounts.forEach { account ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AccountIcon(
                                    iconFileCode = account.iconFileCode,
                                    size = 36.dp,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(account.name)
                                    Text(
                                        text = "${account.typeDisplayName} · ${account.balance.formatCurrency(account.currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
