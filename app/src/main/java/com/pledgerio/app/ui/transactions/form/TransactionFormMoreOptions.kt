package com.pledgerio.app.ui.transactions.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.ui.components.PledgerCard
import com.pledgerio.app.ui.transactions.FilterAutocompleteField

@Composable
fun TransactionFormMoreOptions(
    expanded: Boolean,
    onToggle: () -> Unit,
    categoryQuery: String,
    categorySelected: FilterOption?,
    categorySuggestions: List<FilterOption>,
    isSearchingCategory: Boolean,
    onCategoryQueryChange: (String) -> Unit,
    onCategorySelected: (FilterOption) -> Unit,
    onCategoryClear: () -> Unit,
    expenseQuery: String,
    expenseSelected: FilterOption?,
    expenseSuggestions: List<FilterOption>,
    isSearchingExpense: Boolean,
    onExpenseQueryChange: (String) -> Unit,
    onExpenseSelected: (FilterOption) -> Unit,
    onExpenseClear: () -> Unit,
    contractQuery: String,
    contractSelected: FilterOption?,
    contractSuggestions: List<FilterOption>,
    isSearchingContract: Boolean,
    onContractQueryChange: (String) -> Unit,
    onContractSelected: (FilterOption) -> Unit,
    onContractClear: () -> Unit,
    tags: List<String>,
    tagInput: String,
    tagSuggestions: List<String>,
    isSearchingTags: Boolean,
    isAddingTag: Boolean,
    tagError: String?,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    showAutoClassifyAction: Boolean = false,
    canAutoClassify: Boolean = true,
    isAutoClassifying: Boolean = false,
    autoClassifyStatus: String? = null,
    onAutoClassify: () -> Unit = {},
    onSelectTagSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "More options",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (showAutoClassifyAction) {
                TextButton(
                    onClick = onAutoClassify,
                    enabled = canAutoClassify && !isAutoClassifying,
                ) {
                    if (isAutoClassifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Auto classify")
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }

        AnimatedVisibility(visible = expanded) {
            PledgerCard {
                Column {
                    Text(
                        text = "Optional classification for reports and filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = if (autoClassifyStatus == null) 12.dp else 4.dp),
                    )
                    if (autoClassifyStatus != null) {
                        Text(
                            text = autoClassifyStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    FilterAutocompleteField(
                        label = "Category",
                        query = categoryQuery,
                        selected = categorySelected,
                        suggestions = categorySuggestions,
                        isLoading = isSearchingCategory,
                        onQueryChange = onCategoryQueryChange,
                        onSelected = onCategorySelected,
                        onClear = onCategoryClear,
                    )
                    FilterAutocompleteField(
                        label = "Budget expense",
                        query = expenseQuery,
                        selected = expenseSelected,
                        suggestions = expenseSuggestions,
                        isLoading = isSearchingExpense,
                        onQueryChange = onExpenseQueryChange,
                        onSelected = onExpenseSelected,
                        onClear = onExpenseClear,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    FilterAutocompleteField(
                        label = "Contract",
                        query = contractQuery,
                        selected = contractSelected,
                        suggestions = contractSuggestions,
                        isLoading = isSearchingContract,
                        onQueryChange = onContractQueryChange,
                        onSelected = onContractSelected,
                        onClear = onContractClear,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    TransactionTagsField(
                        tags = tags,
                        input = tagInput,
                        suggestions = tagSuggestions,
                        isSearching = isSearchingTags,
                        isAdding = isAddingTag,
                        error = tagError,
                        onInputChange = onTagInputChange,
                        onAddTag = onAddTag,
                        onRemoveTag = onRemoveTag,
                        onSelectSuggestion = onSelectTagSuggestion,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
