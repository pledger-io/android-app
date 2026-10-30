package com.pledgerio.app.catalog.domain.model

data class Contract(
    val id: Long,
    val name: String,
    val description: String = "",
)
