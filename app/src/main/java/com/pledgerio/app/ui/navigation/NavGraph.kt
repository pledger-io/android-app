package com.pledgerio.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pledgerio.app.ui.accounts.AccountDetailScreen
import com.pledgerio.app.ui.accounts.AccountFormScreen
import com.pledgerio.app.ui.accounts.AccountsScreen
import com.pledgerio.app.ui.accounts.AccountsViewModel
import com.pledgerio.app.ui.budgets.BudgetDetailScreen
import com.pledgerio.app.ui.budgets.BudgetsScreen
import com.pledgerio.app.ui.budgets.BudgetsViewModel
import com.pledgerio.app.ui.dashboard.DashboardScreen
import com.pledgerio.app.ui.onboarding.LoginScreen
import com.pledgerio.app.ui.onboarding.ServerSetupScreen
import com.pledgerio.app.ui.reports.ReportsScreen
import com.pledgerio.app.ui.settings.SettingsScreen
import com.pledgerio.app.ui.transactions.TransactionDetailScreen
import com.pledgerio.app.ui.transactions.TransactionFormScreen
import com.pledgerio.app.ui.transactions.TransactionsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.ServerSetup.route) {
            ServerSetupScreen(
                onServerValidated = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ServerSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBackToServer = {
                    navController.navigate(Screen.ServerSetup.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToTransactions = {
                    navController.navigate(Screen.Transactions.route)
                },
                onNavigateToTransaction = { id ->
                    navController.navigate(Screen.TransactionDetail.createRoute(id))
                },
                onNavigateToAddTransaction = {
                    navController.navigate(Screen.AddTransaction.route)
                },
                onNavigateToAddAccount = {
                    navController.navigate(Screen.AddAccount.createRoute())
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
            )
        }

        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.TransactionDetail.createRoute(id))
                },
                onNavigateToAdd = {
                    navController.navigate(Screen.AddTransaction.route)
                },
            )
        }

        composable(Screen.AddTransaction.route) {
            TransactionFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddAccount = { typeCode ->
                    navController.navigate(Screen.AddAccount.createRoute(typeCode))
                },
            )
        }

        composable(
            route = Screen.EditTransaction.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
        ) {
            TransactionFormScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddAccount = { typeCode ->
                    navController.navigate(Screen.AddAccount.createRoute(typeCode))
                },
            )
        }

        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
        ) {
            TransactionDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { transactionId ->
                    navController.navigate(Screen.EditTransaction.createRoute(transactionId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Screen.Accounts.route) {
            val accountsViewModel: AccountsViewModel = hiltViewModel()
            AccountsScreen(
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.AccountDetail.createRoute(id))
                },
                onNavigateToAdd = { typeCode ->
                    navController.navigate(Screen.AddAccount.createRoute(typeCode))
                },
                viewModel = accountsViewModel,
            )
        }

        composable(
            route = Screen.AccountDetail.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) {
            AccountDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTransaction = { id ->
                    navController.navigate(Screen.TransactionDetail.createRoute(id))
                },
                onNavigateToEdit = { accountId ->
                    navController.navigate(Screen.EditAccount.createRoute(accountId))
                },
            )
        }

        composable(
            route = Screen.AddAccount.route,
            arguments = listOf(
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            AccountFormRoute(navController = navController) {
                navController.popBackStack()
            }
        }

        composable(
            route = Screen.EditAccount.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) {
            AccountFormRoute(navController = navController) {
                navController.popBackStack()
            }
        }

        composable(Screen.Budgets.route) {
            val budgetsViewModel: BudgetsViewModel = hiltViewModel()
            BudgetsScreen(
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.BudgetDetail.createRoute(id))
                },
                viewModel = budgetsViewModel,
            )
        }

        composable(
            route = Screen.BudgetDetail.route,
            arguments = listOf(navArgument("budgetId") { type = NavType.LongType })
        ) {
            val budgetsViewModel: BudgetsViewModel = hiltViewModel(
                navController.getBackStackEntry(Screen.Budgets.route),
            )
            BudgetDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onBudgetListUpdated = budgetsViewModel::applyBudgetListState,
            )
        }

        composable(Screen.Reports.route) {
            ReportsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.ServerSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun AccountFormRoute(
    navController: NavHostController,
    onNavigateBack: () -> Unit,
) {
    val accountsEntry = navController.currentBackStack.value
        .firstOrNull { it.destination.route == Screen.Accounts.route }
    if (accountsEntry != null) {
        val accountsViewModel: AccountsViewModel = hiltViewModel(accountsEntry)
        AccountFormScreen(
            onNavigateBack = onNavigateBack,
            onAccountSaved = accountsViewModel::applyAccountSaved,
        )
    } else {
        AccountFormScreen(onNavigateBack = onNavigateBack)
    }
}
