package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.common.Resource
import com.pledgerio.app.domain.model.FlushResult
import com.pledgerio.app.domain.model.PendingTransactionCreate
import com.pledgerio.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionOutboxRepository {
    fun observePending(): Flow<List<PendingTransactionCreate>>
    suspend fun enqueueCreate(transaction: Transaction): Resource<PendingTransactionCreate>
    suspend fun discard(localId: String): Resource<Unit>
    suspend fun flushPending(generation: String): FlushResult
}
