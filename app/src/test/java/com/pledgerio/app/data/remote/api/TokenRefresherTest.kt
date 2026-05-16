package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenRefresherTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenRefresher: TokenRefresher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        sessionManager = mockk(relaxed = true)
        every { sessionManager.getBaseUrl() } returns server.url("/").toString().trimEnd('/')
        every { sessionManager.getRefreshToken() } returns "refresh-abc"
        every { sessionManager.isTokenExpiringSoon() } returns true

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        tokenRefresher = TokenRefresher(sessionManager, moshi, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refreshToken persists new access token on success`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "access_token": "new-access",
                      "refresh_token": "new-refresh",
                      "expires_in": 3600
                    }
                    """.trimIndent(),
                ),
        )

        assertTrue(tokenRefresher.refreshToken())

        verify { sessionManager.saveToken("new-access") }
        verify { sessionManager.saveRefreshToken("new-refresh") }
        verify { sessionManager.saveTokenExpiry(3600) }
    }

    @Test
    fun `refreshToken returns false when refresh token missing`() {
        every { sessionManager.getRefreshToken() } returns null

        assertFalse(tokenRefresher.refreshToken())
    }

    @Test
    fun `refreshToken returns false on server error`() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertFalse(tokenRefresher.refreshToken())
    }
}
