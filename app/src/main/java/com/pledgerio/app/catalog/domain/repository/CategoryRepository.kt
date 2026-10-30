package com.pledgerio.app.catalog.domain.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.catalog.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    /** Cache-backed list of categories; emits updates as the cache changes. */
    fun observeCategories(): Flow<List<Category>>

    /** Cache-backed filtered list. */
    fun observeMatching(query: String): Flow<List<Category>>

    fun getCategories(): Flow<Resource<List<Category>>>
    suspend fun getCategory(id: Long): Resource<Category>
    suspend fun searchCategories(name: String): Resource<List<Category>>
    suspend fun createCategory(name: String, description: String): Resource<Category>
    suspend fun updateCategory(category: Category): Resource<Category>
    suspend fun deleteCategory(id: Long): Resource<Unit>

    /** Force a network refresh of the category catalog. */
    suspend fun refreshCategories(): Resource<List<Category>>
}
