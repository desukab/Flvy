package app.flvy.android

import android.webkit.WebView
import java.lang.ref.WeakReference

object FlvyWebBridge {
    private var ref: WeakReference<WebView>? = null

    fun attach(webView: WebView) { ref = WeakReference(webView) }

    fun detach(webView: WebView) {
        if (ref?.get() === webView) ref = null
    }

    fun pushNotification(title: String, body: String) {
        val web = ref?.get() ?: return
        val safeTitle = jsString(title)
        val safeBody = jsString(body)
        web.post {
            web.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('flvy-notification',{detail:{title:$safeTitle,body:$safeBody}}));",
                null
            )
        }
    }

    private fun jsString(value: String): String =
        "'" + value.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", " ")
            .take(500) + "'"
}
