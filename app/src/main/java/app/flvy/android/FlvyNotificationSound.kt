package app.flvy.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Native adaptation of MacLaine's actual split-flap sound model:
 * click/noise through a band-pass-like component plus a falling-frequency
 * housing thump, with the same clack/heavy/soft/tick profiles.
 */
object FlvyNotificationSound {
    private const val RATE = 48_000
    private val lock = Any()
    private var active: AudioTrack? = null

    fun play(context: Context) {
        val profile = context.getSharedPreferences("flvy", Context.MODE_PRIVATE)
            .getString("notification_sound", "clack") ?: "clack"
        if (profile == "off") return
        val pcm = synth(profile)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC).build()
        }.getOrNull() ?: return

        synchronized(lock) {
            active?.runCatching { stop() }
            active?.release()
            active = track
        }
        runCatching {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread {
                Thread.sleep(pcm.size * 1000L / RATE + 80L)
                synchronized(lock) { if (active === track) active = null }
                runCatching { track.stop() }
                track.release()
            }.start()
        }.onFailure { runCatching { track.release() } }
    }

    private data class Part(val click: Boolean, val a: DoubleArray, val at: Double = 0.0)

    private fun synth(profile: String): ShortArray {
        val steps: List<Part>
        val final: List<Part>
        when (profile) {
            "heavy" -> {
                steps = listOf(Part(true, doubleArrayOf(1900.0,1.4,.022,.20)), Part(false,doubleArrayOf(320.0,220.0,.025,.08)))
                final = listOf(Part(true,doubleArrayOf(1700.0,1.2,.030,.26)),Part(false,doubleArrayOf(135.0,60.0,.110,.45)),Part(true,doubleArrayOf(2400.0,1.6,.014,.09),.028))
            }
            "soft" -> {
                steps = listOf(Part(true,doubleArrayOf(1400.0,.9,.016,.12)))
                final = listOf(Part(true,doubleArrayOf(1150.0,.9,.022,.15)),Part(false,doubleArrayOf(150.0,90.0,.060,.22)))
            }
            "tick" -> {
                steps = listOf(Part(true,doubleArrayOf(4300.0,3.0,.006,.09)))
                final = listOf(Part(true,doubleArrayOf(3600.0,3.0,.010,.15)))
            }
            else -> {
                steps = listOf(Part(true,doubleArrayOf(2700.0,1.8,.018,.20)),Part(true,doubleArrayOf(950.0,1.2,.012,.07)))
                final = listOf(Part(true,doubleArrayOf(2300.0,1.5,.024,.28)),Part(false,doubleArrayOf(190.0,95.0,.075,.34)),Part(true,doubleArrayOf(3100.0,2.0,.010,.07),.016))
            }
        }
        val total = if (profile == "heavy") 150 else if (profile == "soft") 100 else if (profile == "tick") 50 else 115
        val out = DoubleArray(RATE * total / 1000)
        val parts = steps + final
        var cursor = 0
        val random = Random(0xF1A7)
        for (part in parts) {
            val start = (if (part in final) cursor + 35 else cursor) * RATE / 1000
            val a = part.a
            val durMs = if (part.click) a[2] * 1000.0 else a[2] * 1000.0
            val end = (start + durMs * RATE / 1000).toInt().coerceAtMost(out.size)
            for (i in start until end) {
                val t = (i - start).toDouble() / RATE
                val p = ((i - start).toDouble() / (end - start).coerceAtLeast(1))
                val env = if (p < .12) p / .12 else exp(-7.0 * (p - .12))
                val v = if (part.click) {
                    val noise = random.nextDouble() * 2 - 1
                    val tone = sin(2 * PI * a[0] * t)
                    .72 * noise + .28 * tone
                } else {
                    val f = a[0] * exp(kotlin.math.ln(a[1] / a[0]) * p)
                    sin(2 * PI * f * t)
                }
                out[i] += v * env * a[3]
            }
            cursor += if (part in final) 55 else 28
        }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(.001)
        val gain = .72 / peak
        return ShortArray(out.size) { (out[it] * gain * Short.MAX_VALUE).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }
}