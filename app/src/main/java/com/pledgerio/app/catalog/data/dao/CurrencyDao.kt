package com.pledgerio.app.catalog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pledgerio.app.catalog.data.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE enabled = 1 ORDER BY code ASC")
    fun getAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE enabled = 1 ORDER BY code ASC")
    suspend fun getAllOnce(): List<CurrencyEntity>

    @Query("SELECT * FROM currencies WHERE code = :code")
    suspend fun getByCode(code: String): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(currencies: List<CurrencyEntity>)

    @Query("DELETE FROM currencies")
    suspend fun deleteAll()
}
