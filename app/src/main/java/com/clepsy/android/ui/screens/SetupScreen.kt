package com.clepsy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clepsy.android.ui.MainViewModel

@Composable
fun SetupScreen(
    state: MainViewModel.MainUiState,
    onBackendUrlChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onPair: (String) -> Unit,
    onClearPairingMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var backendUrl by rememberSaveable { mutableStateOf(state.userConfig.clepsyBackendUrl) }
    var deviceName by rememberSaveable { mutableStateOf(state.userConfig.sourceName) }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var hasPaired by remember { mutableStateOf(state.userConfig.isPaired) }

    // Sync local state with config changes
    LaunchedEffect(state.userConfig.clepsyBackendUrl) {
        backendUrl = state.userConfig.clepsyBackendUrl
    }
    LaunchedEffect(state.userConfig.sourceName) {
        deviceName = state.userConfig.sourceName
    }
    LaunchedEffect(state.userConfig.isPaired) {
        hasPaired = state.userConfig.isPaired
        if (!state.userConfig.isPaired) {
            pairingCode = ""
        }
    }

    // Auto-clear pairing code and message on success
    LaunchedEffect(state.pairingState) {
        if (state.pairingState is MainViewModel.PairingState.Success) {
            pairingCode = ""
            // Auto-dismiss success message after a brief delay
            kotlinx.coroutines.delay(1500)
            onClearPairingMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (hasPaired) "Clepsy is ready" else "Pair your device",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Configure the Clepsy backend and pair this phone to start monitoring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { 
                        backendUrl = it
                        onBackendUrlChange(it)
                    },
                    label = { Text("Backend URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Uri
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { 
                        deviceName = it
                        onDeviceNameChange(it)
                    },
                    label = { Text("Device name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                PairingMessage(state = state.pairingState, onDismiss = onClearPairingMessage)

                Button(
                    onClick = {
                        onPair(pairingCode.trim())
                    },
                    enabled = pairingCode.isNotBlank() && 
                             backendUrl.isNotBlank() && 
                             state.pairingState !is MainViewModel.PairingState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = when (state.pairingState) {
                        is MainViewModel.PairingState.Loading -> "Pairing…"
                        else -> if (hasPaired) "Re-pair device" else "Pair"
                    })
                }
            }
        }

        if (hasPaired) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Pairing complete. You can return to the dashboard from the menu.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PairingMessage(
    state: MainViewModel.PairingState,
    onDismiss: () -> Unit
) {
    when (state) {
        MainViewModel.PairingState.Idle -> Unit
        MainViewModel.PairingState.Loading -> Text("Pairing…", color = MaterialTheme.colorScheme.primary)
        is MainViewModel.PairingState.Success -> {
            Text(text = state.message, color = MaterialTheme.colorScheme.tertiary)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
        is MainViewModel.PairingState.Error -> {
            var showFullError by remember { mutableStateOf(false) }
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
                
                if (state.fullError != null) {
                    TextButton(
                        onClick = { showFullError = !showFullError }
                    ) {
                        Text(if (showFullError) "Hide details" else "Show details")
                    }
                    
                    if (showFullError) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = state.fullError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
