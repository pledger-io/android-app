package com.pledgerio.app.domain.model

data class Contract(
    val id: Long,
    val name: String,
    val description: String = "",
)
