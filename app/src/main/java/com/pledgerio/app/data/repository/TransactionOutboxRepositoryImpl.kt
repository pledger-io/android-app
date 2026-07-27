package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.dao.TransactionOutboxDao
import com.pledgerio.app.data.local.entity.TransactionEntity
import com.pledgerio.app.data.local.entity.TransactionOutboxEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CreateTransactionRequest
import com.pledgerio.app.data.remote.dto.TransactionDto
import com.pledgerio.app.domain.model.FlushResult
import com.pledgerio.app.domain.model.OutboxStatus
import com.pledgerio.app.domain.model.PendingTransactionCreate
import com.pledgerio.app.domain.model.Transaction
import com.pledgerio.app.domain.model.TransactionSplit
import com.pledgerio.app.domain.model.TransactionType
import com.pledgerio.app.domain.repository.TransactionOutboxRepository
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SyncSessionGuard
import com.pledgerio.app.util.formatApi
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TransactionOutboxRepositoryImpl @Inject constructor(
    private val outboxDao: TransactionOutboxDao,
    private val transactionDao: TransactionDao,
    private val apiService: PledgerApiService,
    private val mutationInvalidator: TransactionMutationInvalidator,
    private val sessionGuard: SyncSessionGuard,
) : TransactionOutboxRepository {

    override fun observePending(): Flow<List<PendingTransactionCreate>> =
        outboxDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueueCreate(transaction: Transaction): Resource<PendingTransactionCreate> {
        return try {
            val entity = transaction.toOutboxEntity()
            outboxDao.insert(entity)
            Resource.Success(entity.toDomain())
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to queue transaction")
        }
    }

    override suspend fun discard(localId: String): Resource<Unit> {
        return try {
            outboxDao.deleteByLocalId(localId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to discard queued transaction")
        }
    }

    override suspend fun flushPending(generation: String): FlushResult {
        if (!sessionGuard.isCurrent(generation)) return FlushResult.AbortedStaleSession

        val pending = outboxDao.getByStatus(OutboxStatus.PENDING.name)
        for (entity in pending) {
            if (!sessionGuard.isCurrent(generation)) return FlushResult.AbortedStaleSession

            val request = entity.toCreateRequest()
            try {
                val response = apiService.createTransaction(request)
                if (response.isSuccessful) {
                    val created = response.body()?.toDomain()
                    if (created == null) {
                        markFailed(entity, "Invalid response while syncing queued transaction")
                    } else {
                        transactionDao.insert(TransactionEntity.fromDomain(created))
                        runCatching { mutationInvalidator.invalidate(created.date) }
                        outboxDao.deleteByLocalId(entity.localId)
                    }
                } else if (response.code() in 400..499) {
                    markFailed(entity, "Failed to sync: HTTP ${response.code()}")
                } else {
                    // 5xx — leave PENDING for a later cycle
                    return FlushResult.StoppedOnNetworkError
                }
            } catch (_: IOException) {
                return FlushResult.StoppedOnNetworkError
            } catch (e: Exception) {
                markFailed(entity, e.message ?: "Failed to sync queued transaction")
            }
        }
        return FlushResult.Completed
    }

    private suspend fun markFailed(entity: TransactionOutboxEntity, message: String) {
        outboxDao.updateStatus(
            localId = entity.localId,
            status = OutboxStatus.FAILED.name,
            lastError = message,
            attemptCount = entity.attemptCount + 1,
        )
    }

    private fun Transaction.toOutboxEntity(): TransactionOutboxEntity {
        val sourceId = sourceAccountId
            ?: error("Source account is required to queue a transaction")
        val destinationId = destinationAccountId
            ?: error("Destination account is required to queue a transaction")
        return TransactionOutboxEntity(
            localId = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
            status = OutboxStatus.PENDING.name,
            lastError = null,
            attemptCount = 0,
            date = date.formatApi(),
            currency = currency,
            description = description,
            amount = amount,
            sourceAccountId = sourceId,
            destinationAccountId = destinationId,
            categoryId = categoryId,
            expenseId = expenseId,
            contractId = contractId,
            tagsJson = tags.takeIf { it.isNotEmpty() }?.let { encodeTags(it) },
            displaySourceName = sourceAccountName.takeIf { it.isNotBlank() },
            displayDestinationName = destinationAccountName.takeIf { it.isNotBlank() },
            displayCategoryName = categoryName?.takeIf { it.isNotBlank() },
            type = type.name,
        )
    }

    private fun TransactionOutboxEntity.toDomain(): PendingTransactionCreate =
        PendingTransactionCreate(
            localId = localId,
            createdAtMillis = createdAtMillis,
            status = OutboxStatus.fromStorage(status),
            lastError = lastError,
            attemptCount = attemptCount,
            date = LocalDate.parse(date),
            currency = currency,
            description = description,
            amount = amount,
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            categoryId = categoryId,
            expenseId = expenseId,
            contractId = contractId,
            tags = decodeTags(tagsJson),
            displaySourceName = displaySourceName,
            displayDestinationName = displayDestinationName,
            displayCategoryName = displayCategoryName,
            type = type?.let { TransactionType.fromString(it) },
        )

    private fun TransactionOutboxEntity.toCreateRequest(): CreateTransactionRequest =
        CreateTransactionRequest(
            date = date,
            currency = currency,
            description = description,
            amount = amount,
            source = sourceAccountId,
            target = destinationAccountId,
            category = categoryId,
            expense = expenseId,
            contract = contractId,
            tags = decodeTags(tagsJson).ifEmpty { null },
        )

    private fun TransactionDto.toDomain(): Transaction =
        Transaction(
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
            split = split?.map {
                TransactionSplit(description = it.description, amount = it.amount)
            } ?: emptyList(),
        )

    companion object {
        fun encodeTags(tags: List<String>): String =
            tags.joinToString(prefix = "[", postfix = "]") { tag ->
                "\"${tag.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }

        fun decodeTags(tagsJson: String?): List<String> {
            if (tagsJson.isNullOrBlank()) return emptyList()
            val trimmed = tagsJson.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val body = trimmed.substring(1, trimmed.lastIndex).trim()
            if (body.isEmpty()) return emptyList()
            return body.split(',')
                .map { it.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\") }
                .filter { it.isNotEmpty() }
        }
    }
}
