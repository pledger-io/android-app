package com.pledgerio.app.settings.domain.model

/**
 * @param issueUrl GitHub issue form URL with fields prefilled; user submits in the browser.
 */
data class IssueReportResult(
    val issueUrl: String,
)
