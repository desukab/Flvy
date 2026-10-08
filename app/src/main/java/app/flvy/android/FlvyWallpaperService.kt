package app.flvy.android

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
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

class FlvyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = FlvyEngine()

    inner class FlvyEngine : Engine() {
        private val handler = Handler(mainLooper)
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
                handler.postDelayed(this, if (lowPower()) 120L else 60L)
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
            holder.setFormat(PixelFormat.OPAQUE)
            holder.setKeepScreenOn(false)
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

        private fun amoled(): Boolean =
            getSharedPreferences("flvy", MODE_PRIVATE).getBoolean("amoled", true)

        private fun backgroundColor(): Int =
            if (amoled()) Color.BLACK else Color.rgb(8, 8, 8)

        private fun scheduleRender() {
            handler.removeCallbacks(redraw)
            val delay = when {
                animating -> 16L
                lowPower() -> 1600L
                else -> 600L
            }
            handler.postDelayed(redraw, delay)
        }

        private fun setPreferredFrameRate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    surfaceHolder.surface.setFrameRate(
                        60f,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                    )
                }
            }
        }

        private fun createWebView() {
            if (web != null || destroyed) return
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this@FlvyWallpaperService))
                .build()

            host = FrameLayout(this@FlvyWallpaperService).apply {
                setBackgroundColor(backgroundColor())
                clipChildren = false
                clipToPadding = false
            }

            web = WebView(this@FlvyWallpaperService).apply {
                setBackgroundColor(backgroundColor())
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                alpha = 1f
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

                    override fun onPageFinished(view: WebView, url: String) {
                        applyWallpaperAppearance(view)
                        view.post { layoutWebView(); render(); if (visible) scheduleRender() }
                    }
                }
            }

            host!!.addView(web, FrameLayout.LayoutParams(-1, -1))
            layoutWebView()
            web!!.loadUrl("https://appassets.androidplatform.net/assets/splitflap/index.html?kiosk=1")
        }

        private fun applyWallpaperAppearance(view: WebView) {
            val bg = if (amoled()) "#000000" else "#080808"
            val flag = if (amoled()) "1" else "0"
            val js = "(function(){var bg='$bg';" +
                "document.documentElement.dataset.chrome='dark';" +
                "document.documentElement.style.backgroundColor=bg;" +
                "document.body.style.backgroundColor=bg;" +
                "document.documentElement.style.colorScheme='dark';" +
                "var e=document.getElementById('sf');if(e)e.style.backgroundColor=bg;" +
                "try{localStorage.setItem('theme','dark');localStorage.setItem('flvy_amoled','$flag')}catch(e){}})();"
            view.evaluateJavascript(js, null)
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
            val canvas: Canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                return
            }
            try {
                canvas.drawColor(backgroundColor())
                web?.invalidate()
                web?.draw(canvas)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
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
