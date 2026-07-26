package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    private val tokenRefresher: TokenRefresher,
    private val authenticatedSessionCoordinator: AuthenticatedSessionCoordinator,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (!isAuthEndpoint(originalRequest.url.encodedPath)) {
            tokenRefresher.refreshTokenIfNeeded()
        }

        val token = sessionManager.getToken()
        val authenticatedRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(authenticatedRequest)

        if (
            response.code == 401 &&
            token != null &&
            !isAuthEndpoint(originalRequest.url.encodedPath)
        ) {
            if (tokenRefresher.refreshToken(force = true)) {
                val newToken = sessionManager.getToken()
                if (newToken != null) {
                    response.close()
                    val retryRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    val retryResponse = chain.proceed(retryRequest)
                    if (retryResponse.code != 401) {
                        return retryResponse
                    }
                    authenticatedSessionCoordinator.terminateSessionAsync(newToken)
                    return retryResponse
                }
            }
            authenticatedSessionCoordinator.terminateSessionAsync(token)
        }

        return response
    }

    private fun isAuthEndpoint(path: String): Boolean =
        path.endsWith("/v2/api/security/authenticate") ||
            path.endsWith("/v2/api/security/oauth") ||
            path.endsWith("/v2/api/security/logout") ||
            path.endsWith("/health")
}
