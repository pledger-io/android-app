package com.pledgerio.app.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the pre-verification JWT between password login and TOTP verify.
 * Process memory only — never persisted to [SessionManager].
 */
@Singleton
class PendingMfaSession @Inject constructor() {

    data class Pending(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long,
        val username: String,
    ) {
        fun authorizationHeader(): String = "Bearer $accessToken"
    }

    @Volatile
    private var pending: Pending? = null

    @Synchronized
    fun set(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
        username: String,
    ) {
        require(accessToken.isNotBlank()) { "Pending MFA access token required" }
        require(username.isNotBlank()) { "Pending MFA username required" }
        pending = Pending(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = expiresInSeconds,
            username = username,
        )
    }

    @Synchronized
    fun get(): Pending? = pending

    @Synchronized
    fun clear() {
        pending = null
    }

    fun isPending(): Boolean = get() != null
}
