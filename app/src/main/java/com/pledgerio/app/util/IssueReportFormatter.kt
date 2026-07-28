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
    ): String = buildString {
        appendLine(description.trim().ifBlank { "_No description provided._" })
        appendLine()
        appendLine("**Environment**")
        appendLine("- Source: Android mobile app")
        appendLine("- App: Pledger Android $appVersionName ($appVersionCode)")
        appendLine("- Device: $deviceManufacturer $deviceModel")
        appendLine("- Android: $androidRelease (API $androidSdk)")
        appendLine("- Server: ${serverUrl?.let(LogSanitizer::sanitizeServerUrl) ?: "not configured"}")
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
        ))
        appendLine()
        appendLine("### Relevant log output")
        appendLine()
        appendLine("```shell")
        appendLine(buildLogExcerpt(logs))
        append("```")
    }

    fun buildLogExcerpt(logs: String, maxChars: Int = MAX_BODY_LOG_CHARS): String {
        require(maxChars > 0)
        val sanitized = LogSanitizer.sanitize(logs.trim())
        if (sanitized.isBlank()) return "_No logs captured._"
        if (sanitized.length <= maxChars) return sanitized

        val omitted = sanitized.length - maxChars
        return "… ($omitted characters omitted; showing recent redacted logs)\n" +
            sanitized.takeLast(maxChars)
    }

    fun deviceManufacturer(): String = Build.MANUFACTURER.orEmpty()

    fun deviceModel(): String = Build.MODEL.orEmpty()

    fun androidRelease(): String = Build.VERSION.RELEASE.orEmpty()

    private const val MAX_BODY_LOG_CHARS = 4_000
}
