package com.pledgerio.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identifies one authenticated session without exposing its values through [toString].
 *
 * The access token is carried only in memory so an interceptor can use exactly the credential
 * that was atomically verified. This object must never be persisted or logged.
 */
class AuthenticatedSessionScope internal constructor(
    val accessToken: String,
    val baseUrl: String,
    val syncGeneration: String,
) {
    override fun toString(): String = "AuthenticatedSessionScope(redacted)"
}

class RefreshSessionSnapshot internal constructor(
    val scope: AuthenticatedSessionScope,
    val refreshToken: String,
) {
    override fun toString(): String = "RefreshSessionSnapshot(redacted)"
}

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "pledger_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SYNC_GENERATION = "sync_generation"

        /** Refresh the access token this long before it expires. */
        const val TOKEN_REFRESH_BUFFER_MS = 60_000L
    }

    @Synchronized
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    @Synchronized
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    @Synchronized
    fun saveTokenExpiry(expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000
        prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, expiresAt).apply()
    }

    @Synchronized
    fun getTokenExpiresAt(): Long? {
        val expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        return expiresAt.takeIf { it > 0L }
    }

    fun isTokenExpiringSoon(
        bufferMs: Long = TOKEN_REFRESH_BUFFER_MS,
    ): Boolean {
        val expiresAt = getTokenExpiresAt() ?: return false
        return System.currentTimeMillis() >= expiresAt - bufferMs
    }

    @Synchronized
    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    @Synchronized
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    @Synchronized
    fun saveBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    @Synchronized
    fun getBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    @Synchronized
    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    @Synchronized
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    /**
     * Installs all credential fields and the new work generation in one encrypted preference
     * transaction.
     */
    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun installAuthenticatedSession(
        accessToken: String,
        username: String,
        refreshToken: String?,
        expiresInSeconds: Long,
    ): String {
        val generation = UUID.randomUUID().toString()
        val editor = prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .remove(KEY_USERNAME)
            .remove(KEY_SYNC_GENERATION)
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_USERNAME, username)
            .putString(KEY_SYNC_GENERATION, generation)
        refreshToken?.let { editor.putString(KEY_REFRESH_TOKEN, it) }
        if (expiresInSeconds > 0) {
            editor.putLong(
                KEY_TOKEN_EXPIRES_AT,
                System.currentTimeMillis() + expiresInSeconds * 1000,
            )
        }
        check(editor.commit()) { "Could not persist authenticated session" }
        return generation
    }

    /**
     * Captures every value that scopes a refresh request under one monitor.
     */
    @Synchronized
    fun getRefreshSessionSnapshot(
        expectedScope: AuthenticatedSessionScope,
    ): RefreshSessionSnapshot? {
        if (!isSessionScopeCurrent(expectedScope)) return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return RefreshSessionSnapshot(expectedScope, refreshToken)
    }

    /**
     * Commits a refresh only when the complete session snapshot still matches.
     *
     * A server or account transition changes at least one compared value, so its in-flight
     * response is discarded without touching the new session.
     */
    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun commitRefreshedCredentials(
        snapshot: RefreshSessionSnapshot,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
    ): AuthenticatedSessionScope? {
        if (!isSessionScopeCurrent(snapshot.scope)) return null
        if (prefs.getString(KEY_REFRESH_TOKEN, null) != snapshot.refreshToken) return null

        val committedRefreshToken = refreshToken ?: snapshot.refreshToken
        val editor = prefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, committedRefreshToken)
        if (expiresInSeconds > 0) {
            editor.putLong(
                KEY_TOKEN_EXPIRES_AT,
                System.currentTimeMillis() + expiresInSeconds * 1000,
            )
        } else {
            editor.remove(KEY_TOKEN_EXPIRES_AT)
        }
        if (!editor.commit()) return null

        return AuthenticatedSessionScope(
            accessToken = accessToken,
            baseUrl = snapshot.scope.baseUrl,
            syncGeneration = snapshot.scope.syncGeneration,
        )
    }

    @Synchronized
    fun getAuthenticatedSessionScope(): AuthenticatedSessionScope? {
        val accessToken = prefs.getString(KEY_TOKEN, null) ?: return null
        val baseUrl = prefs.getString(KEY_BASE_URL, null)?.trimEnd('/') ?: return null
        val generation = prefs.getString(KEY_SYNC_GENERATION, null) ?: return null
        return AuthenticatedSessionScope(accessToken, baseUrl, generation)
    }

    @Synchronized
    fun isSessionScopeCurrent(scope: AuthenticatedSessionScope): Boolean =
        prefs.getString(KEY_TOKEN, null) == scope.accessToken &&
            prefs.getString(KEY_BASE_URL, null)?.trimEnd('/') == scope.baseUrl &&
            prefs.getString(KEY_SYNC_GENERATION, null) == scope.syncGeneration

    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun clearAuthTokensIfCurrent(scope: AuthenticatedSessionScope): Boolean {
        if (!isSessionScopeCurrent(scope)) return false
        clearAuthTokens()
        return true
    }

    /**
     * Starts a new opaque generation for authenticated background work.
     *
     * The value is encrypted at rest and contains no account or credential data.
     */
    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun rotateSyncGeneration(): String {
        val generation = UUID.randomUUID().toString()
        check(prefs.edit().putString(KEY_SYNC_GENERATION, generation).commit()) {
            "Could not persist background work generation"
        }
        return generation
    }

    @Synchronized
    fun getSyncGeneration(): String? = prefs.getString(KEY_SYNC_GENERATION, null)

    /**
     * Invalidates workers synchronously before cancellation is dispatched.
     *
     * commit() is intentional: callers rely on the generation being durably removed before
     * allowing a different authenticated session to be installed.
     */
    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun invalidateSyncGeneration() {
        check(prefs.edit().remove(KEY_SYNC_GENERATION).commit()) {
            "Could not invalidate background work generation"
        }
    }

    /**
     * Runs a non-suspending side effect only while [generation] is the active session.
     *
     * Used for notifications so invalidation and publication have a single ordering point.
     */
    @Synchronized
    fun runIfSyncGenerationCurrent(generation: String, action: () -> Unit): Boolean {
        val isCurrent = generation.isNotBlank() &&
            getToken() != null &&
            prefs.getString(KEY_SYNC_GENERATION, null) == generation
        if (isCurrent) action()
        return isCurrent
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun isLoggedIn(): Boolean = getToken() != null

    /** Clears auth credentials but keeps server URL and biometric preference. */
    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun clearAuthTokens() {
        check(
            prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRES_AT)
                .remove(KEY_USERNAME)
                .remove(KEY_SYNC_GENERATION)
                .commit(),
        ) {
            "Could not clear authentication state"
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun clearAuthTokensAndSaveBaseUrl(baseUrl: String) {
        check(
            prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRES_AT)
                .remove(KEY_USERNAME)
                .remove(KEY_SYNC_GENERATION)
                .putString(KEY_BASE_URL, baseUrl)
                .commit(),
        ) {
            "Could not switch server and clear authentication state"
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
