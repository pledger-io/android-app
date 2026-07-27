package com.pledgerio.app.ui.navigation

import android.net.Uri
import java.net.URI
import java.time.YearMonth

sealed class DeepLink {
    data class Transaction(val id: Long) : DeepLink()
    data class Account(val id: Long) : DeepLink()
    data class Budgets(val yearMonth: YearMonth?) : DeepLink()
}

object DeepLinkParser {
    private const val SCHEME = "pledger"

    fun parse(uri: Uri?): DeepLink? {
        if (uri == null || uri.scheme != SCHEME) return null
        return when (uri.host) {
            "transaction" -> {
                val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: return null
                DeepLink.Transaction(id)
            }
            "account" -> {
                val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: return null
                DeepLink.Account(id)
            }
            "budgets" -> {
                val year = uri.getQueryParameter("year")?.toIntOrNull()
                val month = uri.getQueryParameter("month")?.toIntOrNull()
                DeepLink.Budgets(yearMonthOrNull(year, month))
            }
            else -> null
        }
    }

    /**
     * JVM-safe parse for unit tests and string-built notification URIs.
     * Prefer [parse] with [Uri] from Intents in production code.
     */
    fun parse(uriString: String?): DeepLink? {
        if (uriString.isNullOrBlank()) return null
        val uri = runCatching { URI(uriString) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME) return null
        val host = uri.host ?: return null
        val pathSegments = uri.path
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }
            ?.split('/')
            .orEmpty()
        val query = parseQuery(uri.rawQuery)
        return when (host) {
            "transaction" -> {
                val id = pathSegments.firstOrNull()?.toLongOrNull() ?: return null
                DeepLink.Transaction(id)
            }
            "account" -> {
                val id = pathSegments.firstOrNull()?.toLongOrNull() ?: return null
                DeepLink.Account(id)
            }
            "budgets" -> {
                val year = query["year"]?.toIntOrNull()
                val month = query["month"]?.toIntOrNull()
                DeepLink.Budgets(yearMonthOrNull(year, month))
            }
            else -> null
        }
    }

    private fun yearMonthOrNull(year: Int?, month: Int?): YearMonth? =
        if (year != null && month != null) {
            runCatching { YearMonth.of(year, month) }.getOrNull()
        } else {
            null
        }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = part.substring(0, idx)
            val value = part.substring(idx + 1)
            key to value
        }.toMap()
    }
}
