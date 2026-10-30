package com.pledgerio.app.settings.domain.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.settings.domain.model.IssueReportResult

interface IssueReportRepository {
    suspend fun submitBugReport(title: String, description: String): Resource<IssueReportResult>
}
