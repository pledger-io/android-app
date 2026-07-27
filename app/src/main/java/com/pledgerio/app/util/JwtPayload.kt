package com.pledgerio.app.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Lightweight JWT payload helpers. Signature is not verified — callers must only
 * use tokens freshly returned over HTTPS from the configured Pledger server.
 */
object JwtPayload {
    const val PRE_VERIFICATION_ROLE = "PRE_VERIFICATION_USER"

    fun requiresMfaVerification(accessToken: String): Boolean =
        roles(accessToken).any { role ->
            role.equals(PRE_VERIFICATION_ROLE, ignoreCase = true) ||
                role.equals("ROLE_$PRE_VERIFICATION_ROLE", ignoreCase = true)
        }

    fun roles(accessToken: String): List<String> {
        val payloadJson = decodePayloadJson(accessToken) ?: return emptyList()
        return try {
            val payload = JSONObject(payloadJson)
            when {
                payload.has("roles") -> readRoles(payload.get("roles"))
                payload.has("authorities") -> readRoles(payload.get("authorities"))
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
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

    private fun readRoles(value: Any): List<String> = when (value) {
        is JSONArray -> buildList {
            for (i in 0 until value.length()) {
                when (val item = value.get(i)) {
                    is String -> add(item)
                    is JSONObject -> {
                        val name = item.optString("authority")
                            .ifBlank { item.optString("role") }
                        if (name.isNotBlank()) add(name)
                    }
                }
            }
        }
        is String -> listOf(value)
        else -> emptyList()
    }
}
