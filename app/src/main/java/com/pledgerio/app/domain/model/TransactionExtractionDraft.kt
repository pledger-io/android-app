package com.pledgerio.app.domain.model

import java.time.LocalDate

data class TransactionExtractionDraft(
    val description: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val date: LocalDate? = null,
    val type: TransactionType? = null,
    val sourceName: String? = null,
    val targetName: String? = null,
    val confidence: Double? = null,
    val rawText: String? = null,
)
