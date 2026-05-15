package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ContractDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String = "",
    @Json(name = "description") val description: String? = null,
)
