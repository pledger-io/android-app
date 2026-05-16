package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CachePolicy
import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.TagDao
import com.pledgerio.app.data.local.dao.TransactionDao
import com.pledgerio.app.data.local.entity.TagEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CreateTagRequest
import com.pledgerio.app.domain.model.Tag
import com.pledgerio.app.domain.repository.TagRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val tagDao: TagDao,
    private val transactionDao: TransactionDao,
    private val cacheRefresher: CacheRefresher,
) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.getAll()
            .map { names -> names.map { Tag(it) } }
            .distinctUntilChanged()
            .onStart { triggerStaleRefresh() }

    override fun observeMatching(query: String): Flow<List<Tag>> =
        tagDao.observeMatching(query.trim())
            .map { names -> names.map { Tag(it) } }
            .distinctUntilChanged()
            .onStart { triggerStaleRefresh() }

    override suspend fun createTag(name: String): Resource<Tag> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Resource.Error("Tag name is required")
        return try {
            val response = apiService.createTag(CreateTagRequest(trimmed))
            if (response.isSuccessful) {
                tagDao.insert(TagEntity(trimmed))
                cacheRefresher.refreshInBackground(SyncKeys.TAGS) { refreshTags() }
                Resource.Success(Tag(trimmed))
            } else {
                Resource.Error("Failed to create tag: HTTP ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun renameTag(oldName: String, newName: String): Resource<Tag> {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return Resource.Error("Tag name is required")
        if (oldName.equals(trimmed, ignoreCase = true)) {
            return Resource.Success(Tag(oldName))
        }
        return when (val created = createTag(trimmed)) {
            is Resource.Success -> {
                when (val deleted = deleteTag(oldName, refreshCatalog = false)) {
                    is Resource.Success -> {
                        renameTagInCachedTransactions(oldName, trimmed)
                        Resource.Success(created.data)
                    }
                    is Resource.Error -> deleted
                    is Resource.Loading -> Resource.Error("Unexpected state")
                }
            }
            else -> created
        }
    }

    override suspend fun deleteTag(name: String): Resource<Unit> {
        return deleteTag(name, refreshCatalog = true)
    }

    private suspend fun deleteTag(name: String, refreshCatalog: Boolean): Resource<Unit> {
        return try {
            val response = apiService.deleteTag(name)
            if (response.isSuccessful) {
                tagDao.deleteByName(name)
                removeTagFromCachedTransactions(name)
                if (refreshCatalog) {
                    cacheRefresher.refreshInBackground(SyncKeys.TAGS) { refreshTags() }
                }
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete tag: HTTP ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun refreshTags(): Resource<List<Tag>> {
        return cacheRefresher.refreshNow(SyncKeys.TAGS) {
            try {
                val response = apiService.getTags()
                if (response.isSuccessful) {
                    val tags = response.body()
                        .orEmpty()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                        .map { Tag(it) }
                    tagDao.replaceAll(tags.map { it.name })
                    Resource.Success(tags)
                } else {
                    Resource.Error("Failed to fetch tags: HTTP ${response.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Network error")
            }
        }
    }

    private suspend fun renameTagInCachedTransactions(oldName: String, newName: String) {
        transactionDao.getAllOnce().forEach { entity ->
            if (entity.tags.any { it.equals(oldName, ignoreCase = true) }) {
                val updated = entity.tags.map { tag ->
                    if (tag.equals(oldName, ignoreCase = true)) newName else tag
                }.distinctBy { it.lowercase() }
                transactionDao.insert(entity.copy(tags = updated))
            }
        }
    }

    private suspend fun removeTagFromCachedTransactions(name: String) {
        transactionDao.getAllOnce().forEach { entity ->
            if (entity.tags.any { it.equals(name, ignoreCase = true) }) {
                val updated = entity.tags.filterNot { it.equals(name, ignoreCase = true) }
                transactionDao.insert(entity.copy(tags = updated))
            }
        }
    }

    private fun triggerStaleRefresh() {
        cacheRefresher.launchIfStale(
            key = SyncKeys.TAGS,
            ttlMs = CachePolicy.TAGS_TTL_MS,
        ) { refreshTags() }
    }
}
