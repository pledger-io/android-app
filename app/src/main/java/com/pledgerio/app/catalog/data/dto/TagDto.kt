package com.pledgerio.app.catalog.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateTagRequest(
    @Json(name = "name") val name: String,
)
