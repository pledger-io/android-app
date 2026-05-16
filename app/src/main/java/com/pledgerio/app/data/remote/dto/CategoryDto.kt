package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CategoryDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "lastUsed") val lastUsed: String? = null,
)

@JsonClass(generateAdapter = true)
data class CategoryPagedResponse(
    @Json(name = "info") val info: PageInfo = PageInfo(),
    @Json(name = "content") val content: List<CategoryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CategoryUpsertRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
)
