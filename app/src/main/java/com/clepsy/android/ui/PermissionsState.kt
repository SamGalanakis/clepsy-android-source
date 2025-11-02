package com.clepsy.android.ui

data class PermissionsState(
    val usageAccessGranted: Boolean,
    val notificationGranted: Boolean,
    val notificationPermissionRequired: Boolean,
    val notificationAccessGranted: Boolean
) {
    val allGranted: Boolean
        get() = usageAccessGranted && notificationAccessGranted && (
            !notificationPermissionRequired || notificationGranted
        )
}
