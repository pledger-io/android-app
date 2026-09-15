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
import com.pledgerio.app.util.SessionDataBarrier
import com.pledgerio.app.util.SessionManager
import com.pledgerio.app.util.SyncSessionGuard
import com.pledgerio.app.util.formatApi
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TransactionOutboxRepositoryImpl @Inject constructor(
    private val outboxDao: TransactionOutboxDao,
    private val transactionDao: TransactionDao,
    private val apiService: PledgerApiService,
    private val mutationInvalidator: TransactionMutationInvalidator,
    private val sessionGuard: SyncSessionGuard,
    private val sessionManager: SessionManager,
    private val sessionDataBarrier: SessionDataBarrier,
) : TransactionOutboxRepository {

    override fun observePending(): Flow<List<PendingTransactionCreate>> =
        outboxDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun enqueueCreate(transaction: Transaction): Resource<PendingTransactionCreate> {
        return sessionDataBarrier.withWorkerStep {
            if (sessionManager.getSyncGeneration().isNullOrBlank()) {
                return@withWorkerStep Resource.Error("Not signed in")
            }
            try {
                val entity = transaction.toOutboxEntity()
                outboxDao.insert(entity)
                Resource.Success(entity.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Failed to queue transaction")
            }
        }
    }

    override suspend fun discard(localId: String): Resource<Unit> {
        return sessionDataBarrier.withWorkerStep {
            try {
                outboxDao.deleteByLocalId(localId)
                Resource.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Failed to discard queued transaction")
            }
        }
    }

    override suspend fun flushPending(generation: String): FlushResult {
        if (!sessionGuard.isCurrent(generation)) return FlushResult.AbortedStaleSession

        val unresolved = outboxDao.getByStatus(OutboxStatus.IN_FLIGHT.name)
        for (entity in unresolved) {
            if (!sessionGuard.isCurrent(generation)) return FlushResult.AbortedStaleSession
            when (resolveUnknownOutcome(entity)) {
                UnknownOutcome.Created -> Unit
                UnknownOutcome.NotCreated -> requeue(entity)
                UnknownOutcome.Unresolved -> return FlushResult.StoppedOnNetworkError
            }
        }

        val pending = outboxDao.getByStatus(OutboxStatus.PENDING.name)
        for (entity in pending) {
            if (!sessionGuard.isCurrent(generation)) return FlushResult.AbortedStaleSession

            // A row is claimed before the POST, so a crash or unknown network outcome leaves it
            // IN_FLIGHT and the next flush reconciles it against the server instead of re-posting.
            if (outboxDao.claimForSend(
                    localId = entity.localId,
                    pendingStatus = OutboxStatus.PENDING.name,
                    inFlightStatus = OutboxStatus.IN_FLIGHT.name,
                ) == 0
            ) {
                continue
            }

            val request = entity.toCreateRequest()
            try {
                val response = apiService.createTransaction(request)
                if (response.isSuccessful) {
                    val created = response.body()?.toDomain()
                    if (created == null) {
                        // The server accepted it; drop the row rather than risk a duplicate.
                        outboxDao.deleteByLocalId(entity.localId)
                        runCatching { mutationInvalidator.invalidate(entity.parsedDate()) }
                    } else {
                        transactionDao.insert(TransactionEntity.fromDomain(created))
                        runCatching { mutationInvalidator.invalidate(created.date) }
                        outboxDao.deleteByLocalId(entity.localId)
                    }
                } else if (response.code() in 400..499) {
                    markFailed(entity, "Failed to sync: HTTP ${response.code()}")
                } else {
                    // 5xx — the request was rejected, so it is safe to send again later.
                    requeue(entity)
                    return FlushResult.StoppedOnNetworkError
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                return FlushResult.StoppedOnNetworkError
            } catch (_: Exception) {
                // The outcome of the POST is unknown, so the row stays IN_FLIGHT and is
                // reconciled against the server on the next flush.
                continue
            }
        }
        return FlushResult.Completed
    }

    private enum class UnknownOutcome { Created, NotCreated, Unresolved }

    /**
     * Determines whether a claimed row reached the server, by looking for a transaction on the
     * same day with the same accounts, amount and description.
     */
    private suspend fun resolveUnknownOutcome(entity: TransactionOutboxEntity): UnknownOutcome {
        val date = entity.parsedDate()
        return try {
            val response = apiService.getTransactions(
                startDate = date.formatApi(),
                endDate = date.plusDays(1).formatApi(),
                accounts = listOf(entity.sourceAccountId, entity.destinationAccountId),
                numberOfResults = MAX_RECONCILE_RESULTS,
            )
            if (!response.isSuccessful) return UnknownOutcome.Unresolved
            val match = response.body()?.content.orEmpty()
                .map { it.toDomain() }
                .firstOrNull { entity.matches(it) }
                ?: return UnknownOutcome.NotCreated
            transactionDao.insert(TransactionEntity.fromDomain(match))
            runCatching { mutationInvalidator.invalidate(match.date) }
            outboxDao.deleteByLocalId(entity.localId)
            UnknownOutcome.Created
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UnknownOutcome.Unresolved
        }
    }

    private fun TransactionOutboxEntity.matches(transaction: Transaction): Boolean =
        transaction.date == parsedDate() &&
            transaction.description == description &&
            kotlin.math.abs(transaction.amount - amount) < AMOUNT_TOLERANCE &&
            transaction.sourceAccountId == sourceAccountId &&
            transaction.destinationAccountId == destinationAccountId

    private fun TransactionOutboxEntity.parsedDate(): LocalDate = LocalDate.parse(date)

    private suspend fun requeue(entity: TransactionOutboxEntity) {
        val attempts = entity.attemptCount + 1
        if (attempts >= MAX_SEND_ATTEMPTS) {
            markFailed(entity, "Failed to sync after $attempts attempts")
            return
        }
        outboxDao.updateStatus(
            localId = entity.localId,
            status = OutboxStatus.PENDING.name,
            lastError = entity.lastError,
            attemptCount = attempts,
        )
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
        private const val MAX_RECONCILE_RESULTS = 200
        private const val MAX_SEND_ATTEMPTS = 5
        private const val AMOUNT_TOLERANCE = 0.005

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
