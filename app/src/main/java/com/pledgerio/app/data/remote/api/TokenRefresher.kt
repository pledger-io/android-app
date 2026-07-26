package com.pledgerio.app.data.remote.api

import com.pledgerio.app.data.remote.dto.LoginResponse
import com.pledgerio.app.data.remote.dto.RefreshTokenRequest
import com.pledgerio.app.di.RefreshClient
import com.pledgerio.app.util.AuthenticatedSessionScope
import com.pledgerio.app.util.RefreshSessionSnapshot
import com.pledgerio.app.util.SessionManager
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class TokenRefresher @Inject constructor(
    private val sessionManager: SessionManager,
    private val moshi: Moshi,
    @RefreshClient private val refreshOkHttpClient: OkHttpClient,
) {
    private val refreshLock = Any()

    private val loginResponseAdapter = moshi.adapter(LoginResponse::class.java)
    private val refreshRequestAdapter = moshi.adapter(RefreshTokenRequest::class.java)

    /**
     * Returns a token scoped to [expectedScope], refreshing it when needed.
     *
     * A failed proactive refresh may use the still-current existing token and let the server
     * decide. A stale session always returns null.
     */
    fun tokenForRequest(
        expectedScope: AuthenticatedSessionScope,
    ): AuthenticatedSessionScope? = synchronized(refreshLock) {
        if (!sessionManager.isSessionScopeCurrent(expectedScope)) return null
        if (!sessionManager.isTokenExpiringSoon()) return expectedScope

        val snapshot = sessionManager.getRefreshSessionSnapshot(expectedScope)
            ?: return expectedScope.takeIf(sessionManager::isSessionScopeCurrent)
        performRefresh(snapshot)
            ?: expectedScope.takeIf(sessionManager::isSessionScopeCurrent)
    }

    /**
     * Refreshes a server-rejected token and returns the exact atomically committed credential.
     */
    fun refreshAfterUnauthorized(
        expectedScope: AuthenticatedSessionScope,
    ): AuthenticatedSessionScope? = synchronized(refreshLock) {
        if (!sessionManager.isSessionScopeCurrent(expectedScope)) {
            val currentScope = sessionManager.getAuthenticatedSessionScope()
            return currentScope?.takeIf {
                it.baseUrl == expectedScope.baseUrl &&
                    it.syncGeneration == expectedScope.syncGeneration
            }
        }
        val snapshot = sessionManager.getRefreshSessionSnapshot(expectedScope) ?: return null
        performRefresh(snapshot)
    }

    private fun performRefresh(
        snapshot: RefreshSessionSnapshot,
    ): AuthenticatedSessionScope? {
        val requestBody = refreshRequestAdapter.toJson(
            RefreshTokenRequest(refreshToken = snapshot.refreshToken),
        )
        val request = Request.Builder()
            .url("${snapshot.scope.baseUrl}/v2/api/security/oauth")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            refreshOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val loginResponse = loginResponseAdapter.fromJson(body) ?: return null
                val accessToken = loginResponse.accessToken
                if (accessToken.isBlank()) return null
                sessionManager.commitRefreshedCredentials(
                    snapshot = snapshot,
                    accessToken = accessToken,
                    refreshToken = loginResponse.refreshToken,
                    expiresInSeconds = loginResponse.expiresIn,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
