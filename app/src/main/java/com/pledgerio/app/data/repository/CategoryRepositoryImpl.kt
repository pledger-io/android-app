package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CachePolicy
import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.CategoryDao
import com.pledgerio.app.data.local.entity.CategoryEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.data.remote.dto.CategoryUpsertRequest
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val categoryDao: CategoryDao,
    private val cacheRefresher: CacheRefresher,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.getAll()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .onStart { triggerStaleRefresh() }

    override fun observeMatching(query: String): Flow<List<Category>> =
        categoryDao.observeMatching(query.trim())
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .onStart { triggerStaleRefresh() }

    override fun getCategories(): Flow<Resource<List<Category>>> = flow {
        emit(Resource.Loading)
        val cached = categoryDao.getAll().first()
        if (cached.isNotEmpty()) {
            emit(Resource.Success(cached.map { it.toDomain() }))
        }
        if (cacheRefresher.isStale(SyncKeys.CATEGORIES, CachePolicy.CATEGORIES_TTL_MS)) {
            val refreshed = refreshCategories()
            if (refreshed is Resource.Error && cached.isEmpty()) {
                emit(refreshed)
            } else if (refreshed is Resource.Success) {
                emit(refreshed)
            }
        }
    }

    override suspend fun searchCategories(name: String): Resource<List<Category>> {
        triggerStaleRefresh()
        val results = categoryDao.searchOnce(name.trim(), limit = 20)
            .map { it.toDomain() }
        return Resource.Success(results)
    }

    override suspend fun getCategory(id: Long): Resource<Category> {
        val cached = categoryDao.getById(id)
        if (cached != null) {
            triggerStaleRefresh()
            return Resource.Success(cached.toDomain())
        }
        return try {
            val response = apiService.getCategory(id)
            if (response.isSuccessful) {
                val dto = response.body() ?: return Resource.Error("Category not found")
                val entity = dto.toEntity()
                categoryDao.insert(entity)
                Resource.Success(entity.toDomain())
            } else {
                Resource.Error("Category not found")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Error fetching category")
        }
    }

    override suspend fun createCategory(name: String, description: String): Resource<Category> {
        return try {
            val response = apiService.createCategory(
                CategoryUpsertRequest(
                    name = name.trim(),
                    description = description.trim().ifBlank { null },
                ),
            )
            if (response.isSuccessful) {
                val created = response.body()?.toEntity() ?: return Resource.Error("Invalid category response")
                categoryDao.insert(created)
                cacheRefresher.refreshInBackground(SyncKeys.CATEGORIES) { refreshCategories() }
                Resource.Success(created.toDomain())
            } else {
                Resource.Error("Failed to create category: HTTP ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun updateCategory(category: Category): Resource<Category> {
        return try {
            val response = apiService.updateCategory(
                id = category.id,
                request = CategoryUpsertRequest(
                    name = category.name.trim(),
                    description = category.description.trim().ifBlank { null },
                ),
            )
            if (response.isSuccessful) {
                val updated = (response.body()?.toEntity() ?: CategoryEntity.fromDomain(category))
                categoryDao.insert(updated)
                cacheRefresher.refreshInBackground(SyncKeys.CATEGORIES) { refreshCategories() }
                Resource.Success(updated.toDomain())
            } else {
                Resource.Error("Failed to update category: HTTP ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun deleteCategory(id: Long): Resource<Unit> {
        return try {
            val response = apiService.deleteCategory(id)
            if (response.isSuccessful) {
                categoryDao.deleteById(id)
                cacheRefresher.refreshInBackground(SyncKeys.CATEGORIES) { refreshCategories() }
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete category: HTTP ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun refreshCategories(): Resource<List<Category>> {
        return cacheRefresher.refreshNow(SyncKeys.CATEGORIES) {
            try {
                val categories = fetchAllCategoriesFromApi()
                if (categories != null) {
                    categoryDao.replaceAll(categories.map { CategoryEntity.fromDomain(it) })
                    Resource.Success(categories)
                } else {
                    Resource.Error("Failed to fetch categories")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * The categories list endpoint requires [offset] and [numberOfResults] query parameters
     * (see rest-application contract). Omitting them yields HTTP 404 from the server.
     */
    private suspend fun fetchAllCategoriesFromApi(name: String? = null): List<Category>? {
        val collected = mutableListOf<Category>()
        var offset = 0
        val pageSize = 200
        while (true) {
            val response = apiService.getCategories(
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                offset = offset,
                numberOfResults = pageSize,
            )
            if (!response.isSuccessful) {
                return if (collected.isNotEmpty()) collected else null
            }
            val body = response.body()
            val page = body?.content?.map { dto ->
                Category(
                    id = dto.id,
                    name = dto.name,
                    description = dto.description ?: "",
                )
            } ?: emptyList()
            collected.addAll(page)
            val total = body?.info?.records ?: collected.size.toLong()
            if (page.isEmpty() || collected.size.toLong() >= total) break
            offset += pageSize
        }
        return collected
    }

    private fun triggerStaleRefresh() {
        cacheRefresher.launchIfStale(
            key = SyncKeys.CATEGORIES,
            ttlMs = CachePolicy.CATEGORIES_TTL_MS,
        ) { refreshCategories() }
    }

    private fun com.pledgerio.app.data.remote.dto.CategoryDto.toEntity(): CategoryEntity = CategoryEntity(
        id = id,
        name = name,
        description = description.orEmpty(),
    )
}
