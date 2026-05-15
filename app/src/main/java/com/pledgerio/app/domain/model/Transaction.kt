package com.pledgerio.app.domain.model

import java.time.LocalDate

data class Transaction(
    val id: Long,
    val description: String,
    val amount: Double,
    val currency: String = "EUR",
    val type: TransactionType,
    val date: LocalDate,
    val sourceAccountId: Long? = null,
    val sourceAccountName: String = "",
    val destinationAccountId: Long? = null,
    val destinationAccountName: String = "",
    val categoryName: String? = null,
    val budgetName: String? = null,
    val contractName: String? = null,
    val tags: List<String> = emptyList(),
) {
    val displayAccountName: String
        get() = when (type) {
            TransactionType.CREDIT -> sourceAccountName
            TransactionType.DEBIT -> destinationAccountName
            TransactionType.TRANSFER -> "$sourceAccountName → $destinationAccountName"
        }

    val primaryAccountId: Long
        get() = when (type) {
            TransactionType.CREDIT -> sourceAccountId ?: 0
            TransactionType.DEBIT -> destinationAccountId ?: 0
            TransactionType.TRANSFER -> sourceAccountId ?: 0
        }
}

enum class TransactionType {
    CREDIT,
    DEBIT,
    TRANSFER;

    companion object {
        fun fromString(value: String): TransactionType = when (value.uppercase()) {
            "CREDIT", "INCOME" -> CREDIT
            "DEBIT", "EXPENSE" -> DEBIT
            "TRANSFER" -> TRANSFER
            else -> DEBIT
        }
    }
}
