package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.FilterOption
import com.pledgerio.app.domain.model.Transaction

internal fun resolveAccountDisplayName(
    accountId: Long?,
    selected: FilterOption?,
    ownedAccounts: List<Account>,
): String {
    selected?.label?.let { return it }
    return ownedAccounts.find { it.id == accountId }?.name ?: ""
}

internal fun buildTransactionForSubmit(
    state: TransactionFormUiState,
    sourceName: String,
    targetName: String,
    categoryId: Long?,
    expenseId: Long?,
    contractId: Long?,
): Transaction {
    return Transaction(
        id = state.editingTransactionId ?: 0,
        description = state.description.trim(),
        amount = state.amount.toDouble(),
        currency = state.currency,
        type = state.type,
        date = state.date,
        sourceAccountId = state.sourceAccountId,
        sourceAccountName = sourceName,
        destinationAccountId = state.targetAccountId,
        destinationAccountName = targetName,
        categoryName = state.categorySelected?.label
            ?: state.categoryQuery.trim().takeIf { it.isNotEmpty() },
        budgetName = state.expenseSelected?.label
            ?: state.expenseQuery.trim().takeIf { it.isNotEmpty() },
        contractName = state.contractSelected?.label
            ?: state.contractQuery.trim().takeIf { it.isNotEmpty() },
        categoryId = categoryId,
        expenseId = expenseId,
        contractId = contractId,
        tags = state.tags,
    )
}

internal suspend fun resolveOptionalSelectionId(
    selected: FilterOption?,
    query: String,
    resolveByName: suspend (String) -> FilterOption?,
): Long? {
    selected?.id?.let { return it }
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return null
    return resolveByName(trimmedQuery)?.id
}
