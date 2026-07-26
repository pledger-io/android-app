package com.pledgerio.app.data.remote.api

import com.pledgerio.app.data.remote.dto.LoginResponse
import com.pledgerio.app.data.remote.dto.RefreshTokenRequest
import com.pledgerio.app.util.SessionManager
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.pledgerio.app.di.RefreshClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefresher @Inject constructor(
    private val sessionManager: SessionManager,
    private val moshi: Moshi,
    @RefreshClient private val refreshOkHttpClient: OkHttpClient,
) {
    private val refreshLock = Any()

    private val loginResponseAdapter = moshi.adapter(LoginResponse::class.java)
    private val refreshRequestAdapter = moshi.adapter(RefreshTokenRequest::class.java)

    fun refreshTokenIfNeeded(): Boolean {
        if (!sessionManager.isTokenExpiringSoon()) return true
        return refreshToken()
    }

    fun refreshToken(force: Boolean = false): Boolean = synchronized(refreshLock) {
        if (!force && !sessionManager.isTokenExpiringSoon() && sessionManager.getToken() != null) {
            return true
        }

        val refreshToken = sessionManager.getRefreshToken() ?: return false
        val baseUrl = sessionManager.getBaseUrl()?.trimEnd('/') ?: return false

        val requestBody = refreshRequestAdapter.toJson(
            RefreshTokenRequest(refreshToken = refreshToken),
        )
        val request = Request.Builder()
            .url("$baseUrl/v2/api/security/oauth")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            refreshOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string() ?: return false
                val loginResponse = loginResponseAdapter.fromJson(body) ?: return false
                val accessToken = loginResponse.accessToken
                if (accessToken.isBlank()) return false
                sessionManager.saveToken(accessToken)
                loginResponse.refreshToken?.let { sessionManager.saveRefreshToken(it) }
                if (loginResponse.expiresIn > 0) {
                    sessionManager.saveTokenExpiry(loginResponse.expiresIn)
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
