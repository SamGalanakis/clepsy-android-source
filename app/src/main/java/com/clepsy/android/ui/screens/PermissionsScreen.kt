package com.clepsy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(
    usageAccessGranted: Boolean,
    notificationGranted: Boolean,
    notificationPermissionRequired: Boolean,
    notificationAccessGranted: Boolean,
    onRequestUsageAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Allow required permissions",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Clepsy needs these permissions before monitoring can begin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionCard(
            title = "Usage access",
            description = "Allow Clepsy to detect which app is in the foreground to decide when to capture.",
            granted = usageAccessGranted,
            actionLabel = "Open settings",
            onAction = onRequestUsageAccess
        )

        if (notificationPermissionRequired) {
            PermissionCard(
                title = "Notifications",
                description = "Enable service status notifications required for foreground monitoring.",
                granted = notificationGranted,
                actionLabel = "Allow",
                onAction = onRequestNotificationPermission
            )
        }

        PermissionCard(
            title = "Notification access",
            description = "Allow Clepsy to read notification content for usage context and media metadata.",
            granted = notificationAccessGranted,
            actionLabel = "Open settings",
            onAction = onRequestNotificationAccess
        )

        Spacer(Modifier.height(8.dp))

        val allGranted = usageAccessGranted && notificationAccessGranted && (
            !notificationPermissionRequired || notificationGranted
        )
        if (allGranted) {
            Text(
                text = "All required permissions granted. You can continue to pairing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (granted) "Granted" else "Required",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
