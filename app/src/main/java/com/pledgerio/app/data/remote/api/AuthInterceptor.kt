package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.AuthenticatedSessionScope
import com.pledgerio.app.util.SessionManager
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    private val tokenRefresher: TokenRefresher,
    private val authenticatedSessionCoordinator: AuthenticatedSessionCoordinator,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath
        val initialScope = sessionManager.getAuthenticatedSessionScope()
        val requestScope = when {
            initialScope == null -> null
            isRefreshEndpoint(path) -> null
            shouldSkipProactiveRefresh(path) -> initialScope
            else -> tokenRefresher.tokenForRequest(initialScope)
                ?: throw IOException("Authenticated session changed before request")
        }

        if (
            requestScope != null &&
            (
                !sessionManager.isSessionScopeCurrent(requestScope) ||
                    !requestTargetsScope(originalRequest, requestScope)
                )
        ) {
            throw IOException("Authenticated session does not match request destination")
        }

        val authenticatedRequest = if (requestScope != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer ${requestScope.accessToken}")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(authenticatedRequest)

        if (
            response.code == 401 &&
            requestScope != null &&
            !shouldSkipReactiveRefresh(path)
        ) {
            val refreshedScope = tokenRefresher.refreshAfterUnauthorized(requestScope)
            if (
                refreshedScope != null &&
                sessionManager.isSessionScopeCurrent(refreshedScope) &&
                requestTargetsScope(originalRequest, refreshedScope)
            ) {
                response.close()
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer ${refreshedScope.accessToken}")
                    .build()
                val retryResponse = chain.proceed(retryRequest)
                if (retryResponse.code != 401) {
                    return retryResponse
                }
                authenticatedSessionCoordinator.terminateSessionAsync(refreshedScope)
                return retryResponse
            }
            authenticatedSessionCoordinator.terminateSessionAsync(requestScope)
        }

        return response
    }

    private fun requestTargetsScope(
        request: okhttp3.Request,
        scope: AuthenticatedSessionScope,
    ): Boolean {
        val baseUrl = scope.baseUrl.toHttpUrlOrNull() ?: return false
        return request.url.scheme == baseUrl.scheme &&
            request.url.host == baseUrl.host &&
            request.url.port == baseUrl.port
    }

    private fun isRefreshEndpoint(path: String): Boolean =
        path.endsWith("/v2/api/security/authenticate") ||
            path.endsWith("/v2/api/security/oauth") ||
            path.endsWith("/health")

    private fun shouldSkipProactiveRefresh(path: String): Boolean =
        isRefreshEndpoint(path) || path.endsWith("/v2/api/security/logout")

    private fun shouldSkipReactiveRefresh(path: String): Boolean =
        path.endsWith("/v2/api/security/authenticate") ||
            path.endsWith("/v2/api/security/oauth") ||
            path.endsWith("/v2/api/security/logout") ||
            path.endsWith("/health")
}
