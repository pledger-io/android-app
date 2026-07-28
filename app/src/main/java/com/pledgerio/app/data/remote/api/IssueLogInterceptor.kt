package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.AppLog
import com.pledgerio.app.util.LogSanitizer
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records sanitized HTTP metadata for issue reports (no request/response bodies).
 */
@Singleton
class IssueLogInterceptor @Inject constructor(
    private val appLog: AppLog,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        val route = LogSanitizer.routeTemplate(request.url.encodedPath)
        val response = try {
            chain.proceed(request)
        } catch (error: Exception) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            appLog.e(
                TAG,
                "${request.method} $route → failed (${elapsedMs}ms)",
            )
            throw error
        }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        appLog.i(
            TAG,
            "${request.method} $route → ${response.code} (${elapsedMs}ms)",
        )
        return response
    }

    companion object {
        private const val TAG = "Http"
    }
}
