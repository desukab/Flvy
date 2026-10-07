package app.flvy.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class FlvyNotificationListener: NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val e=sbn.notification.extras
        val title=e.getCharSequence("android.title")?.toString().orEmpty()
        val body=e.getCharSequence("android.text")?.toString().orEmpty()
        if(title.isNotBlank() || body.isNotBlank()) MainActivityHolder.board?.showNotification(title,body)
    }
}
