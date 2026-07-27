package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_outbox")
data class TransactionOutboxEntity(
    @PrimaryKey val localId: String,
    val createdAtMillis: Long,
    val status: String,
    val lastError: String? = null,
    val attemptCount: Int = 0,
    val date: String,
    val currency: String,
    val description: String,
    val amount: Double,
    val sourceAccountId: Long,
    val destinationAccountId: Long,
    val categoryId: Long? = null,
    val expenseId: Long? = null,
    val contractId: Long? = null,
    val tagsJson: String? = null,
    val displaySourceName: String? = null,
    val displayDestinationName: String? = null,
    val displayCategoryName: String? = null,
    val type: String? = null,
)
