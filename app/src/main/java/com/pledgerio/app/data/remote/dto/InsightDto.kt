package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DetectedInsightDto(
    @Json(name = "type") val type: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "severity") val severity: String? = null,
    @Json(name = "score") val score: Double = 0.0,
    @Json(name = "detected-date") val detectedDate: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "transaction-id") val transactionId: Long? = null,
    @Json(name = "metadata") val metadata: Map<String, @JvmSuppressWildcards Any?>? = null,
)

@JsonClass(generateAdapter = true)
data class DetectedPatternDto(
    @Json(name = "type") val type: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "confidence") val confidence: Double = 0.0,
    @Json(name = "detected-date") val detectedDate: String? = null,
    @Json(name = "metadata") val metadata: Map<String, @JvmSuppressWildcards Any?>? = null,
)
