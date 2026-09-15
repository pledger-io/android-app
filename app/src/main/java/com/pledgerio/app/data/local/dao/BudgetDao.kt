package com.pledgerio.app.data.local.dao

import androidx.room.*
import com.pledgerio.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY name ASC")
    fun getAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    /** Swaps the cached month in one transaction so readers never observe an empty table. */
    @Transaction
    suspend fun replaceAll(budgets: List<BudgetEntity>) {
        deleteAll()
        if (budgets.isNotEmpty()) {
            insertAll(budgets)
        }
    }
}
