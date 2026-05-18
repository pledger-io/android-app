package com.pledgerio.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object ServerSetup : Screen("server_setup?changeServer={changeServer}") {
        fun createRoute(changeServer: Boolean = false) =
            "server_setup?changeServer=$changeServer"
    }
    data object Transactions : Screen("transactions?expenseId={expenseId}&expenseName={expenseName}") {
        fun createRoute(expenseId: Long? = null, expenseName: String? = null): String {
            if (expenseId == null) return "transactions?expenseId=-1&expenseName="
            val name = expenseName.orEmpty()
            return "transactions?expenseId=$expenseId&expenseName=${android.net.Uri.encode(name)}"
        }
    }
    data object Login : Screen("login")
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
    data object BudgetDetail : Screen("budget/{budgetId}") {
        fun createRoute(budgetId: Long) = "budget/$budgetId"
    }
    data object Reports : Screen("reports")
    data object Settings : Screen("settings")
    data object Categories : Screen("categories")
    data object Tags : Screen("tags")
}
