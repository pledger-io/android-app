package com.pledgerio.app.domain.model

data class BudgetListState(
    val budgets: List<Budget> = emptyList(),
    val needsInitialSetup: Boolean = false,
    /** Expected monthly net income when known from the API; null when only Room groups are cached. */
    val income: Double? = null,
)
