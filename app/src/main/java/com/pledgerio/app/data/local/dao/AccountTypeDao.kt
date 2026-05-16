package com.pledgerio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pledgerio.app.data.local.entity.AccountTypeEntity

@Dao
interface AccountTypeDao {

    @Query("SELECT code FROM account_types ORDER BY code ASC")
    suspend fun getAllCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AccountTypeEntity>)

    @Query("DELETE FROM account_types")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(codes: List<String>) {
        deleteAll()
        if (codes.isNotEmpty()) {
            insertAll(codes.map { AccountTypeEntity(it) })
        }
    }
}
