package com.pledgerio.app.data.remote.api

import com.pledgerio.app.util.AuthenticatedSessionCoordinator
import com.pledgerio.app.util.AuthenticatedSessionScope
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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var tokenRefresher: TokenRefresher
    private lateinit var authenticatedSessionCoordinator: AuthenticatedSessionCoordinator
    private lateinit var interceptor: AuthInterceptor
    private lateinit var oldScope: AuthenticatedSessionScope
    private lateinit var newScope: AuthenticatedSessionScope

    @Before
    fun setUp() {
        sessionManager = mockk(relaxed = true)
        tokenRefresher = mockk(relaxed = true)
        authenticatedSessionCoordinator = mockk(relaxed = true)
        oldScope = AuthenticatedSessionScope(
            accessToken = "old-token",
            baseUrl = "https://example.com",
            syncGeneration = "generation-a",
        )
        newScope = AuthenticatedSessionScope(
            accessToken = "new-token",
            baseUrl = "https://example.com",
            syncGeneration = "generation-a",
        )
        every { sessionManager.getAuthenticatedSessionScope() } returns oldScope
        every { sessionManager.isSessionScopeCurrent(any()) } returns true
        every { tokenRefresher.tokenForRequest(oldScope) } returns oldScope
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
        every { tokenRefresher.refreshAfterUnauthorized(oldScope) } returns newScope
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
        verify(exactly = 1) { tokenRefresher.tokenForRequest(oldScope) }
        verify(exactly = 1) { tokenRefresher.refreshAfterUnauthorized(oldScope) }
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
        every { tokenRefresher.refreshAfterUnauthorized(oldScope) } returns null
        every { chain.proceed(any()) } returns responseFor(original, 401)
        every { authenticatedSessionCoordinator.terminateSessionAsync(any()) } just Runs

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        verify(exactly = 1) { tokenRefresher.tokenForRequest(oldScope) }
        verify(exactly = 1) { tokenRefresher.refreshAfterUnauthorized(oldScope) }
        verify(exactly = 1) {
            authenticatedSessionCoordinator.terminateSessionAsync(oldScope)
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
        every { tokenRefresher.refreshAfterUnauthorized(oldScope) } returns newScope
        every { authenticatedSessionCoordinator.terminateSessionAsync(any()) } just Runs
        every { chain.proceed(any()) } returnsMany listOf(
            responseFor(original, 401),
            responseFor(original, 401),
        )

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        verify(exactly = 1) {
            authenticatedSessionCoordinator.terminateSessionAsync(newScope)
        }
    }

    @Test
    fun `intercept never retries a refreshed credential against a different server`() {
        val original = Request.Builder()
            .url("https://example.com/v2/api/accounts")
            .build()
        val chain = mockk<Interceptor.Chain>()
        val otherServerScope = AuthenticatedSessionScope(
            accessToken = "other-token",
            baseUrl = "https://other.example",
            syncGeneration = "generation-b",
        )
        every { chain.request() } returns original
        every {
            tokenRefresher.refreshAfterUnauthorized(oldScope)
        } returns otherServerScope
        every { chain.proceed(any()) } returns responseFor(original, 401)
        every { authenticatedSessionCoordinator.terminateSessionAsync(any()) } just Runs

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        verify(exactly = 1) { chain.proceed(any()) }
        verify(exactly = 1) {
            authenticatedSessionCoordinator.terminateSessionAsync(oldScope)
        }
    }

    @Test
    fun `intercept skips proactive refresh for auth endpoints`() {
        val authRequest = Request.Builder()
            .url("https://example.com/v2/api/security/authenticate")
            .build()
        val chain = mockk<Interceptor.Chain>()

        every { chain.request() } returns authRequest
        every { chain.proceed(any()) } returns responseFor(authRequest, 200)

        interceptor.intercept(chain)

        verify(exactly = 0) { tokenRefresher.tokenForRequest(any()) }
        verify(exactly = 0) { tokenRefresher.refreshAfterUnauthorized(any()) }
    }

    @Test
    fun `tombstoned session sends no credential on normal requests`() {
        val original = Request.Builder()
            .url("https://example.com/v2/api/accounts")
            .header("Authorization", "Bearer persisted-old-token")
            .build()
        val chain = mockk<Interceptor.Chain>()
        val capturedRequests = mutableListOf<Request>()
        every { sessionManager.getAuthenticatedSessionScope() } returns null
        every { chain.request() } returns original
        every {
            chain.proceed(capture(capturedRequests))
        } returns responseFor(original, 200)

        interceptor.intercept(chain)

        assertNull(capturedRequests.single().header("Authorization"))
        verify(exactly = 0) { tokenRefresher.tokenForRequest(any()) }
    }

    @Test
    fun `tombstoned session preserves captured credential only on logout request`() {
        val logoutRequest = Request.Builder()
            .url("https://example.com/v2/api/security/logout")
            .header("Authorization", "Bearer captured-old-token")
            .build()
        val chain = mockk<Interceptor.Chain>()
        val capturedRequests = mutableListOf<Request>()
        every { sessionManager.getAuthenticatedSessionScope() } returns null
        every { chain.request() } returns logoutRequest
        every {
            chain.proceed(capture(capturedRequests))
        } returns responseFor(logoutRequest, 200)

        interceptor.intercept(chain)

        assertEquals(
            "Bearer captured-old-token",
            capturedRequests.single().header("Authorization"),
        )
        verify(exactly = 0) { tokenRefresher.tokenForRequest(any()) }
    }

    @Test
    fun `pending MFA verify preserves explicit Authorization without installed session`() {
        val verifyRequest = Request.Builder()
            .url("https://example.com/v2/api/user-account/verify-2-factor")
            .header("Authorization", "Bearer pre-verify-token")
            .post("{}".toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        val chain = mockk<Interceptor.Chain>()
        val capturedRequests = mutableListOf<Request>()
        every { sessionManager.getAuthenticatedSessionScope() } returns null
        every { chain.request() } returns verifyRequest
        every {
            chain.proceed(capture(capturedRequests))
        } returns responseFor(verifyRequest, 403)

        val result = interceptor.intercept(chain)

        assertEquals(403, result.code)
        assertEquals(
            "Bearer pre-verify-token",
            capturedRequests.single().header("Authorization"),
        )
        verify(exactly = 0) { tokenRefresher.tokenForRequest(any()) }
        verify(exactly = 0) { authenticatedSessionCoordinator.terminateSessionAsync(any()) }
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
