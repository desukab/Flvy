package app.flvy.android

import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader

/**
 * Same MacLaine split-flap engine as FLVY's main screen.
 * The renderer is active while flaps move and throttles aggressively when idle.
 */
class FlvyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = FlvyEngine()

    inner class FlvyEngine : Engine() {
        private val handler = android.os.Handler(mainLooper)
        private var visible = false
        private var destroyed = false
        private var web: WebView? = null
        private var host: FrameLayout? = null
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private var animating = false

        private val pollIdle = object : Runnable {
            override fun run() {
                if (!visible || destroyed) return
                val w = web ?: return
                w.evaluateJavascript("window.__flvyBoardIdle ? window.__flvyBoardIdle() : true") { value ->
                    if (visible && !destroyed) {
                        animating = value != "true"
                        scheduleRender()
                    }
                }
                handler.postDelayed(this, if (lowPower()) 350L else 120L)
            }
        }

        private val redraw = object : Runnable {
            override fun run() {
                if (!visible || destroyed) return
                render()
                scheduleRender()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            createWebView()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(redraw)
            handler.removeCallbacks(pollIdle)
            if (isVisible) {
                createWebView()
                setPreferredFrameRate()
                render()
                handler.post(pollIdle)
                handler.post(redraw)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            layoutWebView()
            setPreferredFrameRate()
            if (visible) render()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(redraw)
            handler.removeCallbacks(pollIdle)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            destroyed = true
            visible = false
            handler.removeCallbacks(redraw)
            handler.removeCallbacks(pollIdle)
            destroyWebView()
            super.onDestroy()
        }

        private fun lowPower(): Boolean =
            getSharedPreferences("flvy", MODE_PRIVATE).getBoolean("low_power", true)

        private fun scheduleRender() {
            handler.removeCallbacks(redraw)
            // Smooth 30fps during flap motion. Once idle, render only often enough to
            // catch clocks/page rotation. Low-power mode stretches that idle interval.
            val delay = when {
                animating -> 33L
                lowPower() -> 1500L
                else -> 500L
            }
            handler.postDelayed(redraw, delay)
        }

        private fun setPreferredFrameRate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    surfaceHolder.surface.setFrameRate(
                        30f,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                    )
                } catch (_: Exception) { }
            }
        }

        private fun createWebView() {
            if (web != null || destroyed) return
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this@FlvyWallpaperService))
                .build()

            host = FrameLayout(this@FlvyWallpaperService).apply { setBackgroundColor(Color.BLACK) }

            web = WebView(this@FlvyWallpaperService).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)
                    @Suppress("DEPRECATION")
                    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(Uri.parse(url))
                }
            }

            host!!.addView(web, FrameLayout.LayoutParams(-1, -1))
            layoutWebView()
            web!!.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html?kiosk=1")
        }

        private fun layoutWebView() {
            val h = host ?: return
            val w = web ?: return
            w.measure(
                View.MeasureSpec.makeMeasureSpec(surfaceWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(surfaceHeight, View.MeasureSpec.EXACTLY)
            )
            w.layout(0, 0, surfaceWidth, surfaceHeight)
            h.measure(
                View.MeasureSpec.makeMeasureSpec(surfaceWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(surfaceHeight, View.MeasureSpec.EXACTLY)
            )
            h.layout(0, 0, surfaceWidth, surfaceHeight)
        }

        private fun render() {
            val holder = surfaceHolder
            if (!holder.surface.isValid) return
            val canvas: Canvas = try { holder.lockCanvas() } catch (_: Exception) { null } ?: return
            try {
                canvas.drawColor(Color.BLACK)
                web?.invalidate()
                web?.draw(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun destroyWebView() {
            web?.stopLoading()
            web?.destroy()
            web = null
            host?.removeAllViews()
            host = null
        }
    }
}
