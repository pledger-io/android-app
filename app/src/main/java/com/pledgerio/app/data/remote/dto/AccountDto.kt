package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String = "default",
    @Json(name = "description") val description: String? = null,
    @Json(name = "iconFileCode") val iconFileCode: String? = null,
    @Json(name = "account") val account: AccountIdentificationDto? = null,
    @Json(name = "interest") val interest: AccountInterestDto? = null,
    @Json(name = "history") val history: AccountHistoryDto? = null,
)

@JsonClass(generateAdapter = true)
data class AccountIdentificationDto(
    @Json(name = "iban") val iban: String? = null,
    @Json(name = "bic") val bic: String? = null,
    @Json(name = "number") val number: String? = null,
    @Json(name = "currency") val currency: String? = null,
)

@JsonClass(generateAdapter = true)
data class AccountInterestDto(
    @Json(name = "periodicity") val periodicity: String? = null,
    @Json(name = "interest") val interest: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class AccountHistoryDto(
    @Json(name = "firstTransaction") val firstTransaction: String? = null,
    @Json(name = "lastTransaction") val lastTransaction: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateAccountRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "currency") val currency: String = "EUR",
    @Json(name = "type") val type: String = "default",
    @Json(name = "iban") val iban: String? = null,
    @Json(name = "bic") val bic: String? = null,
    @Json(name = "number") val number: String? = null,
    @Json(name = "interest") val interest: Double? = null,
    @Json(name = "interestPeriodicity") val interestPeriodicity: String? = null,
    @Json(name = "imageIcon") val imageIcon: String? = null,
)

@JsonClass(generateAdapter = true)
data class AccountPagedResponse(
    @Json(name = "info") val info: PageInfo = PageInfo(),
    @Json(name = "content") val content: List<AccountDto> = emptyList(),
)
