package com.clepsy.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clepsy.android.config.ConfigRepository
import com.clepsy.android.models.UserConfig
import com.clepsy.android.monitoring.MonitoringState
import com.clepsy.android.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Aggregates UI state for onboarding, monitoring, and settings flows.
 */
class MainViewModel(
    private val configRepository: ConfigRepository,
    monitoringState: StateFlow<MonitoringState>
) : ViewModel() {

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    private val config: StateFlow<UserConfig> = configRepository.config

    val uiState: StateFlow<MainUiState> = combine(
        config,
        monitoringState,
        _pairingState
    ) { userConfig, monitoring, pairing ->
        MainUiState(
            userConfig = userConfig,
            monitoringState = monitoring,
            pairingState = pairing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = MainUiState()
    )

    fun updateBackendUrl(url: String) {
        viewModelScope.launch {
            configRepository.updateBackendUrl(url)
        }
    }

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            configRepository.updateDeviceName(name)
        }
    }

    fun pair(pairingCode: String) {
        val trimmedCode = pairingCode.trim()
        if (trimmedCode.isBlank()) {
            _pairingState.value = PairingState.Error("Pairing code cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                _pairingState.value = PairingState.Loading
                when (val result = configRepository.pair(trimmedCode)) {
                    is ApiResult.Success -> {
                        _pairingState.value = PairingState.Success("Device paired successfully")
                    }
                    is ApiResult.Failure -> {
                        val userMessage = buildString {
                            if (result.statusCode != null) {
                                append("HTTP ${result.statusCode}: ")
                            }
                            append(
                                when {
                                    result.message?.isNotBlank() == true -> {
                                        // Try to extract a user-friendly message from JSON error
                                        extractErrorMessage(result.message)
                                    }
                                    result.statusCode != null -> "Server returned error code ${result.statusCode}"
                                    else -> "Pairing failed. Please check your code and backend URL."
                                }
                            )
                        }
                        _pairingState.value = PairingState.Error(
                            message = userMessage,
                            statusCode = result.statusCode,
                            fullError = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(
                    message = "Pairing failed: ${e.message ?: "Unknown error"}",
                    fullError = e.stackTraceToString()
                )
            }
        }
    }

    private fun extractErrorMessage(rawError: String): String {
        // Try to extract a friendly message from JSON error responses
        return try {
            // Check for common JSON error patterns
            when {
                rawError.contains("\"detail\"", ignoreCase = true) -> {
                    // FastAPI style: {"detail": "message"}
                    val regex = """"detail"\s*:\s*"([^"]+)"""".toRegex()
                    regex.find(rawError)?.groupValues?.get(1) ?: rawError
                }
                rawError.contains("\"error\"", ignoreCase = true) -> {
                    // Generic: {"error": "message"}
                    val regex = """"error"\s*:\s*"([^"]+)"""".toRegex()
                    regex.find(rawError)?.groupValues?.get(1) ?: rawError
                }
                rawError.contains("\"message\"", ignoreCase = true) -> {
                    // Generic: {"message": "message"}
                    val regex = """"message"\s*:\s*"([^"]+)"""".toRegex()
                    regex.find(rawError)?.groupValues?.get(1) ?: rawError
                }
                else -> rawError
            }
        } catch (e: Exception) {
            rawError
        }
    }

    fun clearPairingMessage() {
        _pairingState.value = PairingState.Idle
    }

    fun setMonitoringActive(active: Boolean) {
        viewModelScope.launch {
            configRepository.toggleActive(active)
        }
    }

    fun unpair() {
        viewModelScope.launch {
            configRepository.unpair()
            _pairingState.value = PairingState.Idle
        }
    }

    data class MainUiState(
        val userConfig: UserConfig = UserConfig(),
        val monitoringState: MonitoringState = MonitoringState(),
        val pairingState: PairingState = PairingState.Idle
    )

    sealed interface PairingState {
        data object Idle : PairingState
        data object Loading : PairingState
        data class Success(val message: String) : PairingState
        data class Error(
            val message: String,
            val statusCode: Int? = null,
            val fullError: String? = null
        ) : PairingState
    }

    class Factory(
        private val configRepository: ConfigRepository,
        private val monitoringState: StateFlow<MonitoringState>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(configRepository, monitoringState) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}
