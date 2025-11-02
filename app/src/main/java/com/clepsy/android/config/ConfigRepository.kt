package com.clepsy.android.config

import android.os.Build
import com.clepsy.android.models.UserConfig
import com.clepsy.android.models.UserConfig.Companion.DEFAULT_DEVICE_NAME
import com.clepsy.android.network.ApiResult
import com.clepsy.android.network.ClepsyApi
import com.clepsy.android.network.HttpClepsyApi
import com.clepsy.android.network.PairingRequest
import com.clepsy.android.storage.UserConfigStorage
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/**
 * Acts as the single source of truth for pairing state and backend configuration.
 */
class ConfigRepository(
    private val storage: UserConfigStorage,
    private val httpClient: OkHttpClient,
    private val apiFactory: (String, OkHttpClient) -> ClepsyApi = { baseUrl, client ->
        HttpClepsyApi(baseUrl, client)
    }
) {

    val config: StateFlow<UserConfig> = storage.config()

    suspend fun updateBackendUrl(url: String) {
        storage.update { it.copy(clepsyBackendUrl = url.trim()) }
    }

    suspend fun updateDeviceName(name: String) {
        val cleaned = name.trim().ifBlank { UserConfig.DEFAULT_DEVICE_NAME }
        storage.update { it.copy(sourceName = cleaned) }
    }

    suspend fun toggleActive(active: Boolean) {
        storage.update { it.copy(active = active) }
    }

    suspend fun updateBlacklist(packages: Set<String>) {
        storage.update { it.copy(appBlacklist = packages) }
    }

    suspend fun pair(code: String): ApiResult<UserConfig> {
        val current = config.value
        val backend = current.clepsyBackendUrl.takeIf { it.isNotBlank() }
            ?: return ApiResult.Failure(null, "Backend URL must be set before pairing")

    val deviceName = current.sourceName.ifBlank { buildDefaultDeviceName() }
        val api = apiFactory(backend.trimEnd('/'), httpClient)

        return when (val result = api.pair(PairingRequest(code = code.trim(), deviceName = deviceName))) {
            is ApiResult.Success -> {
                val updated = current.copy(
                    deviceToken = result.data.deviceToken,
                    sourceId = result.data.sourceId,
                    sourceName = deviceName
                )
                storage.overwrite(updated)
                ApiResult.Success(updated)
            }
            is ApiResult.Failure -> result
        }
    }

    suspend fun unpair() {
        storage.update {
            it.copy(
                deviceToken = "",
                sourceId = null,
                active = false
            )
        }
    }

    private fun buildDefaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val composed = buildString {
            if (manufacturer.isNotEmpty()) append(manufacturer.replaceFirstChar { it.uppercase() })
            if (model.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append(model)
            }
        }
        return composed.ifBlank { DEFAULT_DEVICE_NAME }
    }
}
