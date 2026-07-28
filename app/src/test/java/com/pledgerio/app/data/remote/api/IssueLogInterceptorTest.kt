package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.AppLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test

class IssueLogInterceptorTest {

    @Test
    fun `request log contains fixed route template and no query values`() {
        val appLog = mockk<AppLog>(relaxed = true)
        val request = Request.Builder()
            .url(
                "https://example.com/v2/api/transactions/42" +
                    "?description=Salary%20July&amount=1234.56",
            )
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response

        IssueLogInterceptor(appLog).intercept(chain)

        verify(exactly = 1) {
            appLog.i(
                "Http",
                match { message ->
                    message.startsWith("GET /v2/api/transactions/{id} → 200 (") &&
                        message.endsWith("ms)") &&
                        !message.contains("Salary") &&
                        !message.contains("1234.56") &&
                        !message.contains("example.com")
                },
            )
        }
    }
}
