package com.clepsy.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.clepsy.android.ui.MainViewModel
import com.clepsy.android.ui.PermissionsState
import com.clepsy.android.ui.navigation.ClepsyAppNavHost
import com.clepsy.android.ui.theme.ClepsyTheme
import com.clepsy.android.util.NotificationAccessUtils
import com.clepsy.android.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val appGraph by lazy { (application as ClepsyApp).graph }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(appGraph.configRepository, appGraph.monitoringState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            var usageAccessGranted by remember {
                mutableStateOf(PermissionUtils.hasUsageStatsPermission(context))
            }

            val notificationPermissionRequired = remember {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            }

            var notificationsGranted by remember {
                mutableStateOf(
                    if (notificationPermissionRequired) {
                        PermissionUtils.hasNotificationPermission(context)
                    } else {
                        true
                    }
                )
            }

            var notificationAccessGranted by remember {
                mutableStateOf(NotificationAccessUtils.hasNotificationAccess(context))
            }

            val usageAccessLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                usageAccessGranted = PermissionUtils.hasUsageStatsPermission(context)
            }

            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                notificationsGranted = granted
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        usageAccessGranted = PermissionUtils.hasUsageStatsPermission(context)
                        notificationsGranted = if (notificationPermissionRequired) {
                            PermissionUtils.hasNotificationPermission(context)
                        } else {
                            true
                        }
                        notificationAccessGranted = NotificationAccessUtils.hasNotificationAccess(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val requestUsageAccess = remember(usageAccessLauncher) {
                {
                    usageAccessLauncher.launch(PermissionUtils.usageAccessSettingsIntent(context))
                }
            }

            val requestNotifications = remember(notificationLauncher, notificationPermissionRequired, notificationsGranted) {
                {
                    if (!notificationPermissionRequired || notificationsGranted) return@remember
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val requestNotificationAccess = remember(notificationAccessGranted) {
                {
                    context.startActivity(NotificationAccessUtils.openSettingsIntent(context))
                }
            }

            val permissionsSummary = remember(
                usageAccessGranted,
                notificationsGranted,
                notificationPermissionRequired,
                notificationAccessGranted
            ) {
                PermissionsState(
                    usageAccessGranted = usageAccessGranted,
                    notificationGranted = notificationsGranted,
                    notificationPermissionRequired = notificationPermissionRequired,
                    notificationAccessGranted = notificationAccessGranted
                )
            }

            ClepsyTheme {
                ClepsyAppNavHost(
                    viewModel = mainViewModel,
                    permissions = permissionsSummary,
                    onRequestNotificationAccess = requestNotificationAccess,
                    onRequestUsageAccess = requestUsageAccess,
                    onRequestNotificationPermission = requestNotifications
                )
            }
        }
    }
}
