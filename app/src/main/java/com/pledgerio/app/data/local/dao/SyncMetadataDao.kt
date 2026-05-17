package com.pledgerio.app.data.local.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.pledgerio.app.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT lastSyncedAt FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSyncedAt(key: String): Long?

    @Query("SELECT lastSyncedAt FROM sync_metadata WHERE `key` = :key")
    fun observeLastSyncedAt(key: String): Flow<Long?>

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAll()
}
