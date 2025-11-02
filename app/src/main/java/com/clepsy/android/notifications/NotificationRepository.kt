package com.clepsy.android.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Stores notification-derived context for quick lookup by package name.
 */
class NotificationRepository {
    private val _snapshots = MutableStateFlow<Map<String, NotificationSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, NotificationSnapshot>> = _snapshots

    fun update(snapshot: NotificationSnapshot) {
        _snapshots.update { current -> current + (snapshot.packageName to snapshot) }
    }

    fun updateAll(items: Collection<NotificationSnapshot>) {
        _snapshots.value = items.associateBy { it.packageName }
    }

    fun remove(packageName: String) {
        _snapshots.update { current -> current - packageName }
    }

    fun snapshotFor(packageName: String): NotificationSnapshot? = _snapshots.value[packageName]
}

/**
 * Simplified view of the latest posted notification for a package.
 */
data class NotificationSnapshot(
    val packageName: String,
    val notificationText: String?,
    val mediaMetadata: Map<String, String>?
)
