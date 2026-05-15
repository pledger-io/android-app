package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BalanceRequest(
    @Json(name = "range") val range: DateRangeDto,
    @Json(name = "accounts") val accounts: List<Long>? = null,
    @Json(name = "categories") val categories: List<Long>? = null,
    @Json(name = "expenses") val expenses: List<Long>? = null,
    @Json(name = "contracts") val contracts: List<Long>? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "importSlug") val importSlug: String? = null,
)

@JsonClass(generateAdapter = true)
data class BalanceDto(
    @Json(name = "balance") val balance: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class BalancePartitionedDto(
    @Json(name = "balance") val balance: Double = 0.0,
    @Json(name = "partition") val partition: String = "",
)

@JsonClass(generateAdapter = true)
data class BalanceDatedDto(
    @Json(name = "balance") val balance: Double = 0.0,
    @Json(name = "date") val date: String = "",
)
