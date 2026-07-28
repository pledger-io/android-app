package com.pledgerio.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun `redacts bearer tokens`() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"
        val sanitized = LogSanitizer.sanitize(input)
        assertEquals("Authorization: Bearer [REDACTED]", sanitized)
    }

    @Test
    fun `redacts sensitive json fields`() {
        val input = """{"access_token":"secret","refresh_token":"also-secret"}"""
        val sanitized = LogSanitizer.sanitize(input)
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("also-secret"))
    }

    @Test
    fun `redacts every query parameter value`() {
        val input = "https://example.com/callback?access_token=abc&page=1"
        val sanitized = LogSanitizer.sanitizeUrl(input)
        assertEquals(
            "https://example.com/callback?access_token=[REDACTED]&page=[REDACTED]",
            sanitized,
        )
    }

    @Test
    fun `redacts description and amount query values`() {
        val input =
            "https://example.com/v2/api/ai/auto-complete?description=Salary%20July&amount=1234.56"

        val sanitized = LogSanitizer.sanitizeUrl(input)

        assertFalse(sanitized.contains("Salary"))
        assertFalse(sanitized.contains("1234.56"))
        assertTrue(sanitized.contains("description=[REDACTED]"))
        assertTrue(sanitized.contains("amount=[REDACTED]"))
    }

    @Test
    fun `sanitizes legacy full request urls to route templates`() {
        val sanitized = LogSanitizer.sanitize(
            "GET https://private.example.com/v2/api/user-account/alice/sessions/42" +
                "?description=Salary → 200",
        )

        assertEquals(
            "GET /v2/api/user-account/{user}/sessions/{session} → 200",
            sanitized,
        )
    }

    @Test
    fun `server url keeps origin only`() {
        assertEquals(
            "https://example.com:8443",
            LogSanitizer.sanitizeServerUrl(
                "https://example.com:8443/private/path?description=Salary#token",
            ),
        )
    }

    @Test
    fun `route template hides identifiers and rejects unknown routes`() {
        assertEquals(
            "/v2/api/user-account/{user}/sessions/{session}",
            LogSanitizer.routeTemplate("/v2/api/user-account/alice/sessions/42"),
        )
        assertEquals(
            "/<unrecognized-route>",
            LogSanitizer.routeTemplate("/private/alice/transactions"),
        )
    }
}
