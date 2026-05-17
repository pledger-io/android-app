package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TransactionDto(
    @Json(name = "id") val id: Long,
    @Json(name = "description") val description: String = "",
    @Json(name = "currency") val currency: String = "EUR",
    @Json(name = "amount") val amount: Double = 0.0,
    @Json(name = "type") val type: String = "DEBIT",
    @Json(name = "dates") val dates: TransactionDatesDto? = null,
    @Json(name = "source") val source: AccountLinkDto? = null,
    @Json(name = "destination") val destination: AccountLinkDto? = null,
    @Json(name = "metadata") val metadata: TransactionMetadataDto? = null,
    @Json(name = "split") val split: List<TransactionSplitDto>? = null,
)

@JsonClass(generateAdapter = true)
data class AccountLinkDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String = "",
    @Json(name = "type") val type: String = "",
)

@JsonClass(generateAdapter = true)
data class TransactionDatesDto(
    @Json(name = "transaction") val transaction: String? = null,
    @Json(name = "booked") val booked: String? = null,
    @Json(name = "interest") val interest: String? = null,
)

@JsonClass(generateAdapter = true)
data class TransactionMetadataDto(
    @Json(name = "category") val category: String? = null,
    @Json(name = "budget") val budget: String? = null,
    @Json(name = "contract") val contract: String? = null,
    @Json(name = "import") val importSlug: String? = null,
    @Json(name = "tags") val tags: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class TransactionSplitDto(
    @Json(name = "description") val description: String = "",
    @Json(name = "amount") val amount: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class CreateTransactionRequest(
    @Json(name = "date") val date: String,
    @Json(name = "currency") val currency: String = "EUR",
    @Json(name = "description") val description: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "source") val source: Long,
    @Json(name = "target") val target: Long,
    @Json(name = "category") val category: Long? = null,
    @Json(name = "expense") val expense: Long? = null,
    @Json(name = "contract") val contract: Long? = null,
    @Json(name = "tags") val tags: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class DateRangeDto(
    @Json(name = "startDate") val startDate: String,
    @Json(name = "endDate") val endDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class TransactionPagedResponse(
    @Json(name = "info") val info: PageInfo = PageInfo(),
    @Json(name = "content") val content: List<TransactionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TransactionClassificationSuggestionDto(
    @Json(name = "budget") val budget: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "tags") val tags: List<String>? = null,
)
