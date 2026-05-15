package com.pledgerio.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pledgerio.app.R
import com.pledgerio.app.ui.navigation.NavGraph
import com.pledgerio.app.ui.navigation.Screen
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.TextSecondary
import com.pledgerio.app.util.SessionManager
import javax.inject.Inject

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val labelResId: Int,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard.route, Icons.Default.Home, R.string.tab_dashboard),
    BottomNavItem(Screen.Transactions.route, Icons.Default.Receipt, R.string.tab_transactions),
    BottomNavItem(Screen.Budgets.route, Icons.Default.PieChart, R.string.tab_budgets),
    BottomNavItem(Screen.Accounts.route, Icons.Default.AccountBalance, R.string.tab_accounts),
    BottomNavItem(Screen.Reports.route, Icons.Default.BarChart, R.string.tab_reports),
)

private val mainScreens = setOf(
    Screen.Dashboard.route,
    Screen.Transactions.route,
    Screen.Budgets.route,
    Screen.Accounts.route,
    Screen.Reports.route,
)

@Composable
fun PledgerApp(
    sessionManager: SessionManager = hiltViewModel<AppViewModel>().sessionManager,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val startDestination = remember {
        when {
            sessionManager.getBaseUrl() == null -> Screen.ServerSetup.route
            !sessionManager.isLoggedIn() -> Screen.Login.route
            else -> Screen.Dashboard.route
        }
    }

    val showBottomBar = currentRoute in mainScreens

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = navBackStackEntry?.destination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(text = stringResource(item.labelResId))
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldGreen,
                                selectedTextColor = EmeraldGreen,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavGraph(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
