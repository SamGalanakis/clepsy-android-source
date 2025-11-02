package com.clepsy.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clepsy.android.R
import com.clepsy.android.monitoring.CaptureScheduler
import com.clepsy.android.monitoring.MonitoringState
import com.clepsy.android.ui.MainViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.ZERO

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainViewModel.MainUiState,
    onBackendUrlChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onUnpair: () -> Unit,
    onOpenDeploymentInBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    var backendUrl by rememberSaveable { mutableStateOf(state.userConfig.clepsyBackendUrl) }
    var deviceName by rememberSaveable { mutableStateOf(state.userConfig.sourceName) }
    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.Settings) }

    LaunchedEffect(state.userConfig.clepsyBackendUrl) {
        backendUrl = state.userConfig.clepsyBackendUrl
    }
    LaunchedEffect(state.userConfig.sourceName) {
        deviceName = state.userConfig.sourceName
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        HeaderContent(
            deploymentUrl = state.userConfig.clepsyBackendUrl,
            onClick = onOpenDeploymentInBrowser
        )

        Spacer(Modifier.height(16.dp))

        SecondaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            SettingsTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) }
                )
            }
        }

        when (selectedTab) {
            SettingsTab.Settings -> SettingsTabContent(
                backendUrl = backendUrl,
                onBackendUrlValueChange = { backendUrl = it },
                deviceName = deviceName,
                onDeviceNameValueChange = { deviceName = it },
                onSave = {
                    onBackendUrlChange(backendUrl.trim())
                    onDeviceNameChange(deviceName.trim())
                },
                active = state.userConfig.active,
                onToggleMonitoring = onToggleMonitoring,
                isPaired = state.userConfig.isPaired,
                onUnpair = onUnpair
            )
            SettingsTab.Monitoring -> MonitoringTabContent(
                monitoringState = state.monitoringState,
                isActive = state.userConfig.active,
                hasDeploymentUrl = state.userConfig.clepsyBackendUrl.isNotBlank()
            )
        }
    }
}

@Composable
private fun HeaderContent(
    deploymentUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUrl = deploymentUrl.isNotBlank()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasUrl, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.clepsy_launcher),
                contentDescription = "Clepsy logo",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Clepsy", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (hasUrl) deploymentUrl else "Set your deployment URL to open the dashboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open in browser",
                tint = if (hasUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsTabContent(
    backendUrl: String,
    onBackendUrlValueChange: (String) -> Unit,
    deviceName: String,
    onDeviceNameValueChange: (String) -> Unit,
    onSave: () -> Unit,
    active: Boolean,
    onToggleMonitoring: (Boolean) -> Unit,
    isPaired: Boolean,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showUnpairDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        MonitoringStatusCard(active = active, onToggleMonitoring = onToggleMonitoring)

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = onBackendUrlValueChange,
                label = { Text("Clepsy deployment URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = deviceName,
                onValueChange = onDeviceNameValueChange,
                label = { Text("Device name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }

        if (isPaired) {
            OutlinedButton(
                onClick = { showUnpairDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unpair device")
            }
        }
    }

    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text("Unpair device?") },
            text = { Text("This will stop monitoring and return you to the pairing screen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnpairDialog = false
                        onUnpair()
                    }
                ) {
                    Text("Unpair")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MonitoringStatusCard(
    active: Boolean,
    onToggleMonitoring: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Active", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (active) "Monitoring is on" else "Monitoring is paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = onToggleMonitoring,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                text = "Clepsy will send usage data only when monitoring is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonitoringTabContent(
    monitoringState: MonitoringState,
    isActive: Boolean,
    hasDeploymentUrl: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val now = Clock.System.now()

    val (heartbeatText, heartbeatStatus) = monitoringState.lastHeartbeat
        ?.let { instant ->
            "${formatRelativeTime(now, instant)} (Success)" to MonitoringStatus.Success
        }
        ?: ("No heartbeat sent yet" to MonitoringStatus.Pending)

    val (eventText, eventStatus) = monitoringState.lastEvent
        ?.let { instant ->
            buildString {
                append(formatRelativeTime(now, instant))
                append(" (Success)")
                monitoringState.lastEventReason?.let { reason ->
                    append(" • ")
                    append(formatReason(reason))
                }
            } to MonitoringStatus.Success
        }
        ?: ("No usage events sent yet" to MonitoringStatus.Pending)

    val serviceStatus = when {
        monitoringState.lastError != null -> MonitoringStatus.Error
        monitoringState.serviceRunning && isActive -> MonitoringStatus.Success
        else -> MonitoringStatus.Pending
    }

    val serviceText = when {
        monitoringState.lastError != null -> "Attention required"
        !isActive -> "Paused in settings"
        monitoringState.serviceRunning -> "Running"
        else -> "Starting…"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        MonitoringMetric(
            title = "Service status",
            value = serviceText,
            status = serviceStatus
        )

        MonitoringMetric(
            title = "Last heartbeat",
            value = heartbeatText,
            status = heartbeatStatus
        )

        MonitoringMetric(
            title = "Last data sent",
            value = eventText,
            status = eventStatus
        )

        monitoringState.lastError?.let { error ->
            MonitoringMetric(
                title = "Last error",
                value = error,
                status = MonitoringStatus.Error
            )
        }

        if (!hasDeploymentUrl) {
            Text(
                text = "Add a Clepsy deployment URL in Settings to open the /s/ dashboard in your browser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonitoringMetric(
    title: String,
    value: String,
    status: MonitoringStatus
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            val color = when (status) {
                MonitoringStatus.Success -> MaterialTheme.colorScheme.tertiary
                MonitoringStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                MonitoringStatus.Error -> MaterialTheme.colorScheme.error
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

private enum class SettingsTab(val title: String) {
    Settings("Settings"),
    Monitoring("Monitoring")
}

private enum class MonitoringStatus {
    Success,
    Pending,
    Error
}

private fun formatRelativeTime(now: Instant, instant: Instant): String {
    val duration = now - instant
    val safeDuration = if (duration.isNegative()) ZERO else duration
    val seconds = safeDuration.inWholeSeconds
    return when {
        seconds <= 1 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

private fun formatReason(reason: CaptureScheduler.Reason): String = when (reason) {
    CaptureScheduler.Reason.INITIAL -> "First event"
    CaptureScheduler.Reason.APP_SWITCH -> "App switch"
    CaptureScheduler.Reason.SAME_APP -> "Same app interval"
    CaptureScheduler.Reason.HEARTBEAT -> "Heartbeat interval"
}
