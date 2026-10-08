package app.flvy.android

import android.app.Activity
import android.app.Dialog
import android.app.WallpaperManager
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONObject
import androidx.webkit.WebViewAssetLoader
import androidx.core.app.ActivityCompat
import androidx.webkit.WebViewClientCompat

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPlaceJson: String? = null
    private var pageReady = false
    private val prefs by lazy { getSharedPreferences("flvy", MODE_PRIVATE) }
    private val fredoka by lazy {
        runCatching { Typeface.createFromAsset(assets, "Fredoka.ttf") }
            .getOrElse { Typeface.create("sans-serif-rounded", Typeface.BOLD) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
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
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
                @Suppress("DEPRECATION")
                override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                    loader.shouldInterceptRequest(Uri.parse(url))
                override fun onPageFinished(view: WebView, url: String) {
                    pageReady = true
                    applyDisplayFlags()
                    pendingPlaceJson?.let { injectPlace(it); pendingPlaceJson = null }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
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
        val settingsButton = TextView(this).apply {
            text = "☰"
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = fredoka
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(190, 10, 10, 10), Color.argb(90, 255, 255, 255), 18)
            setOnClickListener { showControlCenter() }
            contentDescription = "FLVY settings"
        }
        root.addView(settingsButton, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(14), dp(14), 0)
        })

        setContentView(root)
        web.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html")
        FlvyWebBridge.attach(web)

        if (FlvyLocation.hasPermission(this)) FlvyLocation.resolve(this, ::applyPlace)
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), FlvyLocation.REQUEST_CODE)
    }

    private fun showControlCenter() {
        val dialog = Dialog(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.BLACK); overScrollMode = View.OVER_SCROLL_NEVER }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        scroll.addView(page)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "FLVY"; typeface = fredoka; textSize = 30f; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        header.addView(TextView(this).apply {
            text = "×"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        page.addView(header)
        page.addView(TextView(this).apply {
            text = "Your display. Your rules."; textSize = 15f; setTextColor(Color.LTGRAY); typeface = fredoka
            setPadding(0, 0, 0, dp(18))
        })

        section(page, "DISPLAY", "Pure black OLED background, no grey wash.") {
            addView(Switch(this@MainActivity).apply {
                text = "AMOLED black"; textSize = 16f; setTextColor(Color.WHITE); typeface = fredoka
                isChecked = prefs.getBoolean("amoled", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("amoled", checked).apply(); applyDisplayFlags() }
            }, LinearLayout.LayoutParams(-1, dp(56)))
        }

        section(page, "POWER", "Only animate hard when the flaps are moving.") {
            addView(Switch(this@MainActivity).apply {
                text = "Low-power wallpaper"; textSize = 16f; setTextColor(Color.WHITE); typeface = fredoka
                isChecked = prefs.getBoolean("low_power", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("low_power", checked).apply() }
            }, LinearLayout.LayoutParams(-1, dp(56)))
        }

        section(page, "MAKE IT YOURS", "The original split-flap editor gives you the deep controls.") {
            action("Customize display", "Layouts · looks · pages · schedules") {
                dialog.dismiss(); web.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html"); immersive()
            }
            action("Choose a look", "Black · White · Solari · custom") {
                dialog.dismiss(); web.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html#looks")
            }
            action("Screens & schedules", "Build multiple boards and playlists") {
                dialog.dismiss(); web.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html#sb")
            }
        }

        section(page, "LIVE", "Put the same FLVY board behind your apps.") {
            action("Set as live wallpaper", "Use the real FLVY split-flap engine") { dialog.dismiss(); setLiveWallpaper() }
        }

        section(page, "NOTIFICATION SOUND", "Give incoming messages the sound of a real board.") {
            soundChoice(page, "Flap", "flap", "A short three-clack mechanical roll")
            soundChoice(page, "Soft", "soft", "Quieter two-clack version")
            soundChoice(page, "Tick", "tick", "Single crisp flap")
            soundChoice(page, "Off", "off", "Silent notifications")
        }

        section(page, "CONTENT", "Connect only what you choose.") {
            action("Notifications", "Show incoming messages on the board") { dialog.dismiss(); openNotificationSettings() }
            action("Location & weather", "Use device location for local content") { dialog.dismiss(); openLocationSettings() }
            action("Choose media", "Add your own images") { dialog.dismiss(); chooseMedia() }
        }

        section(page, "ANDROID", "System-level controls.") {
            action("App permissions", "Review FLVY access") { dialog.dismiss(); openAppSettings() }
            action("Fullscreen AMOLED", "Hide system chrome in FLVY") { dialog.dismiss(); immersive() }
        }

        dialog.setContentView(scroll)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(rounded(Color.BLACK, Color.TRANSPARENT, 26))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            attributes = attributes.apply { gravity = Gravity.BOTTOM }
        }
    }

    private fun LinearLayout.action(title: String, detail: String, click: () -> Unit) {
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(Color.rgb(12, 12, 12), Color.rgb(34, 34, 34), 18)
            isClickable = true; minimumHeight = dp(64); setOnClickListener { click() }
        }
        addView(row, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
        row.addView(TextView(this@MainActivity).apply {
            text = title; textSize = 17f; setTextColor(Color.WHITE); typeface = fredoka
        })
        row.addView(TextView(this@MainActivity).apply {
            text = detail; textSize = 12f; setTextColor(Color.rgb(155, 155, 155)); setPadding(0, dp(3), 0, 0)
        })
    }

    private fun soundChoice(parent: LinearLayout, title: String, value: String, detail: String) {
        val selected = prefs.getString("notification_sound", "flap") == value
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(if (selected) Color.rgb(20, 20, 20) else Color.rgb(8, 8, 8),
                if (selected) Color.rgb(75, 75, 75) else Color.rgb(28, 28, 28), 16)
            isClickable = true
            setOnClickListener {
                prefs.edit().putString("notification_sound", value).apply()
                FlvyNotificationSound.play(this@MainActivity)
                // Keep the settings sheet open; the next open reflects the new selection.
            }
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this).apply {
            text = title; textSize = 16f; setTextColor(Color.WHITE); typeface = fredoka
        })
        copy.addView(TextView(this).apply {
            text = detail; textSize = 11f; setTextColor(Color.rgb(135, 135, 135))
        })
        row.addView(copy, LinearLayout.LayoutParams(0, dp(58), 1f))
        row.addView(TextView(this).apply {
            text = if (selected) "✓" else ""
            textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(34), dp(58)))
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(6) })
    }

    private fun section(parent: LinearLayout, title: String, detail: String, content: LinearLayout.() -> Unit) {
        parent.addView(TextView(this).apply {
            text = title; textSize = 11f; setTextColor(Color.rgb(150, 150, 150)); letterSpacing = 0.16f; typeface = fredoka
            setPadding(dp(4), dp(14), 0, dp(7))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(7, 7, 7), Color.rgb(28, 28, 28), 22)
        }
        card.addView(TextView(this).apply {
            text = detail; textSize = 12f; setTextColor(Color.rgb(135, 135, 135)); setPadding(dp(8), 0, dp(8), dp(8))
        })
        card.content()
        parent.addView(card, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun applyDisplayFlags() {
        if (!pageReady) return
        val black = prefs.getBoolean("amoled", true)
        val bg = if (black) "#000000" else "#080808"
        val flag = if (black) "1" else "0"
        web.setBackgroundColor(if (black) Color.BLACK else Color.rgb(8, 8, 8))
        web.evaluateJavascript("(function(){var bg='$bg';document.documentElement.dataset.chrome='dark';document.documentElement.style.backgroundColor=bg;document.body.style.backgroundColor=bg;document.documentElement.style.colorScheme='dark';var s=document.querySelector('.sf');if(s)s.style.backgroundColor=bg;try{localStorage.setItem('theme','dark');localStorage.setItem('flvy_amoled','$flag')}catch(e){}})();", null)
    }

    private fun setLiveWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, FlvyWallpaperService::class.java))
        }
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
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
        web.evaluateJavascript("localStorage.setItem('sf_place'," + JSONObject.quote(json) + ");location.reload();", null)
    }

    private fun openNotificationSettings() {
        val host = this
        val intent = if (android.os.Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(host, FlvyNotificationListener::class.java))
        } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun chooseMedia() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, MEDIA_REQUEST)
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == FlvyLocation.REQUEST_CODE && FlvyLocation.hasPermission(this)) FlvyLocation.resolve(this, ::applyPlace)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val result = if (resultCode == RESULT_OK && data != null) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            fileCallback?.onReceiveValue(result); fileCallback = null
        }
    }

    override fun onResume() { super.onResume(); immersive(); if (::web.isInitialized) web.onResume() }
    override fun onPause() { if (::web.isInitialized) web.onPause(); super.onPause() }

    override fun onDestroy() {
        FlvyWebBridge.detach(web); web.destroy(); super.onDestroy()
    }

    private fun immersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FILE_CHOOSER_REQUEST = 4101
        private const val MEDIA_REQUEST = 4102
    }
}
