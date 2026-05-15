package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionType
import java.time.LocalDate

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: Long,
    val description: String,
    val amount: Double,
    val currency: String = "EUR",
    val type: String,
    val date: LocalDate,
    val sourceAccountId: Long? = null,
    val sourceAccountName: String = "",
    val destinationAccountId: Long? = null,
    val destinationAccountName: String = "",
    val categoryName: String? = null,
    val budgetName: String? = null,
    val tags: List<String> = emptyList(),
    val lastSynced: Long = System.currentTimeMillis(),
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        description = description,
        amount = amount,
        currency = currency,
        type = TransactionType.fromString(type),
        date = date,
        sourceAccountId = sourceAccountId,
        sourceAccountName = sourceAccountName,
        destinationAccountId = destinationAccountId,
        destinationAccountName = destinationAccountName,
        categoryName = categoryName,
        budgetName = budgetName,
        tags = tags,
    )

    companion object {
        fun fromDomain(transaction: Transaction): TransactionEntity = TransactionEntity(
            id = transaction.id,
            description = transaction.description,
            amount = transaction.amount,
            currency = transaction.currency,
            type = transaction.type.name,
            date = transaction.date,
            sourceAccountId = transaction.sourceAccountId,
            sourceAccountName = transaction.sourceAccountName,
            destinationAccountId = transaction.destinationAccountId,
            destinationAccountName = transaction.destinationAccountName,
            categoryName = transaction.categoryName,
            budgetName = transaction.budgetName,
            tags = transaction.tags,
        )
    }
}
