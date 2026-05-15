package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CurrencyDto(
    @Json(name = "code") val code: String,
    @Json(name = "name") val name: String,
    @Json(name = "symbol") val symbol: String,
    @Json(name = "decimalPlaces") val decimalPlaces: Int,
    @Json(name = "enabled") val enabled: Boolean,
)
