package com.pledgerio.app.data.cache

import java.time.YearMonth

/**
 * Keys used to track cache freshness in `sync_metadata`. Keeping them in one place avoids
 * typos and makes it easy to invalidate related caches together.
 */
object SyncKeys {
    const val OWNED_ACCOUNTS = "owned_accounts"
    const val COUNTERPARTY_ACCOUNTS = "counterparty_accounts"
    const val ACCOUNT_TYPES = "account_types"
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
    const val EXPENSE_GROUPS = "expense_groups"
    const val CONTRACTS = "contracts"
    const val CURRENCIES = "currencies"

    fun budgetMonth(month: YearMonth): String = "budget_$month"
}
