package com.clepsy.android.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clepsy.android.service.ClepsyMonitoringService
import com.clepsy.android.ui.MainViewModel
import com.clepsy.android.ui.PermissionsState
import com.clepsy.android.ui.screens.DashboardScreen
import com.clepsy.android.ui.screens.PermissionsScreen
import com.clepsy.android.ui.screens.SetupScreen
import com.clepsy.android.ui.screens.SettingsScreen
import java.net.URI

@Composable
fun ClepsyAppNavHost(
    viewModel: MainViewModel,
    permissions: PermissionsState,
    onRequestNotificationAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.userConfig.isPaired) {
        if (uiState.userConfig.isPaired) {
            ClepsyMonitoringService.start(context)
        } else {
            ClepsyMonitoringService.stop(context)
        }
    }

    LaunchedEffect(permissions, uiState.userConfig.isPaired) {
        val targetRoute = when {
            !permissions.allGranted -> NavDestination.Permissions.route
            !uiState.userConfig.isPaired -> NavDestination.Setup.route
            else -> NavDestination.Settings.route
        }
        val currentRoute = navController.currentDestination?.route
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavDestination.Permissions.route,
        modifier = modifier
    ) {
        composable(NavDestination.Permissions.route) {
            PermissionsScreen(
                usageAccessGranted = permissions.usageAccessGranted,
                notificationGranted = permissions.notificationGranted,
                notificationPermissionRequired = permissions.notificationPermissionRequired,
                notificationAccessGranted = permissions.notificationAccessGranted,
                onRequestUsageAccess = onRequestUsageAccess,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestNotificationAccess = onRequestNotificationAccess
            )
        }
        composable(NavDestination.Setup.route) {
            if (uiState.userConfig.isPaired) {
                LaunchedEffect(uiState.userConfig.isPaired) {
                    navController.navigate(NavDestination.Settings.route) {
                        popUpTo(NavDestination.Setup.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else {
                SetupScreen(
                    state = uiState,
                    onBackendUrlChange = viewModel::updateBackendUrl,
                    onDeviceNameChange = viewModel::updateDeviceName,
                    onPair = viewModel::pair,
                    onClearPairingMessage = viewModel::clearPairingMessage
                )
            }
        }
        composable(NavDestination.Dashboard.route) {
            DashboardScreen(
                backendUrl = uiState.userConfig.clepsyBackendUrl,
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavDestination.Settings.route) {
            SettingsScreen(
                state = uiState,
                onBackendUrlChange = viewModel::updateBackendUrl,
                onDeviceNameChange = viewModel::updateDeviceName,
                onToggleMonitoring = viewModel::setMonitoringActive,
                onUnpair = viewModel::unpair,
                onOpenDeploymentInBrowser = {
                    buildDashboardUrl(uiState.userConfig.clepsyBackendUrl)?.let { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }
            )
        }
    }
}

private fun buildDashboardUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return null
    return try {
        val candidate = URI(trimmed)
        val base = if (candidate.scheme == null) URI("https://$trimmed") else candidate
        val normalized = base.toString().let { if (it.endsWith('/')) it else "$it/" }
        "$normalized" + "s/"
    } catch (_: Throwable) {
        null
    }
}
