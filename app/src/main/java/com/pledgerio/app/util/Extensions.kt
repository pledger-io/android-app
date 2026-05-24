package com.pledgerio.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency

fun Double.formatCurrency(currencyCode: String? = null): String {
    val code = currencyCode ?: UserPreferences.defaultDisplayCurrency
    val javaCurrency = runCatching { Currency.getInstance(code) }.getOrNull()
    val decimalPlaces = javaCurrency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
    val symbol = javaCurrency?.symbol ?: code
    val formatted = String.format("%,.${decimalPlaces}f", this)
    return "$symbol $formatted"
}

fun LocalDate.formatDisplay(): String {
    return this.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
}

fun LocalDate.formatShort(): String {
    return this.format(DateTimeFormatter.ofPattern("MMM dd"))
}

fun LocalDate.formatApi(): String {
    return this.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

fun String.toLocalDate(): LocalDate {
    return LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
}
