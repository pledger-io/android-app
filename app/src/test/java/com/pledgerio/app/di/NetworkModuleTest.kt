package com.pledgerio.app.di

import com.pledgerio.app.data.remote.api.AuthInterceptor
import com.pledgerio.app.data.remote.api.DynamicBaseUrlInterceptor
import com.pledgerio.app.data.remote.api.IssueLogInterceptor
import io.mockk.mockk
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun `network client omits raw URL logging interceptor`() {
        val client = NetworkModule.provideOkHttpClient(
            authInterceptor = mockk<AuthInterceptor>(),
            dynamicBaseUrlInterceptor = mockk<DynamicBaseUrlInterceptor>(),
            issueLogInterceptor = mockk<IssueLogInterceptor>(),
        )

        assertEquals(3, client.interceptors.size)
        assertFalse(client.interceptors.any { it is HttpLoggingInterceptor })
    }
}
