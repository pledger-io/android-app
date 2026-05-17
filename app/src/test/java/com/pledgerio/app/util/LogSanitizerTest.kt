package com.pledgerio.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `redacts token query parameters`() {
        val input = "https://example.com/callback?access_token=abc&page=1"
        val sanitized = LogSanitizer.sanitizeUrl(input)
        assertEquals("https://example.com/callback?access_token=[REDACTED]&page=1", sanitized)
    }
}
