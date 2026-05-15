package com.pledgerio.app.domain.model

data class Currency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimalPlaces: Int,
    val enabled: Boolean,
)
