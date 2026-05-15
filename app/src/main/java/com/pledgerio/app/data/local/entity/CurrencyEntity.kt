package com.pledgerio.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pledgerio.app.domain.model.Currency

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String,
    val decimalPlaces: Int,
    val enabled: Boolean,
) {
    fun toDomain(): Currency = Currency(
        code = code,
        name = name,
        symbol = symbol,
        decimalPlaces = decimalPlaces,
        enabled = enabled,
    )

    companion object {
        fun fromDomain(currency: Currency): CurrencyEntity = CurrencyEntity(
            code = currency.code,
            name = currency.name,
            symbol = currency.symbol,
            decimalPlaces = currency.decimalPlaces,
            enabled = currency.enabled,
        )
    }
}
