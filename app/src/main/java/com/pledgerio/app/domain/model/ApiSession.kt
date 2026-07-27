package com.pledgerio.app.domain.model

import java.time.LocalDate

/**
 * Durable API token session (long-lived credential), not the interactive JWT for this device.
 */
data class ApiSession(
    val id: Long,
    val description: String,
    val token: String?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
) {
    /** List UI: never show the full secret even if the API returns it. */
    fun maskedToken(): String = maskApiToken(token)

    companion object {
        const val MIN_DESCRIPTION_LENGTH = 8
    }
}

/** Masks a token as bullets plus the last 4 characters (or bullets only if blank/short). */
fun maskApiToken(token: String?): String {
    if (token.isNullOrBlank()) return "••••"
    if (token.length <= 4) return "••••"
    return "••••${token.takeLast(4)}"
}
