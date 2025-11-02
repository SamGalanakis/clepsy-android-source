package com.clepsy.android.notifications

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.MediaMetadata
import com.clepsy.android.ClepsyApp

/**
 * Collects notification content and media metadata for the monitoring service.
 */
class ClepsyNotificationListenerService : NotificationListenerService() {

    private val repository: NotificationRepository
        get() = (application as ClepsyApp).graph.notificationRepository

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshPackage(sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshPackage(sbn.packageName)
    }

    private fun refreshPackage(packageName: String) {
        val snapshot = activeNotifications
            ?.filter { it.packageName == packageName }
            ?.maxByOrNull { it.postTime }
            ?.let(::toSnapshot)

        if (snapshot != null) {
            repository.update(snapshot)
        } else {
            repository.remove(packageName)
        }
    }

    private fun refreshActiveNotifications() {
        val snapshots = activeNotifications
            ?.groupBy { it.packageName }
            ?.mapNotNull { (_, notifications) ->
                notifications.maxByOrNull { it.postTime }?.let(::toSnapshot)
            }
            .orEmpty()

        repository.updateAll(snapshots)
    }

    private fun toSnapshot(sbn: StatusBarNotification): NotificationSnapshot? {
        val notification = sbn.notification ?: return null
        val text = extractNotificationText(notification)
        val metadata = extractMediaMetadata(notification)
        return NotificationSnapshot(
            packageName = sbn.packageName,
            notificationText = text,
            mediaMetadata = metadata
        )
    }

    private fun extractNotificationText(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val parts = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        return if (parts.isEmpty()) null else parts.joinToString(separator = " • ")
    }

    private fun extractMediaMetadata(notification: Notification): Map<String, String>? {
        val token = notification.mediaSessionToken() ?: return null
        val controller = try {
            MediaController(this, token)
        } catch (_: Throwable) {
            return null
        }
        val metadata = controller.metadata ?: return null
        return metadata.toMap()
    }

    @Suppress("DEPRECATION")
    private fun Notification.mediaSessionToken(): MediaSession.Token? {
        val extras = extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }

    private fun MediaMetadata.toMap(): Map<String, String>? {
        val result = mutableMapOf<String, String>()
        fun put(key: String, value: String?) {
            if (!value.isNullOrBlank()) {
                result[key] = value
            }
        }

        put("title", getString(MediaMetadata.METADATA_KEY_TITLE))
        put("artist", getString(MediaMetadata.METADATA_KEY_ARTIST))
        put("album", getString(MediaMetadata.METADATA_KEY_ALBUM))
        put("album_artist", getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))
        put("display_title", getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
        put("writer", getString(MediaMetadata.METADATA_KEY_WRITER))
        put("author", getString(MediaMetadata.METADATA_KEY_AUTHOR))

        val duration = getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (duration > 0) {
            result["duration_ms"] = duration.toString()
        }

        return result.takeIf { it.isNotEmpty() }
    }
}
