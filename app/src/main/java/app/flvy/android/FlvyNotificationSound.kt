package app.flvy.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object FlvyNotificationSound {
    private const val SAMPLE_RATE = 48_000
    private val lock = Any()
    private var active: AudioTrack? = null

    fun play(context: android.content.Context) {
        val style = context.getSharedPreferences("flvy", android.content.Context.MODE_PRIVATE)
            .getString("notification_sound", "flap") ?: "flap"
        if (style == "off") return

        val pcm = synth(style)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (_: Throwable) {
            return
        }

        synchronized(lock) {
            active?.runCatching { stop() }
            active?.release()
            active = track
        }

        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread {
                try {
                    Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 80L)
                } catch (_: InterruptedException) {
                } finally {
                    synchronized(lock) {
                        if (active === track) active = null
                    }
                    runCatching { track.stop() }
                    track.release()
                }
            }.start()
        } catch (_: Throwable) {
            runCatching { track.release() }
        }
    }

    fun stop() {
        synchronized(lock) {
            active?.runCatching { stop() }
            active?.release()
            active = null
        }
    }

    private fun synth(style: String): ShortArray {
        val bursts = when (style) {
            "soft" -> listOf(0 to 90, 130 to 210)
            "tick" -> listOf(0 to 55)
            else -> listOf(0 to 85, 105 to 190, 210 to 295)
        }
        val totalMs = bursts.last().second + 40
        val out = ShortArray(SAMPLE_RATE * totalMs / 1000)
        val random = Random(0xF1A7)
        for ((startMs, endMs) in bursts) {
            val start = startMs * SAMPLE_RATE / 1000
            val end = (endMs * SAMPLE_RATE / 1000).coerceAtMost(out.size)
            for (i in start until end) {
                val t = (i - start).toDouble() / SAMPLE_RATE
                val p = (i - start).toDouble() / (end - start).coerceAtLeast(1)
                val env = if (p < 0.12) p / 0.12 else exp(-7.0 * (p - 0.12))
                val click = sin(2.0 * PI * (115.0 + 850.0 * exp(-22.0 * t)) * t)
                val noise = random.nextDouble() * 2.0 - 1.0
                val body = 0.72 * click + 0.28 * noise
                val gain = when (style) {
                    "soft" -> 0.20
                    "tick" -> 0.28
                    else -> 0.34
                }
                val sample = (body * env * gain * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[i] = (out[i].toInt() + sample)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }
}
