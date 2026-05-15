package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PageInfo(
    @Json(name = "records") val records: Long = 0,
    @Json(name = "pages") val pages: Int = 0,
    @Json(name = "pageSize") val pageSize: Int = 20,
)
