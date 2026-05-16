package com.pledgerio.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.ui.components.OfflineBanner
import com.pledgerio.app.ui.navigation.NavGraph
import com.pledgerio.app.ui.navigation.Screen
import com.pledgerio.app.ui.theme.EmeraldGreen
import com.pledgerio.app.ui.theme.PledgerTheme
import com.pledgerio.app.ui.theme.TextSecondary
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
fun PledgerRoot(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val sessionManager = appViewModel.sessionManager
    val themeMode by appViewModel.themeMode.collectAsState()
    val isOnline by appViewModel.isOnline.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val startDestination = remember(sessionManager.getBaseUrl(), sessionManager.isLoggedIn()) {
        when {
            sessionManager.getBaseUrl() == null -> Screen.ServerSetup.route
            !sessionManager.isLoggedIn() -> Screen.Login.route
            else -> Screen.Dashboard.route
        }
    }

    val showBottomBar = currentRoute in mainScreens

    PledgerTheme(darkTheme = darkTheme) {
        PledgerAppContent(
            navController = navController,
            startDestination = startDestination,
            showBottomBar = showBottomBar,
            navBackStackEntry = navBackStackEntry,
            isOnline = isOnline,
        )
    }
}

@Composable
private fun PledgerAppContent(
    navController: androidx.navigation.NavHostController,
    startDestination: String,
    showBottomBar: Boolean,
    navBackStackEntry: androidx.navigation.NavBackStackEntry?,
    isOnline: Boolean,
) {
    Scaffold(
        bottomBar = {
            Column {
                OfflineBanner(isOnline = isOnline)
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

                            val label = stringResource(item.labelResId)
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
                                alwaysShowLabel = false,
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldGreen,
                                    selectedTextColor = EmeraldGreen,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        NavGraph(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
