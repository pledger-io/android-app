package com.pledgerio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pledgerio.app.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE type IN (:types) ORDER BY name ASC")
    fun observeByTypes(types: List<String>): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE type IN (:types) ORDER BY name ASC")
    suspend fun getByTypesOnce(types: List<String>): List<AccountEntity>

    @Query(
        "SELECT * FROM accounts WHERE type IN (:types) AND name LIKE '%' || :query || '%' " +
            "ORDER BY name ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchByTypes(
        types: List<String>,
        query: String,
        offset: Int,
        limit: Int,
    ): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts WHERE type IN (:types) AND name LIKE '%' || :query || '%'")
    suspend fun countByTypes(types: List<String>, query: String): Long

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM accounts WHERE type IN (:types)")
    suspend fun deleteByTypes(types: List<String>)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceByTypes(types: List<String>, items: List<AccountEntity>) {
        deleteByTypes(types)
        if (items.isNotEmpty()) insertAll(items)
    }
}
