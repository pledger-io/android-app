package com.pledgerio.app.domain.repository

import com.pledgerio.app.shared.Resource
import com.pledgerio.app.domain.model.IssueReportResult

interface IssueReportRepository {
    suspend fun submitBugReport(title: String, description: String): Resource<IssueReportResult>
}
