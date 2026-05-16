package com.pledgerio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pledgerio.app.data.local.entity.ExpenseGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseGroupDao {

    @Query("SELECT * FROM expense_groups ORDER BY name ASC")
    fun observeAll(): Flow<List<ExpenseGroupEntity>>

    @Query("SELECT * FROM expense_groups WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    fun observeMatching(query: String, limit: Int = 20): Flow<List<ExpenseGroupEntity>>

    @Query("SELECT * FROM expense_groups WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchOnce(query: String, limit: Int = 20): List<ExpenseGroupEntity>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    suspend fun getById(id: Long): ExpenseGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExpenseGroupEntity>)

    @Query("DELETE FROM expense_groups")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<ExpenseGroupEntity>) {
        deleteAll()
        if (items.isNotEmpty()) insertAll(items)
    }
}
