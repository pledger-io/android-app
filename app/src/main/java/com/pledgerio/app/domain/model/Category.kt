package com.pledgerio.app.domain.model

data class Category(
    val id: Long,
    val name: String,
    val description: String = "",
    val parentId: Long? = null,
)
