package com.pledgerio.app.domain.model

/**
 * @param issueUrl GitHub issue form URL with fields prefilled; user submits in the browser.
 * @param clipboardText When non-null, full logs (or report) were copied because the URL could not fit them.
 */
data class IssueReportResult(
    val issueUrl: String,
    val clipboardText: String? = null,
)
