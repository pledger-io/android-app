package com.pledgerio.app.domain.model

data class TransactionFilters(
    val categoryId: Long? = null,
    val expenseId: Long? = null,
    val contractId: Long? = null,
) {
    val hasAny: Boolean
        get() = categoryId != null || expenseId != null || contractId != null
}

data class FilterOption(
    val id: Long,
    val label: String,
)
