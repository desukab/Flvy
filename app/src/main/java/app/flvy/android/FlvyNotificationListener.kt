package app.flvy.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class FlvyNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        getSharedPreferences("flvy", MODE_PRIVATE)
            .edit()
            .putString("last_notification_title", title)
            .putString("last_notification_body", body)
            .apply()

        // Notification shade updates can repost the same entry repeatedly. Keep
        // the board lively without turning one message into a machine-gun of sounds.
        val now = System.currentTimeMillis()
        val soundPrefs = getSharedPreferences("flvy", MODE_PRIVATE)
        val lastKey = soundPrefs.getString("last_notification_sound_key", null)
        val lastAt = soundPrefs.getLong("last_notification_sound_at", 0L)
        val shouldSound = sbn.key != lastKey || now - lastAt > 2500L
        if (shouldSound) {
            soundPrefs.edit()
                .putString("last_notification_sound_key", sbn.key)
                .putLong("last_notification_sound_at", now)
                .apply()
            FlvyNotificationSound.play(this)
        }

        FlvyWebBridge.pushNotification(title, body)
    }
}
