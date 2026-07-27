package com.pledgerio.app.util

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.Base64

/**
 * Lightweight JWT payload helpers. Signature is not verified — callers must only
 * use tokens freshly returned over HTTPS from the configured Pledger server.
 */
object JwtPayload {
    const val PRE_VERIFICATION_ROLE = "PRE_VERIFICATION_USER"

    private val claimsAdapter = Moshi.Builder()
        .build()
        .adapter(JwtClaims::class.java)

    fun requiresMfaVerification(accessToken: String): Boolean =
        roles(accessToken).any { role ->
            role.equals(PRE_VERIFICATION_ROLE, ignoreCase = true) ||
                role.equals("ROLE_$PRE_VERIFICATION_ROLE", ignoreCase = true)
        }

    fun roles(accessToken: String): List<String> {
        val payloadJson = decodePayloadJson(accessToken) ?: return emptyList()
        return try {
            val claims = claimsAdapter.fromJson(payloadJson) ?: return emptyList()
            claims.roles.orEmpty().ifEmpty { claims.authorities.orEmpty() }
        } catch (_: Exception) {
            // Fallback when roles is a single string rather than an array.
            extractQuotedRoles(payloadJson)
        }
    }

    private fun decodePayloadJson(accessToken: String): String? {
        val parts = accessToken.split('.')
        if (parts.size < 2) return null
        return try {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractQuotedRoles(payloadJson: String): List<String> {
        val match = Regex(
            """"(?:roles|authorities)"\s*:\s*"([^"]+)"""",
        ).find(payloadJson)
        return match?.groupValues?.getOrNull(1)?.let { listOf(it) }.orEmpty()
    }

    @JsonClass(generateAdapter = true)
    internal data class JwtClaims(
        @Json(name = "roles") val roles: List<String>? = null,
        @Json(name = "authorities") val authorities: List<String>? = null,
    )
}
