package com.clepsy.android.models

/**
 * Persists user-provided configuration following the desktop client's schema.
 */
data class UserConfig(
    val clepsyBackendUrl: String = "",
    val deviceToken: String = "",
    val sourceName: String = DEFAULT_DEVICE_NAME,
    val sourceId: Int? = null,
    val active: Boolean = true,
    val appBlacklist: Set<String> = emptySet()
) {
    val isPaired: Boolean get() = deviceToken.isNotBlank() && sourceId != null

    companion object {
        const val DEFAULT_DEVICE_NAME = "mobile_phone"
    }
}
