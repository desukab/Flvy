package app.flvy.android

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlvyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = FlvyEngine()

    inner class FlvyEngine : Engine() {
        private val handler = android.os.Handler(mainLooper)
        private var visible = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("EEE  dd  MMM", Locale.getDefault())

        private val tick = object : Runnable {
            override fun run() {
                if (visible) {
                    draw()
                    handler.postDelayed(this, 1000L)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(tick)
            if (visible) {
                draw()
                handler.post(tick)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(tick)
            super.onDestroy()
        }

        private fun draw() {
            val canvas = try { surfaceHolder.lockCanvas() } catch (_: Exception) { null } ?: return
            try {
                canvas.drawColor(Color.BLACK)
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                val now = Date()

                paint.textAlign = Paint.Align.CENTER
                paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)

                label(canvas, "FLVY", w / 2f, h * 0.16f, h * 0.045f)
                flaps(canvas, timeFormat.format(now), w / 2f, h * 0.48f, h * 0.22f)
                flaps(canvas, dateFormat.format(now).uppercase(Locale.getDefault()), w / 2f, h * 0.72f, h * 0.075f)

                val prefs = getSharedPreferences("flvy", MODE_PRIVATE)
                val notification = prefs.getString("last_notification_title", null)
                if (!notification.isNullOrBlank()) {
                    label(canvas, notification.take(34).uppercase(Locale.getDefault()), w / 2f, h * 0.91f, h * 0.032f)
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        private fun label(canvas: Canvas, text: String, cx: Float, cy: Float, size: Float) {
            paint.color = Color.rgb(205, 159, 76)
            paint.textSize = size
            canvas.drawText(text, cx, cy, paint)
        }

        private fun flaps(canvas: Canvas, text: String, cx: Float, cy: Float, size: Float) {
            val gap = size * 0.08f
            val cellW = size * 0.64f
            val cellH = size * 0.92f
            val total = text.length * cellW + (text.length - 1) * gap
            var x = cx - total / 2f

            for (char in text) {
                val rect = RectF(x, cy - cellH / 2f, x + cellW, cy + cellH / 2f)
                paint.color = Color.rgb(23, 23, 23)
                canvas.drawRoundRect(rect, size * 0.06f, size * 0.06f, paint)

                val flap = RectF(rect.left + 2f, rect.top + 2f, rect.right - 2f, rect.bottom - 2f)
                paint.color = Color.rgb(190, 140, 55)
                canvas.drawRoundRect(flap, size * 0.045f, size * 0.045f, paint)

                paint.color = Color.rgb(12, 10, 8)
                canvas.drawRect(flap.left, cy - 2f, flap.right, cy + 2f, paint)

                paint.color = Color.rgb(12, 10, 8)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = size * 0.68f
                canvas.drawText(char.toString(), flap.centerX(), cy + paint.textSize * 0.34f, paint)
                x += cellW + gap
            }
        }
    }
}
