package com.pledgerio.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateBudgetRequest(
    @Json(name = "year") val year: Int,
    @Json(name = "month") val month: Int,
    @Json(name = "income") val income: Double,
)

@JsonClass(generateAdapter = true)
data class BudgetDto(
    @Json(name = "income") val income: Double = 0.0,
    @Json(name = "period") val period: DateRangeDto? = null,
    @Json(name = "expenses") val expenses: List<ExpenseDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ExpenseDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String = "",
    @Json(name = "expected") val expected: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class ExpenseComputedDto(
    @Json(name = "id") val id: Long,
    @Json(name = "left") val left: Double = 0.0,
    @Json(name = "dailyLeft") val dailyLeft: Double = 0.0,
    @Json(name = "spent") val spent: Double = 0.0,
    @Json(name = "dailySpent") val dailySpent: Double = 0.0,
)
