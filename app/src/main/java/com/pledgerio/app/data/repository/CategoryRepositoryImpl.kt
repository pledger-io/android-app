package com.pledgerio.app.data.repository

import com.pledgerio.app.data.cache.CachePolicy
import com.pledgerio.app.data.cache.CacheRefresher
import com.pledgerio.app.data.cache.SyncKeys
import com.pledgerio.app.data.local.dao.CategoryDao
import com.pledgerio.app.data.local.entity.CategoryEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.util.Resource
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
                val entity = CategoryEntity(id = dto.id, name = dto.name, description = dto.description ?: "")
                categoryDao.insertAll(listOf(entity))
                Resource.Success(entity.toDomain())
            } else {
                Resource.Error("Category not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Error fetching category")
        }
    }

    override suspend fun refreshCategories(): Resource<List<Category>> {
        return cacheRefresher.refreshNow(SyncKeys.CATEGORIES) {
            try {
                val response = apiService.getCategories()
                if (response.isSuccessful) {
                    val categories = response.body()?.content?.map { dto ->
                        Category(
                            id = dto.id,
                            name = dto.name,
                            description = dto.description ?: "",
                        )
                    } ?: emptyList()
                    categoryDao.replaceAll(categories.map { CategoryEntity.fromDomain(it) })
                    Resource.Success(categories)
                } else {
                    Resource.Error("Failed to fetch categories: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Network error")
            }
        }
    }

    private fun triggerStaleRefresh() {
        cacheRefresher.launchIfStale(
            key = SyncKeys.CATEGORIES,
            ttlMs = CachePolicy.CATEGORIES_TTL_MS,
        ) { refreshCategories() }
    }
}
