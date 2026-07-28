package com.pledgerio.app.util

import java.net.URI

/**
 * Redacts secrets and financial values before logs leave the app.
 */
object LogSanitizer {

    private val sensitiveJsonKeys = Regex(
        """"(access_?token|refresh_?token|password|secret|authorization|description|amount|account_?name|source|destination)"\s*:\s*("[^"]*"|[^,}\s]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val bearerToken = Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE)
    private val basicAuth = Regex("""Basic\s+\S+""", RegexOption.IGNORE_CASE)
    private val queryParameter = Regex("""([?&])([^=&\s#]+)=([^&#\s]*)""")
    private val absoluteUrl = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    private val allowedRouteTemplates = listOf(
        Regex("""^/health/?$""") to "/health",
        Regex("""^/\.well-known/openid-connect/?$""") to "/.well-known/openid-connect",
        Regex("""^/v2/api/security/authenticate/?$""") to "/v2/api/security/authenticate",
        Regex("""^/v2/api/security/oauth/?$""") to "/v2/api/security/oauth",
        Regex("""^/v2/api/security/logout/?$""") to "/v2/api/security/logout",
        Regex("""^/v2/api/user-account/verify-2-factor/?$""") to
            "/v2/api/user-account/verify-2-factor",
        Regex("""^/v2/api/user-account/[^/]+/sessions/[^/]+/?$""") to
            "/v2/api/user-account/{user}/sessions/{session}",
        Regex("""^/v2/api/user-account/[^/]+/sessions/?$""") to
            "/v2/api/user-account/{user}/sessions",
        Regex("""^/v2/api/user-account/[^/]+/2-factor/?$""") to
            "/v2/api/user-account/{user}/2-factor",
        Regex("""^/v2/api/user-account/[^/]+/?$""") to "/v2/api/user-account/{user}",
        Regex("""^/v2/api/accounts/[^/]+/?$""") to "/v2/api/accounts/{id}",
        Regex("""^/v2/api/accounts/?$""") to "/v2/api/accounts",
        Regex("""^/v2/api/account-types/?$""") to "/v2/api/account-types",
        Regex("""^/v2/api/transactions/[^/]+/?$""") to "/v2/api/transactions/{id}",
        Regex("""^/v2/api/transactions/?$""") to "/v2/api/transactions",
        Regex("""^/v2/api/ai/auto-complete/?$""") to "/v2/api/ai/auto-complete",
        Regex("""^/v2/api/ai/extract/?$""") to "/v2/api/ai/extract",
        Regex("""^/v2/api/categories/[^/]+/?$""") to "/v2/api/categories/{id}",
        Regex("""^/v2/api/categories/?$""") to "/v2/api/categories",
        Regex("""^/v2/api/tags/[^/]+/?$""") to "/v2/api/tags/{tag}",
        Regex("""^/v2/api/tags/?$""") to "/v2/api/tags",
        Regex("""^/v2/api/budgets/expenses/balance/?$""") to
            "/v2/api/budgets/expenses/balance",
        Regex("""^/v2/api/budgets/expenses/?$""") to "/v2/api/budgets/expenses",
        Regex("""^/v2/api/budgets/?$""") to "/v2/api/budgets",
        Regex("""^/v2/api/balance/by-date/[^/]+/?$""") to "/v2/api/balance/by-date/{type}",
        Regex("""^/v2/api/balance/[^/]+/?$""") to "/v2/api/balance/{partition}",
        Regex("""^/v2/api/balance/?$""") to "/v2/api/balance",
        Regex("""^/v2/api/contracts/?$""") to "/v2/api/contracts",
        Regex("""^/v2/api/currencies/?$""") to "/v2/api/currencies",
    )

    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(sensitiveJsonKeys) { match ->
                """"${match.groupValues[1]}": "[REDACTED]""""
            }
            .replace(bearerToken, "Bearer [REDACTED]")
            .replace(basicAuth, "Basic [REDACTED]")
            .replace(absoluteUrl) { sanitizeAbsoluteUrl(it.value) }
            .replace(queryParameter) {
                "${it.groupValues[1]}${it.groupValues[2]}=[REDACTED]"
            }
    }

    /** Keeps query names for diagnosis but never their values or a URL fragment. */
    fun sanitizeUrl(url: String): String =
        url.substringBefore('#').replace(queryParameter) {
            "${it.groupValues[1]}${it.groupValues[2]}=[REDACTED]"
        }

    /** Only the origin is useful in an issue report; configured paths and queries are private. */
    fun sanitizeServerUrl(url: String): String = runCatching {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        require(scheme == "http" || scheme == "https")
        require(!host.isNullOrBlank())
        val displayHost = if (host.contains(':')) "[$host]" else host
        val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
        "$scheme://$displayHost$port"
    }.getOrDefault("[REDACTED]")

    /** Maps only known API paths to fixed templates, hiding IDs, usernames, and tag names. */
    fun routeTemplate(encodedPath: String): String =
        allowedRouteTemplates.firstOrNull { (pattern, _) -> pattern.matches(encodedPath) }
            ?.second
            ?: "/<unrecognized-route>"

    private fun sanitizeAbsoluteUrl(url: String): String = runCatching {
        val uri = URI(url)
        val route = routeTemplate(uri.rawPath.orEmpty())
        if (route == "/<unrecognized-route>") {
            "${uri.scheme.lowercase()}://[REDACTED]"
        } else {
            route
        }
    }.getOrDefault("[REDACTED-URL]")
}
