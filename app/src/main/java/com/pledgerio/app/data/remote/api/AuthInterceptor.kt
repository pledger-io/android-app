package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = sessionManager.getToken()
        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        // Only clear session on 401 if we actually sent a token —
        // unauthenticated requests (like server validation) shouldn't wipe the session
        if (response.code == 401 && token != null) {
            sessionManager.clearSession()
        }

        return response
    }
}
