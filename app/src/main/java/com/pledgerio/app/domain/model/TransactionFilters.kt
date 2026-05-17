package com.pledgerio.app.domain.model

data class TransactionFilters(
    val categoryId: Long? = null,
    val expenseId: Long? = null,
    val contractId: Long? = null,
    val description: String? = null,
) {
    val hasAny: Boolean
        get() = categoryId != null || expenseId != null || contractId != null || !description.isNullOrBlank()
}

data class FilterOption(
    val id: Long,
    val label: String,
)
