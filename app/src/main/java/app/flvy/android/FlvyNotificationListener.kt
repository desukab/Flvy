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

        FlvyWebBridge.pushNotification(title, body)
    }
}
