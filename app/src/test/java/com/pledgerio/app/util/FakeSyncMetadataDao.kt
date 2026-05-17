package com.pledgerio.app.util

import com.pledgerio.app.data.local.dao.SyncMetadataDao
import com.pledgerio.app.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SyncMetadataDao] for tests. Lets us drive TTL-based stale-while-revalidate
 * behaviour deterministically.
 */
class FakeSyncMetadataDao : SyncMetadataDao {

    private val data = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun seed(key: String, lastSyncedAt: Long) {
        data.value = data.value + (key to lastSyncedAt)
    }

    override suspend fun getLastSyncedAt(key: String): Long? = data.value[key]

    override fun observeLastSyncedAt(key: String) = data.map { it[key] }

    override suspend fun upsert(metadata: SyncMetadataEntity) {
        data.value = data.value + (metadata.key to metadata.lastSyncedAt)
    }

    override suspend fun delete(key: String) {
        data.value = data.value - key
    }

    override suspend fun deleteAll() {
        data.value = emptyMap()
    }
}
