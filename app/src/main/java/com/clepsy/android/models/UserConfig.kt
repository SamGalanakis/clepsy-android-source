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

    /**
     * Check if an app should be filtered out based on static regex patterns.
     * Returns true if the event should be dropped.
     */
    fun shouldFilterApp(packageName: String, appLabel: String): Boolean {
        return BLACKLIST_REGEX_PATTERNS.any { pattern ->
            try {
                val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
                regex.containsMatchIn(packageName) || regex.containsMatchIn(appLabel)
            } catch (e: Exception) {
                // Invalid regex pattern, skip it
                false
            }
        }
    }

    companion object {
        const val DEFAULT_DEVICE_NAME = "mobile_phone"
        
        /**
         * Static regex patterns for filtering out system/launcher apps.
         * These patterns are matched against both packageName and appLabel (case-insensitive).
         * 
         * Add patterns here to filter out apps from being sent to the backend.
         * Examples:
         * - "nexuslauncher" - filters Google Pixel launcher
         * - "^com\\.google\\.android\\.apps\\.nexuslauncher$" - exact package match
         * - "launcher|systemui" - multiple patterns with OR
         */
        private val BLACKLIST_REGEX_PATTERNS = setOf(
            "nexuslauncher",           // Google Pixel Launcher
            "systemui",                // Android System UI
            "^com\\.android\\.launcher", // Generic Android launchers
        )
    }
}
