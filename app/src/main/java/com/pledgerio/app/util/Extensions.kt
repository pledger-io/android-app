package com.pledgerio.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Double.formatCurrency(currencyCode: String = "EUR"): String {
    val provider = CurrencyProvider.getInstance()
    if (provider != null) {
        return provider.formatAmount(this, currencyCode)
    }
    val formatted = String.format("%,.2f", this)
    return "$currencyCode $formatted"
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
