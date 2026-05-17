package com.pledgerio.app.util

/**
 * Redacts secrets before logs are attached to GitHub issues or shown to users.
 */
object LogSanitizer {

    private val sensitiveJsonKeys = Regex(
        """"(access_?token|refresh_?token|password|secret|authorization)"\s*:\s*"[^"]*"""",
        RegexOption.IGNORE_CASE,
    )
    private val bearerToken = Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE)
    private val basicAuth = Regex("""Basic\s+\S+""", RegexOption.IGNORE_CASE)
    private val queryToken = Regex("""([?&])(token|access_token|refresh_token|password)=[^&\s]*""", RegexOption.IGNORE_CASE)

    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(sensitiveJsonKeys) { match ->
                val key = match.value.substringBefore(':')
                """$key: "[REDACTED]""""
            }
            .replace(bearerToken, "Bearer [REDACTED]")
            .replace(basicAuth, "Basic [REDACTED]")
            .replace(queryToken) { "${it.groupValues[1]}${it.groupValues[2]}=[REDACTED]" }
    }

    fun sanitizeUrl(url: String): String = sanitize(url)
}
