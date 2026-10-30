package com.pledgerio.app.catalog.domain.model

data class Currency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimalPlaces: Int,
    val enabled: Boolean,
)
