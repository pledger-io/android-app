package com.pledgerio.app.util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueReportUrlBuilderTest {

    @Test
    fun `build includes template and prefilled fields`() {
        val url = IssueReportUrlBuilder.build(
            title = "[Android] Crash",
            whatHappened = "App freezes on login",
            logs = "INFO/Http GET → 200",
        )
        assertTrue(url.startsWith("https://github.com/pledger-io/.github/issues/new?"))
        assertTrue(url.contains("template=bug_report.yml"))
        assertTrue(url.contains("title="))
        assertTrue(url.contains("what-happened="))
        assertTrue(url.contains("logs="))
    }

    @Test
    fun `build includes only a truncated redacted log excerpt`() {
        val url = IssueReportUrlBuilder.build(
            title = "[Android] Crash",
            whatHappened = "Description",
            logs = "GET /search?description=Salary&amount=1234.56\n".repeat(300),
        )
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8)

        assertTrue(decoded.contains("logs="))
        assertTrue(decoded.contains("[REDACTED]"))
        assertTrue(decoded.contains("characters omitted"))
        assertFalse(decoded.contains("Salary"))
        assertFalse(decoded.contains("1234.56"))
        assertTrue(url.length <= 7_500)
    }
}
