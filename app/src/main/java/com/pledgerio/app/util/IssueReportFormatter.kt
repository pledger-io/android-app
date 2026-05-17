package com.pledgerio.app.util

import android.os.Build

object IssueReportFormatter {

    fun buildTitle(userTitle: String): String {
        val trimmed = userTitle.trim()
        val prefix = "[Android]"
        return if (trimmed.isBlank()) {
            "$prefix Mobile app bug report"
        } else if (trimmed.startsWith("[Bug]", ignoreCase = true) || trimmed.startsWith("[Android]", ignoreCase = true)) {
            trimmed
        } else {
            "$prefix $trimmed"
        }
    }

    /** Text for the GitHub issue form field `what-happened` (includes environment). */
    fun buildWhatHappened(
        description: String,
        appVersionName: String,
        appVersionCode: Int,
        deviceManufacturer: String,
        deviceModel: String,
        androidRelease: String,
        androidSdk: Int,
        serverUrl: String?,
        username: String?,
    ): String = buildString {
        appendLine(description.trim().ifBlank { "_No description provided._" })
        appendLine()
        appendLine("**Environment**")
        appendLine("- Source: Android mobile app")
        appendLine("- App: Pledger Android $appVersionName ($appVersionCode)")
        appendLine("- Device: $deviceManufacturer $deviceModel")
        appendLine("- Android: $androidRelease (API $androidSdk)")
        appendLine("- Server: ${serverUrl?.let(LogSanitizer::sanitizeUrl) ?: "not configured"}")
        if (!username.isNullOrBlank()) {
            appendLine("- Username: ${username.trim()}")
        }
    }

    fun buildBody(
        description: String,
        appVersionName: String,
        appVersionCode: Int,
        deviceManufacturer: String,
        deviceModel: String,
        androidRelease: String,
        androidSdk: Int,
        serverUrl: String?,
        username: String?,
        logs: String,
    ): String = buildString {
        appendLine(buildWhatHappened(
            description = description,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            deviceManufacturer = deviceManufacturer,
            deviceModel = deviceModel,
            androidRelease = androidRelease,
            androidSdk = androidSdk,
            serverUrl = serverUrl,
            username = username,
        ))
        appendLine()
        appendLine("### Relevant log output")
        appendLine()
        appendLine("```shell")
        appendLine(if (logs.isBlank()) "_No logs captured._" else logs)
        append("```")
    }

    fun deviceManufacturer(): String = Build.MANUFACTURER.orEmpty()

    fun deviceModel(): String = Build.MODEL.orEmpty()

    fun androidRelease(): String = Build.VERSION.RELEASE.orEmpty()
}
