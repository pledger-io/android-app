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
    private const val MAX_LOG_FIELD_CHARS = 4_000

    fun build(
        title: String,
        whatHappened: String,
        logs: String,
    ): Pair<String, String?> {
        val trimmedLogs = logs.trim()
        var logsForUrl = trimmedLogs
        var clipboard: String? = null

        if (logsForUrl.length > MAX_LOG_FIELD_CHARS) {
            logsForUrl = logsForUrl.take(MAX_LOG_FIELD_CHARS) + "\n… (truncated for URL; full logs on clipboard)"
            clipboard = buildClipboardFallback(title, whatHappened, trimmedLogs)
        }

        var url = composeUrl(title, whatHappened, logsForUrl)

        if (url.length > MAX_URL_CHARS) {
            val shorterLogs = trimmedLogs.take(1_500) + "\n… (truncated; full logs on clipboard)"
            clipboard = buildClipboardFallback(title, whatHappened, trimmedLogs)
            url = composeUrl(title, whatHappened, shorterLogs)
        }

        return url to clipboard
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

    private fun buildClipboardFallback(
        title: String,
        whatHappened: String,
        logs: String,
    ): String = buildString {
        appendLine("Title: $title")
        appendLine()
        appendLine(whatHappened)
        appendLine()
        appendLine("### Relevant log output")
        appendLine()
        appendLine(logs)
    }
}
