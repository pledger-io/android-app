package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.SessionManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var tokenRefresher: TokenRefresher
    private lateinit var authenticatedSessionCoordinator: AuthenticatedSessionCoordinator
    private lateinit var interceptor: AuthInterceptor

    @Before
    fun setUp() {
        sessionManager = mockk(relaxed = true)
        tokenRefresher = mockk(relaxed = true)
        authenticatedSessionCoordinator = mockk(relaxed = true)
        interceptor = AuthInterceptor(
            sessionManager,
            tokenRefresher,
            authenticatedSessionCoordinator,
        )
    }

    @Test
    fun `intercept retries with refreshed token after unauthorized response`() {
        val original = Request.Builder()
            .url("https://example.com/v2/api/accounts")
            .build()
        val chain = mockk<Interceptor.Chain>()

        every { chain.request() } returns original
        every { sessionManager.getToken() } returnsMany listOf("old-token", "new-token")
        every { tokenRefresher.refreshToken(force = true) } returns true
        every { authenticatedSessionCoordinator.terminateSessionAsync(any()) } just Runs

        val capturedRequests = mutableListOf<Request>()
        every { chain.proceed(capture(capturedRequests)) } returnsMany listOf(
            responseFor(original, 401),
            responseFor(original, 200),
        )

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(2, capturedRequests.size)
        assertEquals("Bearer old-token", capturedRequests[0].header("Authorization"))
        assertEquals("Bearer new-token", capturedRequests[1].header("Authorization"))
        verify(exactly = 1) { tokenRefresher.refreshTokenIfNeeded() }
        verify(exactly = 1) { tokenRefresher.refreshToken(force = true) }
        verify(exactly = 0) { authenticatedSessionCoordinator.terminateSessionAsync(any()) }
        verify(exactly = 0) { sessionManager.clearAuthTokens() }
    }

    @Test
    fun `intercept clears auth state when token refresh fails after unauthorized`() {
        val original = Request.Builder()
            .url("https://example.com/v2/api/accounts")
            .build()
        val chain = mockk<Interceptor.Chain>()

        every { chain.request() } returns original
        every { sessionManager.getToken() } returns "old-token"
        every { tokenRefresher.refreshToken(force = true) } returns false
        every { chain.proceed(any()) } returns responseFor(original, 401)
        every { authenticatedSessionCoordinator.terminateSessionAsync(any()) } just Runs

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        verify(exactly = 1) { tokenRefresher.refreshTokenIfNeeded() }
        verify(exactly = 1) { tokenRefresher.refreshToken(force = true) }
        verify(exactly = 1) {
            authenticatedSessionCoordinator.terminateSessionAsync("old-token")
        }
        verify(exactly = 0) { sessionManager.clearAuthTokens() }
    }

    @Test
    fun `intercept terminates refreshed session when retry is also unauthorized`() {
        val original = Request.Builder()
            .url("https://example.com/v2/api/accounts")
            .build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns original
        every { sessionManager.getToken() } returnsMany listOf("old-token", "new-token")
        every { tokenRefresher.refreshToken(force = true) } returns true
        every { authenticatedSessionCoordinator.terminateSessionAsync(any()) } just Runs
        every { chain.proceed(any()) } returnsMany listOf(
            responseFor(original, 401),
            responseFor(original, 401),
        )

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        verify(exactly = 1) {
            authenticatedSessionCoordinator.terminateSessionAsync("new-token")
        }
    }

    @Test
    fun `intercept skips proactive refresh for auth endpoints`() {
        val authRequest = Request.Builder()
            .url("https://example.com/v2/api/security/authenticate")
            .build()
        val chain = mockk<Interceptor.Chain>()

        every { chain.request() } returns authRequest
        every { sessionManager.getToken() } returns null
        every { chain.proceed(any()) } returns responseFor(authRequest, 200)

        interceptor.intercept(chain)

        verify(exactly = 0) { tokenRefresher.refreshTokenIfNeeded() }
        verify(exactly = 0) { tokenRefresher.refreshToken(force = any()) }
    }

    private fun responseFor(request: Request, code: Int): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("{}".toByteArray().toResponseBody("application/json".toMediaType()))
            .build()
}
