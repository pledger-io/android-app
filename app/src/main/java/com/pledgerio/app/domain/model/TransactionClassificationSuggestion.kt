package com.pledgerio.app.domain.model

data class TransactionClassificationSuggestion(
    val budget: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
)
