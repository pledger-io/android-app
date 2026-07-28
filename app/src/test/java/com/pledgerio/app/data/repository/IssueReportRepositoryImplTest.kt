package com.pledgerio.app.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.pledgerio.app.util.AppLog
import com.pledgerio.app.util.Resource
import com.pledgerio.app.util.SessionManager
import io.mockk.every
import io.mockk.mockk
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IssueReportRepositoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val appLog = mockk<AppLog>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private lateinit var repository: IssueReportRepositoryImpl

    @Before
    fun setUp() {
        every { appLog.export() } returns "sample log"
        every { sessionManager.getBaseUrl() } returns "https://example.com"
        val packageManager = mockk<PackageManager>(relaxed = true)
        val packageInfo = PackageInfo().apply {
            versionName = "1.0.0"
            @Suppress("DEPRECATION")
            versionCode = 7
        }
        every { context.packageName } returns "com.pledgerio.app"
        every { context.packageManager } returns packageManager
        @Suppress("DEPRECATION")
        every { packageManager.getPackageInfo("com.pledgerio.app", 0) } returns packageInfo
        repository = IssueReportRepositoryImpl(context, appLog, sessionManager)
    }

    @Test
    fun `submitBugReport requires summary`() = runTest {
        val result = repository.submitBugReport(title = "  ", description = "Something broke")
        assertTrue(result is Resource.Error)
    }

    @Test
    fun `submitBugReport requires description`() = runTest {
        val result = repository.submitBugReport(title = "Crash", description = "")
        assertTrue(result is Resource.Error)
    }

    @Test
    fun `submitBugReport returns github form url`() = runTest {
        val result = repository.submitBugReport(title = "Sync fails", description = "Steps here")
        assertTrue(result is Resource.Success)
        val url = (result as Resource.Success).data.issueUrl
        assertTrue(url.contains("pledger-io/.github/issues/new"))
        assertTrue(url.contains("template=bug_report.yml"))
        assertTrue(url.contains("what-happened="))
        assertTrue(url.contains("logs="))
    }

    @Test
    fun `submitBugReport redacts and bounds logs in url`() = runTest {
        every { appLog.export() } returns
            "GET /search?description=Salary&amount=1234.56\n".repeat(300)
        val result = repository.submitBugReport(title = "Crash", description = "Big logs")
        val url = (result as Resource.Success).data.issueUrl
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8)

        assertFalse(decoded.contains("Salary"))
        assertFalse(decoded.contains("1234.56"))
        assertTrue(url.length <= 7_500)
    }
}
