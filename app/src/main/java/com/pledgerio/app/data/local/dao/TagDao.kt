package com.pledgerio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pledgerio.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT name FROM tags ORDER BY name ASC")
    fun getAll(): Flow<List<String>>

    @Query("SELECT name FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    fun observeMatching(query: String, limit: Int = 50): Flow<List<String>>

    @Query("SELECT name FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchOnce(query: String, limit: Int = 50): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity)

    @Query("DELETE FROM tags WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(names: List<String>) {
        deleteAll()
        if (names.isNotEmpty()) {
            insertAll(names.map { TagEntity(it) })
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)
}
