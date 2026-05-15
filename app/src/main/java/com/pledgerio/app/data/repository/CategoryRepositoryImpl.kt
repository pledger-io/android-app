package com.pledgerio.app.data.repository

import com.pledgerio.app.data.local.dao.CategoryDao
import com.pledgerio.app.data.local.entity.CategoryEntity
import com.pledgerio.app.data.remote.api.PledgerApiService
import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.domain.repository.CategoryRepository
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val apiService: PledgerApiService,
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun getCategories(): Flow<Resource<List<Category>>> = flow {
        emit(Resource.Loading)

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

                categoryDao.deleteAll()
                categoryDao.insertAll(categories.map { CategoryEntity.fromDomain(it) })
                emit(Resource.Success(categories))
            } else {
                emit(Resource.Error("Failed to fetch categories"))
            }
        } catch (e: Exception) {
            categoryDao.getAll().collect { cached ->
                if (cached.isNotEmpty()) {
                    emit(Resource.Success(cached.map { it.toDomain() }))
                } else {
                    emit(Resource.Error(e.message ?: "Network error"))
                }
            }
        }
    }

    override suspend fun searchCategories(name: String): Resource<List<Category>> {
        return try {
            val response = apiService.getCategories(
                name = name.takeIf { it.isNotBlank() },
                offset = 0,
                numberOfResults = 20,
            )
            if (response.isSuccessful) {
                val categories = response.body()?.content?.map { dto ->
                    Category(
                        id = dto.id,
                        name = dto.name,
                        description = dto.description ?: "",
                    )
                } ?: emptyList()
                Resource.Success(categories)
            } else {
                Resource.Error("Failed to search categories")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getCategory(id: Long): Resource<Category> {
        return try {
            val response = apiService.getCategory(id)
            if (response.isSuccessful) {
                val dto = response.body() ?: return Resource.Error("Category not found")
                Resource.Success(Category(id = dto.id, name = dto.name, description = dto.description ?: ""))
            } else {
                val cached = categoryDao.getById(id)
                if (cached != null) Resource.Success(cached.toDomain())
                else Resource.Error("Category not found")
            }
        } catch (e: Exception) {
            val cached = categoryDao.getById(id)
            if (cached != null) Resource.Success(cached.toDomain())
            else Resource.Error(e.message ?: "Error fetching category")
        }
    }
}
