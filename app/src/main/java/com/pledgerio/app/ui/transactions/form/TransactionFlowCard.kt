package com.pledgerio.app.ui.transactions.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.transactions.AccountInputKind

@Composable
fun TransactionFlowCard(
    sourceLabel: String,
    targetLabel: String,
    helperText: String,
    sourceKind: AccountInputKind,
    targetKind: AccountInputKind,
    sourceAccountId: Long?,
    sourceSelected: FilterOption?,
    sourceQuery: String,
    sourceSuggestions: List<FilterOption>,
    isSearchingSource: Boolean,
    targetAccountId: Long?,
    targetSelected: FilterOption?,
    targetQuery: String,
    targetSuggestions: List<FilterOption>,
    isSearchingTarget: Boolean,
    ownedAccounts: List<Account>,
    sourceError: String?,
    targetError: String?,
    onSourceQueryChange: (String) -> Unit,
    onSourceAutocompleteSelected: (FilterOption) -> Unit,
    onSourceAutocompleteClear: () -> Unit,
    onSourceDropdownSelected: (Long) -> Unit,
    onTargetQueryChange: (String) -> Unit,
    onTargetAutocompleteSelected: (FilterOption) -> Unit,
    onTargetAutocompleteClear: () -> Unit,
    onTargetDropdownSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    PledgerCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransactionAccountSlot(
                label = sourceLabel,
                kind = sourceKind,
                selectedId = sourceAccountId,
                selectedOption = sourceSelected,
                query = sourceQuery,
                suggestions = sourceSuggestions,
                isSearching = isSearchingSource,
                ownedAccounts = ownedAccounts,
                error = sourceError,
                onQueryChange = onSourceQueryChange,
                onAutocompleteSelected = onSourceAutocompleteSelected,
                onAutocompleteClear = onSourceAutocompleteClear,
                onDropdownSelected = onSourceDropdownSelected,
                modifier = Modifier.fillMaxWidth(),
            )

            Icon(
                imageVector = Icons.Default.South,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(20.dp),
            )

            TransactionAccountSlot(
                label = targetLabel,
                kind = targetKind,
                selectedId = targetAccountId,
                selectedOption = targetSelected,
                query = targetQuery,
                suggestions = targetSuggestions,
                isSearching = isSearchingTarget,
                ownedAccounts = ownedAccounts,
                error = targetError,
                onQueryChange = onTargetQueryChange,
                onAutocompleteSelected = onTargetAutocompleteSelected,
                onAutocompleteClear = onTargetAutocompleteClear,
                onDropdownSelected = onTargetDropdownSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
