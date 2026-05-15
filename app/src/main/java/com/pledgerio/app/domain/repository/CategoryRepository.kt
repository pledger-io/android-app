package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Category
import com.pledgerio.app.util.Resource
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getCategories(): Flow<Resource<List<Category>>>
    suspend fun getCategory(id: Long): Resource<Category>
    suspend fun searchCategories(name: String): Resource<List<Category>>
}
