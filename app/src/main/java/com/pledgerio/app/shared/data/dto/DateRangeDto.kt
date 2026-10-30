package com.pledgerio.app.shared.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DateRangeDto(
    @Json(name = "startDate") val startDate: String,
    @Json(name = "endDate") val endDate: String? = null,
)
