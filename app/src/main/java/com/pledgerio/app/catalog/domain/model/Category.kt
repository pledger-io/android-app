package com.pledgerio.app.catalog.domain.model

data class Category(
    val id: Long,
    val name: String,
    val description: String = "",
    val parentId: Long? = null,
)
