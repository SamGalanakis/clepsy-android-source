package com.clepsy.android.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.clepsy.android.models.UserConfig
import com.clepsy.android.models.UserConfig.Companion.DEFAULT_DEVICE_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private const val PREFS_FILE = "clepsy_user_config"
private const val KEY_BACKEND_URL = "backend_url"
private const val KEY_DEVICE_TOKEN = "device_token"
private const val KEY_SOURCE_NAME = "source_name"
private const val KEY_SOURCE_ID = "source_id"
private const val KEY_ACTIVE = "active"
private const val KEY_APP_BLACKLIST = "app_blacklist"

/**
 * Handles persistence of [UserConfig] using EncryptedSharedPreferences.
 */
class UserConfigStorage(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences = createEncryptedPreferences(appContext)
    private val state = MutableStateFlow(readFromPreferences())
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        state.value = readFromPreferences()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun config(): StateFlow<UserConfig> = state

    fun dispose() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    suspend fun update(block: (UserConfig) -> UserConfig) {
        withContext(Dispatchers.IO) {
            val next = block(state.value)
            writeToPreferences(next)
            state.value = next
        }
    }

    suspend fun overwrite(config: UserConfig) {
        withContext(Dispatchers.IO) {
            writeToPreferences(config)
            state.value = config
        }
    }

    private fun readFromPreferences(): UserConfig = UserConfig(
        clepsyBackendUrl = preferences.getString(KEY_BACKEND_URL, "") ?: "",
        deviceToken = preferences.getString(KEY_DEVICE_TOKEN, "") ?: "",
    sourceName = preferences.getString(KEY_SOURCE_NAME, DEFAULT_DEVICE_NAME) ?: DEFAULT_DEVICE_NAME,
        sourceId = preferences.getInt(KEY_SOURCE_ID, -1).takeIf { it >= 0 },
        active = preferences.getBoolean(KEY_ACTIVE, true),
        appBlacklist = preferences.getStringSet(KEY_APP_BLACKLIST, emptySet())?.toSet() ?: emptySet()
    )

    private fun writeToPreferences(config: UserConfig) {
        preferences.edit().apply {
            putString(KEY_BACKEND_URL, config.clepsyBackendUrl)
            putString(KEY_DEVICE_TOKEN, config.deviceToken)
            putString(KEY_SOURCE_NAME, config.sourceName)
            if (config.sourceId != null) {
                putInt(KEY_SOURCE_ID, config.sourceId)
            } else {
                remove(KEY_SOURCE_ID)
            }
            putBoolean(KEY_ACTIVE, config.active)
            putStringSet(KEY_APP_BLACKLIST, config.appBlacklist.toMutableSet())
        }.apply()
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
