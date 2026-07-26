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

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveTokenExpiry(expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000
        prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, expiresAt).apply()
    }

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

    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    fun getBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

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

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
