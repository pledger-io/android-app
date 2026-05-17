package com.pledgerio.app.ui.navigation

import android.net.Uri
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
                val yearMonth = if (year != null && month != null) {
                    runCatching { YearMonth.of(year, month) }.getOrNull()
                } else {
                    null
                }
                DeepLink.Budgets(yearMonth)
            }
            else -> null
        }
    }
}
