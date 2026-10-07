package app.flvy.android

import android.graphics.Canvas
import android.graphics.Color
import android.service.wallpaper.WallpaperService
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
 * Live wallpaper uses the same MacLaine split-flap WebView engine as the main FLVY screen.
 * There is deliberately no second/native clock renderer here.
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

        private val redraw = object : Runnable {
            override fun run() {
                if (!visible || destroyed) return
                render()
                handler.postDelayed(this, 1000L)
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            createWebView()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(redraw)
            if (isVisible) {
                createWebView()
                render()
                handler.post(redraw)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            layoutWebView()
            if (visible) render()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(redraw)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            destroyed = true
            visible = false
            handler.removeCallbacks(redraw)
            destroyWebView()
            super.onDestroy()
        }

        private fun createWebView() {
            if (web != null || destroyed) return

            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler(
                    "/assets/",
                    WebViewAssetLoader.AssetsPathHandler(this@FlvyWallpaperService)
                )
                .build()

            host = FrameLayout(this@FlvyWallpaperService).apply {
                setBackgroundColor(Color.BLACK)
            }

            web = WebView(this@FlvyWallpaperService).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                    @Suppress("DEPRECATION")
                    override fun shouldInterceptRequest(
                        view: WebView,
                        url: String
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(Uri.parse(url))
                }
            }

            host!!.addView(
                web,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            layoutWebView()

            // Kiosk keeps editor chrome out of the wallpaper. The board itself is the wallpaper.
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
            val canvas: Canvas = try {
                holder.lockCanvas()
            } catch (_: Exception) {
                null
            } ?: return

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
