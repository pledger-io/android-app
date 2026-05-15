package com.pledgerio.app.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransactionTemplate(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String,
    @Json(name = "amount") val amount: String,
    @Json(name = "type") val type: String,
    @Json(name = "currency") val currency: String,
    @Json(name = "sourceAccountId") val sourceAccountId: Long? = null,
    @Json(name = "sourceAccountName") val sourceAccountName: String = "",
    @Json(name = "targetAccountId") val targetAccountId: Long? = null,
    @Json(name = "targetAccountName") val targetAccountName: String = "",
    @Json(name = "tags") val tags: List<String> = emptyList(),
) {
    val typeEnum: TransactionType?
        get() = runCatching { TransactionType.valueOf(type) }.getOrNull()
}
