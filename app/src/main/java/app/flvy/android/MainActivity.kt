package app.flvy.android

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import org.json.JSONObject
import androidx.webkit.WebViewAssetLoader
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewClientCompat

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPlaceJson: String? = null
    private var pageReady = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)

            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this@MainActivity))
                .build()

            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

                @Suppress("DEPRECATION")
                override fun shouldInterceptRequest(
                    view: WebView,
                    url: String
                ): WebResourceResponse? = loader.shouldInterceptRequest(Uri.parse(url))

                override fun onPageFinished(view: WebView, url: String) {
                    pageReady = true
                    pendingPlaceJson?.let {
                        injectPlace(it)
                        pendingPlaceJson = null
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    return try {
                        startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST)
                        true
                    } catch (_: ActivityNotFoundException) {
                        fileCallback = null
                        false
                    }
                }
            }
        }

        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        val settingsButton = Button(this).apply {
            text = "⚙"
            textSize = 20f
            setTextColor(Color.rgb(232, 194, 112))
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                setStroke(dp(1), Color.rgb(90, 68, 32))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { showControlCenter() }
            contentDescription = "FLVY settings"
        }

        val buttonParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.END)
        buttonParams.setMargins(0, dp(14), dp(14), 0)
        root.addView(settingsButton, buttonParams)

        setContentView(root)

        // IMPORTANT: no kiosk=1 here. The upstream editor, screens, looks,
        // schedules and help must remain reachable from inside the app.
        web.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html")
        FlvyWebBridge.attach(web)
        if (FlvyLocation.hasPermission(this)) FlvyLocation.resolve(this, ::applyPlace)
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), FlvyLocation.REQUEST_CODE)
    }

    private fun showControlCenter() {
        val items = arrayOf(
            "Customize screens / editor",
            "Set as live wallpaper",
            "Location / Indian weather",
            "Notifications",
            "Choose media / storage",
            "Android app permissions",
            "Fullscreen AMOLED board"
        )

        AlertDialog.Builder(this)
            .setTitle("FLVY")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        web.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html")
                        immersive()
                    }
                    1 -> setLiveWallpaper()
                    2 -> openLocationSettings()
                    3 -> openNotificationSettings()
                    4 -> chooseMedia()
                    5 -> openAppSettings()
                    6 -> immersive()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setLiveWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, FlvyWallpaperService::class.java)
            )
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun openLocationSettings() {
        if (FlvyLocation.hasPermission(this)) FlvyLocation.resolve(this, ::applyPlace)
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), FlvyLocation.REQUEST_CODE)
    }

    private fun applyPlace(json: String) {
        pendingPlaceJson = json
        if (pageReady) { injectPlace(json); pendingPlaceJson = null }
    }

    private fun injectPlace(json: String) {
        web.evaluateJavascript("localStorage.setItem('sf_place', " + JSONObject.quote(json) + "); location.reload();", null)
    }

    private fun openNotificationSettings() {
        val host = this
        val intent = if (android.os.Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(host, FlvyNotificationListener::class.java)
                )
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun chooseMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, MEDIA_REQUEST)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == FlvyLocation.REQUEST_CODE && FlvyLocation.hasPermission(this)) FlvyLocation.resolve(this, ::applyPlace)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val result = if (resultCode == RESULT_OK && data != null) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        if (::web.isInitialized) web.onResume()
    }

    override fun onPause() {
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        FlvyWebBridge.detach(web)
        web.destroy()
        super.onDestroy()
    }

    private fun immersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FILE_CHOOSER_REQUEST = 4101
        private const val MEDIA_REQUEST = 4102
    }
}
