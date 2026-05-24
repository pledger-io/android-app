package com.pledgerio.app.domain.repository

import com.pledgerio.app.domain.model.IssueReportResult
import com.pledgerio.app.domain.common.Resource

interface IssueReportRepository {
    suspend fun submitBugReport(title: String, description: String): Resource<IssueReportResult>
}
