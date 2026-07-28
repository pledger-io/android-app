package com.pledgerio.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.pledgerio.app.domain.model.IssueReportResult
import com.pledgerio.app.domain.repository.IssueReportRepository
import com.pledgerio.app.util.AppLog
import com.pledgerio.app.util.IssueReportFormatter
import com.pledgerio.app.util.IssueReportUrlBuilder
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IssueReportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLog: AppLog,
    private val sessionManager: SessionManager,
) : IssueReportRepository {

    override suspend fun submitBugReport(title: String, description: String): Resource<IssueReportResult> {
        if (title.trim().isBlank()) {
            return Resource.Error("Please enter a short summary")
        }
        if (description.trim().isBlank()) {
            return Resource.Error("Please describe what happened")
        }

        val (versionName, versionCode) = appVersion()
        val issueTitle = IssueReportFormatter.buildTitle(title)
        val whatHappened = IssueReportFormatter.buildWhatHappened(
            description = description,
            appVersionName = versionName,
            appVersionCode = versionCode,
            deviceManufacturer = IssueReportFormatter.deviceManufacturer(),
            deviceModel = IssueReportFormatter.deviceModel(),
            androidRelease = IssueReportFormatter.androidRelease(),
            androidSdk = android.os.Build.VERSION.SDK_INT,
            serverUrl = sessionManager.getBaseUrl(),
        )
        val logs = appLog.export()

        appLog.i(TAG, "Opening GitHub issue form for bug report")
        val issueUrl = IssueReportUrlBuilder.build(
            title = issueTitle,
            whatHappened = whatHappened,
            logs = logs,
        )

        return Resource.Success(
            IssueReportResult(
                issueUrl = issueUrl,
            ),
        )
    }

    private fun appVersion(): Pair<String, Int> {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val name = info.versionName ?: "unknown"
            @Suppress("DEPRECATION")
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
            name to code
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown" to 0
        }
    }

    companion object {
        private const val TAG = "IssueReport"
    }
}
