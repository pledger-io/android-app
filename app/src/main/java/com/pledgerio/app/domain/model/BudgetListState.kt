package com.pledgerio.app.domain.model

data class BudgetListState(
    val budgets: List<Budget> = emptyList(),
    val needsInitialSetup: Boolean = false,
)
