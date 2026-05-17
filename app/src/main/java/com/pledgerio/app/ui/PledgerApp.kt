package com.pledgerio.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.ui.components.BiometricLockScreen
import com.pledgerio.app.ui.components.OfflineBanner
import com.pledgerio.app.ui.navigation.DeepLink
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
    BottomNavItem("transactions", Icons.Default.Receipt, R.string.tab_transactions),
    BottomNavItem("budgets", Icons.Default.PieChart, R.string.tab_budgets),
    BottomNavItem(Screen.Accounts.route, Icons.Default.AccountBalance, R.string.tab_accounts),
    BottomNavItem(Screen.Reports.route, Icons.Default.BarChart, R.string.tab_reports),
)

private val mainScreenRoutes = setOf(
    Screen.Dashboard.route,
    "transactions",
    "budgets",
    Screen.Accounts.route,
    Screen.Reports.route,
)

private val onboardingRoutes = setOf(
    "server_setup",
    Screen.Login.route,
)

private fun String?.baseRoute(): String? = this?.substringBefore('?')

@Composable
fun PledgerRoot(
    deepLink: DeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val sessionManager = appViewModel.sessionManager
    val biometricLockManager = appViewModel.biometricLockManager
    val biometricAuthenticator = appViewModel.biometricAuthenticator
    val themeMode by appViewModel.themeMode.collectAsState()
    val isOnline by appViewModel.isOnline.collectAsState()
    val requiresBiometricUnlock by biometricLockManager.requiresUnlock.collectAsState()
    val activity = LocalActivity.current as? androidx.appcompat.app.AppCompatActivity
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
            sessionManager.getBaseUrl() == null -> Screen.ServerSetup.createRoute(changeServer = false)
            !sessionManager.isLoggedIn() -> Screen.Login.route
            else -> Screen.Dashboard.route
        }
    }

    val baseRoute = currentRoute.baseRoute()
    val showBottomBar = baseRoute in mainScreenRoutes
    val showOfflineBanner = baseRoute != null && baseRoute !in onboardingRoutes

    LaunchedEffect(deepLink, sessionManager.isLoggedIn()) {
        if (deepLink == null || !sessionManager.isLoggedIn()) return@LaunchedEffect
        when (deepLink) {
            is DeepLink.Transaction -> {
                navController.navigate(Screen.TransactionDetail.createRoute(deepLink.id))
            }
            is DeepLink.Account -> {
                navController.navigate(Screen.AccountDetail.createRoute(deepLink.id))
            }
            is DeepLink.Budgets -> {
                val route = deepLink.yearMonth?.let { ym ->
                    Screen.Budgets.createRoute(ym.year, ym.monthValue)
                } ?: Screen.Budgets.createRoute()
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
        }
        onDeepLinkHandled()
    }

    val showBiometricLock = requiresBiometricUnlock &&
        sessionManager.isBiometricEnabled() &&
        sessionManager.isLoggedIn()

    PledgerTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            PledgerAppContent(
                navController = navController,
                startDestination = startDestination,
                showBottomBar = showBottomBar,
                showOfflineBanner = showOfflineBanner,
                navBackStackEntry = navBackStackEntry,
                isOnline = isOnline,
            )
            if (showBiometricLock && activity != null) {
                BiometricLockScreen(
                    activity = activity,
                    biometricAuthenticator = biometricAuthenticator,
                    onUnlocked = biometricLockManager::onUnlocked,
                    onSignOut = {
                        appViewModel.signOutFromBiometricLock {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PledgerAppContent(
    navController: androidx.navigation.NavHostController,
    startDestination: String,
    showBottomBar: Boolean,
    showOfflineBanner: Boolean,
    navBackStackEntry: androidx.navigation.NavBackStackEntry?,
    isOnline: Boolean,
) {
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
                                it.route?.baseRoute() == item.route.baseRoute()
                            } == true

                            val label = stringResource(item.labelResId)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    val destination = when (item.route.baseRoute()) {
                                        "transactions" -> Screen.Transactions.createRoute()
                                        "budgets" -> Screen.Budgets.createRoute()
                                        else -> item.route
                                    }
                                    navController.navigate(destination) {
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
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            if (showOfflineBanner) {
                OfflineBanner(isOnline = isOnline)
            }
            NavGraph(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
