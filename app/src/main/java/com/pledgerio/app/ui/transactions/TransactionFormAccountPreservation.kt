package com.pledgerio.app.ui.transactions

import com.pledgerio.app.domain.model.Account
import com.pledgerio.app.domain.model.FilterOption

internal data class PreservedAccountSelection(
    val accountId: Long?,
    val selected: FilterOption?,
    val query: String,
)

internal fun preserveAccountSelection(
    accountId: Long?,
    selected: FilterOption?,
    query: String,
    ownedAccounts: List<Account>,
    oldKind: AccountInputKind,
    newKind: AccountInputKind,
): PreservedAccountSelection {
    if (oldKind != newKind) return PreservedAccountSelection(null, null, "")
    return when (newKind) {
        AccountInputKind.OWNED_DROPDOWN -> {
            if (accountId != null && ownedAccounts.any { it.id == accountId }) {
                PreservedAccountSelection(accountId, null, "")
            } else {
                PreservedAccountSelection(null, null, "")
            }
        }
        AccountInputKind.CREDITOR_AUTOCOMPLETE,
        AccountInputKind.DEBTOR_AUTOCOMPLETE,
        -> {
            if (selected != null && accountId != null) {
                PreservedAccountSelection(accountId, selected, query)
            } else {
                PreservedAccountSelection(null, null, "")
            }
        }
    }
}
