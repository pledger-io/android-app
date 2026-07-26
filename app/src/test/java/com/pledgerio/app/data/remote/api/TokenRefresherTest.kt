package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.AuthenticatedSessionScope
import com.pledgerio.app.util.RefreshSessionSnapshot
import com.pledgerio.app.util.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class TokenRefresherTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenRefresher: TokenRefresher
    private lateinit var scope: AuthenticatedSessionScope
    private lateinit var snapshot: RefreshSessionSnapshot

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        sessionManager = mockk(relaxed = true)
        scope = AuthenticatedSessionScope(
            accessToken = "old-access",
            baseUrl = server.url("/").toString().trimEnd('/'),
            syncGeneration = "generation-a",
        )
        snapshot = RefreshSessionSnapshot(scope, "refresh-abc")
        every { sessionManager.getAuthenticatedSessionScope() } returns scope
        every { sessionManager.isSessionScopeCurrent(scope) } returns true
        every { sessionManager.getRefreshSessionSnapshot(scope) } returns snapshot
        every { sessionManager.isTokenExpiringSoon() } returns true

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        tokenRefresher = TokenRefresher(sessionManager, moshi, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refresh returns the exact atomically committed credential`() {
        val committedScope = AuthenticatedSessionScope(
            accessToken = "new-access",
            baseUrl = scope.baseUrl,
            syncGeneration = scope.syncGeneration,
        )
        every {
            sessionManager.commitRefreshedCredentials(
                snapshot,
                "new-access",
                "new-refresh",
                3600,
            )
        } returns committedScope
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

        val result = tokenRefresher.refreshAfterUnauthorized(scope)

        assertSame(committedScope, result)
        verify(exactly = 1) {
            sessionManager.commitRefreshedCredentials(
                snapshot,
                "new-access",
                "new-refresh",
                3600,
            )
        }
        verify(exactly = 0) { sessionManager.saveToken(any()) }
        verify(exactly = 0) { sessionManager.saveRefreshToken(any()) }
    }

    @Test
    fun `refresh returns null when complete refresh snapshot is unavailable`() {
        every { sessionManager.getRefreshSessionSnapshot(scope) } returns null

        assertNull(tokenRefresher.refreshAfterUnauthorized(scope))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refresh returns null on server error`() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertNull(tokenRefresher.refreshAfterUnauthorized(scope))
        verify(exactly = 0) {
            sessionManager.commitRefreshedCredentials(any(), any(), any(), any())
        }
    }

    @Test
    fun `proactive selection returns current credential without network when not expiring`() {
        every { sessionManager.isTokenExpiringSoon() } returns false

        val result = tokenRefresher.tokenForRequest(scope)

        assertSame(scope, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `stale refresh response is discarded after session changes in flight`() {
        val requestArrived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestArrived.countDown()
                check(releaseResponse.await(5, TimeUnit.SECONDS))
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "access_token": "stale-access",
                          "refresh_token": "stale-refresh",
                          "expires_in": 3600
                        }
                        """.trimIndent(),
                    )
            }
        }
        every {
            sessionManager.commitRefreshedCredentials(
                snapshot,
                "stale-access",
                "stale-refresh",
                3600,
            )
        } returns null
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<AuthenticatedSessionScope?> {
                tokenRefresher.refreshAfterUnauthorized(scope)
            }
            check(requestArrived.await(5, TimeUnit.SECONDS))

            // Models a server/account transition before the old server responds.
            releaseResponse.countDown()

            assertNull(result.get(5, TimeUnit.SECONDS))
            verify(exactly = 1) {
                sessionManager.commitRefreshedCredentials(
                    snapshot,
                    "stale-access",
                    "stale-refresh",
                    3600,
                )
            }
            verify(exactly = 0) { sessionManager.saveToken(any()) }
        } finally {
            releaseResponse.countDown()
            executor.shutdownNow()
        }
    }
}
