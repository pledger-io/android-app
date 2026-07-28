package com.pledgerio.app.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds a GitHub issue-form URL with prefilled fields (see pledger-io/.github bug_report.yml).
 * No API token is required — the user completes submission in the browser.
 */
object IssueReportUrlBuilder {

    private const val ISSUE_NEW_URL = "https://github.com/pledger-io/.github/issues/new"
    private const val TEMPLATE = "bug_report.yml"
    private const val MAX_URL_CHARS = 7_500
    private const val MAX_TITLE_CHARS = 200
    private const val MAX_WHAT_HAPPENED_CHARS = 3_500
    private const val MAX_LOG_FIELD_CHARS = 1_500

    fun build(
        title: String,
        whatHappened: String,
        logs: String,
    ): String {
        val safeTitle = title.trim().take(MAX_TITLE_CHARS)
        var safeWhatHappened = truncate(whatHappened.trim(), MAX_WHAT_HAPPENED_CHARS)
        var safeLogs = IssueReportFormatter.buildLogExcerpt(logs, MAX_LOG_FIELD_CHARS)
        var url = composeUrl(safeTitle, safeWhatHappened, safeLogs)

        if (url.length > MAX_URL_CHARS) {
            safeLogs = "_Redacted logs omitted because the prefilled URL was too long._"
            safeWhatHappened = truncate(safeWhatHappened, 1_500)
            url = composeUrl(safeTitle, safeWhatHappened, safeLogs)
        }

        if (url.length > MAX_URL_CHARS) {
            url = composeUrl(
                safeTitle.take(100),
                "_Description omitted because the prefilled URL was too long._",
                safeLogs,
            )
        }

        return url
    }

    private fun composeUrl(title: String, whatHappened: String, logs: String): String {
        val query = listOf(
            "template" to TEMPLATE,
            "title" to title,
            "what-happened" to whatHappened,
            "logs" to logs,
        ).joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$ISSUE_NEW_URL?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun truncate(value: String, maxChars: Int): String =
        if (value.length <= maxChars) value else value.take(maxChars) + "\n… (truncated)"
}
