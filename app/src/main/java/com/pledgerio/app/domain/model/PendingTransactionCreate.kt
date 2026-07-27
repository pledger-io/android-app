package com.pledgerio.app.domain.model

import java.time.LocalDate

enum class OutboxStatus {
    PENDING,
    FAILED,
    ;

    companion object {
        fun fromStorage(value: String): OutboxStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

data class PendingTransactionCreate(
    val localId: String,
    val createdAtMillis: Long,
    val status: OutboxStatus,
    val lastError: String?,
    val attemptCount: Int,
    val date: LocalDate,
    val currency: String,
    val description: String,
    val amount: Double,
    val sourceAccountId: Long,
    val destinationAccountId: Long,
    val categoryId: Long? = null,
    val expenseId: Long? = null,
    val contractId: Long? = null,
    val tags: List<String> = emptyList(),
    val displaySourceName: String? = null,
    val displayDestinationName: String? = null,
    val displayCategoryName: String? = null,
    val type: TransactionType? = null,
)

sealed class CreateOutcome {
    data class Synced(val transaction: Transaction) : CreateOutcome()
    data class Queued(val pending: PendingTransactionCreate) : CreateOutcome()
}

sealed class FlushResult {
    data object Completed : FlushResult()
    data object AbortedStaleSession : FlushResult()
    data object StoppedOnNetworkError : FlushResult()
}
