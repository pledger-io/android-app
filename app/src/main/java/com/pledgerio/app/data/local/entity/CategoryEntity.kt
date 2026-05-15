package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pledgerio.app.domain.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String = "",
    val parentId: Long? = null,
    val lastSynced: Long = System.currentTimeMillis(),
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        description = description,
        parentId = parentId,
    )

    companion object {
        fun fromDomain(category: Category): CategoryEntity = CategoryEntity(
            id = category.id,
            name = category.name,
            description = category.description,
            parentId = category.parentId,
        )
    }
}
