package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionSplit

internal fun buildStateAfterEditLoad(
    current: TransactionFormUiState,
    tx: Transaction,
    sourceKind: AccountInputKind,
    targetKind: AccountInputKind,
    categorySelected: FilterOption?,
    expenseSelected: FilterOption?,
    contractSelected: FilterOption?,
    splitLines: List<TransactionSplitLineUi>,
): TransactionFormUiState {
    val sourceSelected = tx.toAutocompleteSelection(
        isSource = true,
        inputKind = sourceKind,
    )
    val targetSelected = tx.toAutocompleteSelection(
        isSource = false,
        inputKind = targetKind,
    )
    val splitSnapshot: List<TransactionSplit> = tx.split

    return current.copy(
        isLoading = false,
        type = tx.type,
        description = tx.description,
        amount = tx.amount.toString(),
        currency = tx.currency,
        date = tx.date,
        sourceAccountId = tx.sourceAccountId,
        sourceSelected = sourceSelected,
        sourceQuery = tx.sourceAccountName,
        targetAccountId = tx.destinationAccountId,
        targetSelected = targetSelected,
        targetQuery = tx.destinationAccountName,
        categorySelected = categorySelected,
        categoryQuery = categorySelected?.label ?: tx.categoryName.orEmpty(),
        expenseSelected = expenseSelected,
        expenseQuery = expenseSelected?.label ?: tx.budgetName.orEmpty(),
        contractSelected = contractSelected,
        contractQuery = contractSelected?.label ?: tx.contractName.orEmpty(),
        tags = tx.tags,
        splitLines = splitLines,
        originalSplitSnapshot = splitSnapshot,
        splitSectionExpanded = splitSnapshot.isNotEmpty(),
        moreOptionsExpanded = tx.tags.isNotEmpty() ||
            tx.categoryName != null ||
            tx.budgetName != null ||
            tx.contractName != null,
        moreOptionsManuallyToggled = true,
    )
}

private fun Transaction.toAutocompleteSelection(
    isSource: Boolean,
    inputKind: AccountInputKind,
): FilterOption? {
    if (inputKind == AccountInputKind.OWNED_DROPDOWN) return null
    val accountId = if (isSource) sourceAccountId else destinationAccountId
    val accountName = if (isSource) sourceAccountName else destinationAccountName
    return accountId?.let { FilterOption(id = it, label = accountName) }
}
