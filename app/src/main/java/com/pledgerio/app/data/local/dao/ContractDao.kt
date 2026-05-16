package com.pledgerio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pledgerio.app.data.local.entity.ContractEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContractDao {

    @Query("SELECT * FROM contracts ORDER BY name ASC")
    fun observeAll(): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contracts WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    fun observeMatching(query: String, limit: Int = 20): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contracts WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchOnce(query: String, limit: Int = 20): List<ContractEntity>

    @Query("SELECT * FROM contracts WHERE id = :id")
    suspend fun getById(id: Long): ContractEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ContractEntity>)

    @Query("DELETE FROM contracts")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<ContractEntity>) {
        deleteAll()
        if (items.isNotEmpty()) insertAll(items)
    }
}
