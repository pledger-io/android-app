package com.pledgerio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pledgerio.app.data.local.entity.TransactionOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionOutboxDao {

    @Query("SELECT * FROM transaction_outbox ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<TransactionOutboxEntity>>

    @Query("SELECT * FROM transaction_outbox WHERE status = :status ORDER BY createdAtMillis ASC")
    fun observeByStatus(status: String): Flow<List<TransactionOutboxEntity>>

    @Query("SELECT * FROM transaction_outbox WHERE status = :status ORDER BY createdAtMillis ASC")
    suspend fun getByStatus(status: String): List<TransactionOutboxEntity>

    @Query("SELECT * FROM transaction_outbox WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): TransactionOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionOutboxEntity)

    @Query(
        """
        UPDATE transaction_outbox
        SET status = :status, lastError = :lastError, attemptCount = :attemptCount
        WHERE localId = :localId
        """,
    )
    suspend fun updateStatus(
        localId: String,
        status: String,
        lastError: String?,
        attemptCount: Int,
    )

    @Query("DELETE FROM transaction_outbox WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM transaction_outbox")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transaction_outbox")
    suspend fun count(): Int
}
