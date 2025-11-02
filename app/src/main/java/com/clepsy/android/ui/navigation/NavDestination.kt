package com.clepsy.android.ui.navigation

sealed class NavDestination(val route: String) {
    data object Permissions : NavDestination("permissions")
    data object Setup : NavDestination("setup")
    data object Dashboard : NavDestination("dashboard")
    data object Settings : NavDestination("settings")
}
