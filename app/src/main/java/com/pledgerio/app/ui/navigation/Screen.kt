package com.pledgerio.app.ui.navigation

import android.net.Uri
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object ServerSetup : Screen("server_setup?changeServer={changeServer}") {
        fun createRoute(changeServer: Boolean = false) =
            "server_setup?changeServer=$changeServer"
    }
    data object Transactions : Screen(
        "transactions?" +
            "expenseId={expenseId}&expenseName={expenseName}&" +
            "categoryId={categoryId}&categoryName={categoryName}&" +
            "year={year}&month={month}",
    ) {
        fun createRoute(
            expenseId: Long? = null,
            expenseName: String? = null,
            categoryId: Long? = null,
            categoryName: String? = null,
            year: Int? = null,
            month: Int? = null,
        ): String {
            val expId = expenseId ?: -1L
            val expName = encodeQueryParam(expenseName.orEmpty())
            val catId = categoryId ?: -1L
            val catName = encodeQueryParam(categoryName.orEmpty())
            val y = year ?: -1
            val m = month ?: -1
            return "transactions?" +
                "expenseId=$expId&expenseName=$expName&" +
                "categoryId=$catId&categoryName=$catName&" +
                "year=$y&month=$m"
        }
    }
    data object Login : Screen("login")
    data object Verify2Factor : Screen("login/verify-2fa")
    data object Dashboard : Screen("dashboard")
    data object TransactionDetail : Screen("transaction/{transactionId}") {
        fun createRoute(transactionId: Long) = "transaction/$transactionId"
    }
    data object AddTransaction : Screen(
        "transaction/add?" +
            "prefillDescription={prefillDescription}&" +
            "prefillAmount={prefillAmount}&" +
            "prefillCurrency={prefillCurrency}&" +
            "prefillDate={prefillDate}&" +
            "prefillType={prefillType}&" +
            "prefillSource={prefillSource}&" +
            "prefillTarget={prefillTarget}"
    ) {
        fun createRoute(
            prefillDescription: String? = null,
            prefillAmount: String? = null,
            prefillCurrency: String? = null,
            prefillDate: String? = null,
            prefillType: String? = null,
            prefillSource: String? = null,
            prefillTarget: String? = null,
        ): String {
            val params = listOf(
                "prefillDescription" to prefillDescription,
                "prefillAmount" to prefillAmount,
                "prefillCurrency" to prefillCurrency,
                "prefillDate" to prefillDate,
                "prefillType" to prefillType,
                "prefillSource" to prefillSource,
                "prefillTarget" to prefillTarget,
            ).joinToString("&") { (key, value) ->
                "$key=${Uri.encode(value.orEmpty())}"
            }
            return "transaction/add?$params"
        }
    }
    data object InvoiceScan : Screen("transaction/scan")
    data object EditTransaction : Screen("transaction/{transactionId}/edit") {
        fun createRoute(transactionId: Long) = "transaction/$transactionId/edit"
    }
    data object Accounts : Screen("accounts")
    data object AccountDetail : Screen("account/{accountId}") {
        fun createRoute(accountId: Long) = "account/$accountId"
    }
    data object AddAccount : Screen("account/add?type={type}") {
        fun createRoute(typeCode: String? = null): String =
            if (typeCode.isNullOrBlank()) "account/add" else "account/add?type=$typeCode"
    }
    data object EditAccount : Screen("account/{accountId}/edit") {
        fun createRoute(accountId: Long) = "account/$accountId/edit"
    }
    data object Budgets : Screen("budgets?year={year}&month={month}") {
        fun createRoute(year: Int? = null, month: Int? = null): String {
            if (year == null || month == null) return "budgets?year=-1&month=-1"
            return "budgets?year=$year&month=$month"
        }
    }
    data object Search : Screen("search")
    data object BudgetDetail : Screen("budget/{budgetId}?year={year}&month={month}") {
        fun createRoute(budgetId: Long, year: Int = -1, month: Int = -1) =
            "budget/$budgetId?year=$year&month=$month"
    }
    data object Reports : Screen("reports")
    data object Settings : Screen("settings")
    data object ApiSessions : Screen("settings/sessions")
    data object MfaSetup : Screen("settings/mfa")
    data object Categories : Screen("categories")
    data object Tags : Screen("tags")
}

/** JVM-safe query encoding (avoids android.net.Uri in unit tests). */
private fun encodeQueryParam(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

