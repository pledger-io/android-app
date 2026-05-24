package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.Tag
import com.pledgerio.app.domain.common.Resource
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>

    fun observeMatching(query: String): Flow<List<Tag>>

    suspend fun createTag(name: String): Resource<Tag>

    /** Renames by creating the new tag then deleting the old one (no dedicated API). */
    suspend fun renameTag(oldName: String, newName: String): Resource<Tag>

    suspend fun deleteTag(name: String): Resource<Unit>

    suspend fun refreshTags(): Resource<List<Tag>>
}
