package com.pledgerio.app.transactions.domain.model

data class TransactionClassificationSuggestion(
    val budget: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
)
