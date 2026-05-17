package com.pledgerio.app.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueReportUrlBuilderTest {

    @Test
    fun `build includes template and prefilled fields`() {
        val (url, clipboard) = IssueReportUrlBuilder.build(
            title = "[Android] Crash",
            whatHappened = "App freezes on login",
            logs = "INFO/Http GET → 200",
        )
        assertTrue(url.startsWith("https://github.com/pledger-io/.github/issues/new?"))
        assertTrue(url.contains("template=bug_report.yml"))
        assertTrue(url.contains("title="))
        assertTrue(url.contains("what-happened="))
        assertTrue(url.contains("logs="))
        assertNull(clipboard)
    }

    @Test
    fun `build copies full logs to clipboard when field is too large`() {
        val (url, clipboard) = IssueReportUrlBuilder.build(
            title = "[Android] Crash",
            whatHappened = "Description",
            logs = "line\n".repeat(3_000),
        )
        assertTrue(url.contains("logs="))
        assertNotNull(clipboard)
        assertTrue(clipboard!!.contains("line"))
    }
}
