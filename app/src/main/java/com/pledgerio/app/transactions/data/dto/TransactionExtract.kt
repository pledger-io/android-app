package com.pledgerio.app.transactions.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransactionExtract(
    @Json(name = "text") val text: String
)
