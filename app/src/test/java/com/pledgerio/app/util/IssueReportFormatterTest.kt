package com.pledgerio.app.util

import org.junit.Assert.assertTrue
import org.junit.Test

class IssueReportFormatterTest {

    @Test
    fun `buildTitle prefixes android when missing`() {
        val title = IssueReportFormatter.buildTitle("Sync fails on login")
        assertTrue(title.startsWith("[Android]"))
        assertTrue(title.contains("Sync fails on login"))
    }

    @Test
    fun `buildWhatHappened includes environment`() {
        val text = IssueReportFormatter.buildWhatHappened(
            description = "App crashes",
            appVersionName = "1.0.0",
            appVersionCode = 1,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 9",
            androidRelease = "15",
            androidSdk = 35,
            serverUrl = "https://firefly.example.com",
            username = "demo",
        )
        assertTrue(text.contains("App crashes"))
        assertTrue(text.contains("Android mobile app"))
        assertTrue(text.contains("https://firefly.example.com"))
    }

    @Test
    fun `buildBody includes environment and logs`() {
        val body = IssueReportFormatter.buildBody(
            description = "App crashes when opening budgets",
            appVersionName = "1.0.0",
            appVersionCode = 1,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 9",
            androidRelease = "15",
            androidSdk = 35,
            serverUrl = "https://firefly.example.com",
            username = "demo",
            logs = "2026-01-01 INFO/Http GET → 200",
        )
        assertTrue(body.contains("App crashes when opening budgets"))
        assertTrue(body.contains("Android mobile app"))
        assertTrue(body.contains("https://firefly.example.com"))
        assertTrue(body.contains("```shell"))
        assertTrue(body.contains("INFO/Http"))
    }
}
