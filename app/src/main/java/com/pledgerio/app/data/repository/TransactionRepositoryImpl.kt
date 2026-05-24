package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.entity.TransactionEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CreateTransactionRequest
import com.pledgerio.app.data.remote.dto.TransactionDto
import com.pledgerio.app.data.remote.dto.TransactionExtract
import com.pledgerio.app.data.remote.dto.TransactionSplitDto
import com.pledgerio.app.domain.model.TransactionSplit
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionClassificationSuggestion
import com.pledgerio.app.domain.model.TransactionExtractionDraft
import com.pledgerio.app.domain.model.TransactionFilters
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.PagedResult
import com.pledgerio.app.domain.repository.TransactionRepository
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.formatApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override suspend fun getTransactionsPage(
        startDate: LocalDate,
        endDate: LocalDate,
        accountId: Long?,
        type: TransactionType?,
        filters: TransactionFilters,
        page: Int,
        pageSize: Int,
        offset: Int?,
    ): Resource<PagedResult<Transaction>> {
        return try {
            val apiOffset = offset ?: (page * pageSize)
            val apiType = when (type) {
                TransactionType.DEBIT -> "INCOME"
                TransactionType.CREDIT -> "EXPENSE"
                TransactionType.TRANSFER -> "TRANSFER"
                null -> null
            }
            val response = apiService.getTransactions(
                startDate = startDate.formatApi(),
                endDate = endDate.formatApi(),
                accounts = accountId?.let { listOf(it) },
                type = apiType,
                expenses = filters.expenseId?.let { listOf(it) },
                categories = filters.categoryId?.let { listOf(it) },
                contracts = filters.contractId?.let { listOf(it) },
                description = filters.description,
                offset = apiOffset,
                numberOfResults = pageSize,
            )
            if (response.isSuccessful) {
                val body = response.body()
                val transactions = body?.content?.map { it.toDomain() } ?: emptyList()
                val info = body?.info

                val isUnfilteredGlobalFetch = page == 0 &&
                    accountId == null &&
                    type == null &&
                    filters == TransactionFilters()
                if (isUnfilteredGlobalFetch) {
                    transactionDao.deleteAll()
                }
                if (transactions.isNotEmpty()) {
                    transactionDao.insertAll(transactions.map { TransactionEntity.fromDomain(it) })
                }

                Resource.Success(
                    PagedResult(
                        items = transactions,
                        totalRecords = info?.records ?: transactions.size.toLong(),
                        totalPages = info?.pages ?: 1,
                        pageSize = info?.pageSize ?: pageSize,
                    )
                )
            } else {
                Resource.Error("Failed to fetch transactions: ${response.code()}")
            }
        } catch (e: Exception) {
            val hasIdBasedFilters = filters.categoryId != null ||
                filters.expenseId != null ||
                filters.contractId != null
            if (hasIdBasedFilters) {
                return Resource.Error(
                    "Filtered offline search is not available yet. Reconnect to apply category/expense/contract filters.",
                )
            }
            if (page == 0) {
                val cached = transactionDao.getAllOnce()
                    .map { it.toDomain() }
                    .filter { tx ->
                        !tx.date.isBefore(startDate) && !tx.date.isAfter(endDate) &&
                            (type == null || tx.type == type) &&
                            (
                                filters.description.isNullOrBlank() ||
                                    tx.description.contains(filters.description, ignoreCase = true)
                                )
                    }
                if (cached.isNotEmpty()) {
                    return Resource.Success(
                        PagedResult(
                            items = cached,
                            totalRecords = cached.size.toLong(),
                            totalPages = 1,
                            pageSize = cached.size,
                        )
                    )
                }
            }
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getTransaction(id: Long): Resource<Transaction> {
        return try {
            val response = apiService.getTransaction(id)
            if (response.isSuccessful) {
                val dto = response.body() ?: return Resource.Error("Transaction not found")
                Resource.Success(dto.toDomain())
            } else {
                val cached = transactionDao.getById(id)
                if (cached != null) Resource.Success(cached.toDomain())
                else Resource.Error("Failed to fetch transaction")
            }
        } catch (e: Exception) {
            val cached = transactionDao.getById(id)
            if (cached != null) Resource.Success(cached.toDomain())
            else Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun createTransaction(transaction: Transaction): Resource<Transaction> {
        return try {
            val request = CreateTransactionRequest(
                date = transaction.date.formatApi(),
                currency = transaction.currency,
                description = transaction.description,
                amount = transaction.amount,
                source = transaction.sourceAccountId ?: 0,
                target = transaction.destinationAccountId ?: 0,
                category = transaction.categoryId,
                expense = transaction.expenseId,
                contract = transaction.contractId,
                tags = transaction.tags.ifEmpty { null },
            )
            val response = apiService.createTransaction(request)
            if (response.isSuccessful) {
                val created = response.body()?.toDomain() ?: return Resource.Error("Invalid response")
                transactionDao.insert(TransactionEntity.fromDomain(created))
                Resource.Success(created)
            } else {
                Resource.Error("Failed to create transaction: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun updateTransaction(transaction: Transaction): Resource<Transaction> {
        return try {
            val request = CreateTransactionRequest(
                date = transaction.date.formatApi(),
                currency = transaction.currency,
                description = transaction.description,
                amount = transaction.amount,
                source = transaction.sourceAccountId ?: 0,
                target = transaction.destinationAccountId ?: 0,
                category = transaction.categoryId,
                expense = transaction.expenseId,
                contract = transaction.contractId,
                tags = transaction.tags.ifEmpty { null },
            )
            val response = apiService.updateTransaction(transaction.id, request)
            if (response.isSuccessful) {
                val updated = response.body()?.toDomain() ?: transaction
                transactionDao.insert(TransactionEntity.fromDomain(updated))
                Resource.Success(updated)
            } else {
                Resource.Error("Failed to update transaction: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun patchTransactionSplits(
        id: Long,
        splits: List<TransactionSplit>,
    ): Resource<Transaction> {
        return try {
            val body = splits.map { TransactionSplitDto(description = it.description, amount = it.amount) }
            val response = apiService.patchTransaction(id, body)
            if (response.isSuccessful) {
                val updated = response.body()?.toDomain() ?: return Resource.Error("Invalid response")
                transactionDao.insert(TransactionEntity.fromDomain(updated))
                Resource.Success(updated)
            } else {
                Resource.Error("Failed to update split: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun deleteTransaction(id: Long): Resource<Unit> {
        return try {
            val response = apiService.deleteTransaction(id)
            if (response.isSuccessful) {
                transactionDao.getById(id)?.let { transactionDao.delete(it) }
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete transaction: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun suggestClassifications(
        amount: Double?,
        description: String?,
        source: String?,
        destination: String?,
    ): Resource<TransactionClassificationSuggestion> {
        return try {
            val response = apiService.suggestClassifications(
                amount = amount,
                description = description,
                source = source,
                destination = destination,
            )
            if (response.isSuccessful) {
                val suggestion = response.body()
                Resource.Success(
                    TransactionClassificationSuggestion(
                        budget = suggestion?.budget,
                        category = suggestion?.category,
                        tags = suggestion?.tags.orEmpty(),
                    ),
                )
            } else {
                Resource.Error("Failed to classify transaction: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun extractTransactionFromText(text: String): Resource<TransactionExtractionDraft> {
        if (text.isBlank()) return Resource.Error("No text found in document")
        return try {
            val response = apiService.extractTransactionFromText(
                request = TransactionExtract(text),
            )
            if (response.isSuccessful) {
                val body = response.body().orEmpty()
                val sourceMap = body.readMap("source")
                val targetMap = body.readMap("target")
                val metadata = body.readMap("metadata")
                val rawDate = body.readString("date") ?: body.readString("transactionDate")
                Resource.Success(
                    TransactionExtractionDraft(
                        description = body.readString("description") ?: body.readString("title"),
                        amount = body.readDouble("amount") ?: metadata.readDouble("amount"),
                        currency = body.readString("currency"),
                        date = rawDate?.let(::parseLocalDateOrNull),
                        type = body.readString("type")?.let(::parseExtractionType),
                        sourceName = body.readString("sourceName")
                            ?: sourceMap.readString("name")
                            ?: sourceMap.readString("label"),
                        targetName = body.readString("targetName")
                            ?: body.readString("counterparty")
                            ?: targetMap.readString("name")
                            ?: targetMap.readString("label")
                            ?: body.readString("merchant"),
                        confidence = body.readDouble("confidence")
                            ?: metadata.readDouble("confidence"),
                        rawText = text,
                    ),
                )
            } else {
                Resource.Error("Failed to extract transaction from text: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override fun getRecentTransactions(limit: Int): Flow<Resource<List<Transaction>>> = flow {
        emit(Resource.Loading)
        try {
            val today = LocalDate.now()
            // The API treats endDate as exclusive (same as balance queries in
            // AccountRepositoryImpl, which uses today.plusDays(1)). Using today here
            // excludes every transaction dated today from the dashboard list.
            val response = apiService.getTransactions(
                startDate = today.minusMonths(1).formatApi(),
                endDate = today.plusDays(1).formatApi(),
                offset = 0,
                numberOfResults = limit.coerceAtLeast(25),
            )
            if (response.isSuccessful) {
                val transactions = response.body()?.content
                    ?.map { it.toDomain() }
                    ?.sortedByDescending { it.date }
                    ?.take(limit)
                    ?: emptyList()
                transactionDao.insertAll(transactions.map { TransactionEntity.fromDomain(it) })
                emit(Resource.Success(transactions))
            } else {
                emit(Resource.Error("Failed to fetch transactions"))
            }
        } catch (e: Exception) {
            transactionDao.getRecent(limit).collect { entities ->
                emit(Resource.Success(entities.map { it.toDomain() }))
            }
        }
    }

    private fun TransactionDto.toDomain(): Transaction {
        return Transaction(
            id = id,
            description = description,
            amount = amount,
            currency = currency,
            type = TransactionType.fromString(type),
            date = dates?.transaction?.let { LocalDate.parse(it) } ?: LocalDate.now(),
            sourceAccountId = source?.id,
            sourceAccountName = source?.name ?: "",
            destinationAccountId = destination?.id,
            destinationAccountName = destination?.name ?: "",
            categoryName = metadata?.category,
            budgetName = metadata?.budget,
            contractName = metadata?.contract,
            tags = metadata?.tags ?: emptyList(),
            split = split?.map { TransactionSplit(description = it.description, amount = it.amount) }
                ?: emptyList(),
        )
    }

    private fun parseLocalDateOrNull(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun parseExtractionType(value: String): TransactionType? {
        return when (value.trim().uppercase()) {
            "CREDIT", "EXPENSE" -> TransactionType.CREDIT
            "DEBIT", "INCOME" -> TransactionType.DEBIT
            "TRANSFER" -> TransactionType.TRANSFER
            else -> null
        }
    }

    private fun Map<String, Any?>.readString(key: String): String? {
        val value = this[key] ?: return null
        return (value as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun Map<String, Any?>.readDouble(key: String): Double? {
        val value = this[key] ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun Map<String, Any?>.readMap(key: String): Map<String, Any?> {
        val value = this[key] ?: return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: emptyMap()
    }
}
