package com.pledgerio.app.data.cache

import java.util.concurrent.TimeUnit

/**
 * Stale-while-revalidate time-to-live values per cached resource. Reads still return cached
 * data after the TTL elapses; the TTL only triggers a background refresh.
 */
object CachePolicy {
    val OWNED_ACCOUNTS_TTL_MS = TimeUnit.MINUTES.toMillis(15)
    val COUNTERPARTY_ACCOUNTS_TTL_MS = TimeUnit.MINUTES.toMillis(15)
    val ACCOUNT_TYPES_TTL_MS = TimeUnit.HOURS.toMillis(24)
    val CATEGORIES_TTL_MS = TimeUnit.MINUTES.toMillis(60)
    val EXPENSE_GROUPS_TTL_MS = TimeUnit.MINUTES.toMillis(60)
    val CONTRACTS_TTL_MS = TimeUnit.MINUTES.toMillis(60)
    val CURRENCIES_TTL_MS = TimeUnit.HOURS.toMillis(24)

    fun isStale(lastSyncedAt: Long?, ttlMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (lastSyncedAt == null) return true
        return now - lastSyncedAt >= ttlMs
    }
}
