package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks when a logical resource (e.g. "categories", "contracts") was last successfully
 * synced from the backend. Used to drive stale-while-revalidate cache refreshes.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val lastSyncedAt: Long,
)
