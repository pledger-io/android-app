package com.pledgerio.app.ui.navigation

sealed class Screen(val route: String) {
    data object ServerSetup : Screen("server_setup")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Transactions : Screen("transactions")
    data object TransactionDetail : Screen("transaction/{transactionId}") {
        fun createRoute(transactionId: Long) = "transaction/$transactionId"
    }
    data object AddTransaction : Screen("transaction/add")
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
    data object Budgets : Screen("budgets")
    data object BudgetDetail : Screen("budget/{budgetId}") {
        fun createRoute(budgetId: Long) = "budget/$budgetId"
    }
    data object Reports : Screen("reports")
    data object Settings : Screen("settings")
    data object Categories : Screen("categories")
}
