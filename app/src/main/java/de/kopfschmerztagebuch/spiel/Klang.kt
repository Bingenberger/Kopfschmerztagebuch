package de.kopfschmerztagebuch.spiel

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soundeffekte werden beim Start synthetisch erzeugt (keine Audiodateien nötig),
 * als WAV in den Cache geschrieben und über SoundPool latenzarm abgespielt.
 */
class Klang(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = mutableMapOf<Ereignis, Int>()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

    init {
        val dir = File(context.cacheDir, "klang").apply { mkdirs() }
        fun laden(e: Ereignis, samples: FloatArray) {
            val f = File(dir, "${e.name.lowercase()}.wav")
            f.writeBytes(wav(samples))
            ids[e] = pool.load(f.path, 1)
        }
        laden(Ereignis.ANLAUF, boing(220.0, 330.0, 0.18))
        laden(Ereignis.ABSPRUNG, boing(260.0, 720.0, 0.25))
        laden(Ereignis.SALTO, ton(880.0, 0.09, dreieck = true, laut = 0.35f))
        laden(Ereignis.SCHRAUBE, wusch())
        laden(Ereignis.SPLASH, rauschen(0.35, 0.22, 0.6f))
        laden(Ereignis.KLATSCHER, rauschen(0.55, 0.06, 0.95f))
        laden(Ereignis.PERFEKT, melodie(listOf(523.0, 784.0), 0.12, 0.35))
        laden(Ereignis.BELOHNUNG, melodie(listOf(523.0, 659.0, 784.0, 1047.0), 0.11, 0.45))
    }

    fun spielen(e: Ereignis, ton: Boolean, vibration: Boolean) {
        if (ton) ids[e]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
        if (vibration) vibrieren(e)
    }

    private fun vibrieren(e: Ereignis) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effekt = when (e) {
            Ereignis.SALTO, Ereignis.SCHRAUBE -> VibrationEffect.createOneShot(18, 90)
            Ereignis.ABSPRUNG -> VibrationEffect.createOneShot(40, 140)
            Ereignis.SPLASH -> VibrationEffect.createOneShot(70, 120)
            Ereignis.KLATSCHER -> VibrationEffect.createWaveform(longArrayOf(0, 180, 60, 220), intArrayOf(0, 255, 0, 200), -1)
            Ereignis.PERFEKT -> VibrationEffect.createWaveform(longArrayOf(0, 30, 70, 30), intArrayOf(0, 160, 0, 220), -1)
            Ereignis.BELOHNUNG -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 80), intArrayOf(0, 150, 0, 180, 0, 255), -1)
            Ereignis.ANLAUF -> return
        }
        v.vibrate(effekt)
    }

    fun freigeben() = pool.release()

    companion object {
        private const val RATE = 22050

        private fun ton(freq: Double, dauer: Double, dreieck: Boolean = false, laut: Float = 0.3f) = FloatArray((RATE * dauer).toInt()) { i ->
            val t = i.toDouble() / RATE
            val phase = (freq * t) % 1.0
            val w = if (dreieck) (4 * abs(phase - 0.5) - 1) else sin(2 * PI * freq * t)
            (w * exp(-t * 30) * laut).toFloat()
        }

        private fun boing(von: Double, bis: Double, dauer: Double): FloatArray {
            val n = (RATE * dauer).toInt()
            var phase = 0.0
            return FloatArray(n) { i ->
                val t = i.toDouble() / n
                phase += (von + (bis - von) * t) / RATE
                (sin(2 * PI * phase) * (1 - t) * 0.35).toFloat()
            }
        }

        private fun wusch(): FloatArray {
            val n = (RATE * 0.22).toInt()
            var lp = 0f
            return FloatArray(n) { i ->
                val t = i.toFloat() / n
                val alpha = 0.05f + 0.4f * sin(PI.toFloat() * t)
                lp += alpha * ((Random.nextFloat() * 2 - 1) - lp)
                lp * sin(PI.toFloat() * t) * 0.8f
            }
        }

        /** Gefiltertes Rauschen; kleines [hell] = dumpfer Klang. */
        private fun rauschen(dauer: Double, hell: Double, laut: Float): FloatArray {
            val n = (RATE * dauer).toInt()
            var lp = 0f
            return FloatArray(n) { i ->
                lp += hell.toFloat() * ((Random.nextFloat() * 2 - 1) - lp)
                lp * (1 - i.toFloat() / n) * laut * 2.2f
            }
        }

        private fun melodie(freqs: List<Double>, abstand: Double, halten: Double): FloatArray {
            val n = (RATE * (abstand * freqs.size + halten)).toInt()
            val out = FloatArray(n)
            freqs.forEachIndexed { k, f ->
                val start = (RATE * abstand * k).toInt()
                for (i in 0 until (RATE * halten).toInt()) {
                    if (start + i >= n) break
                    val t = i.toDouble() / RATE
                    val huelle = (if (t < 0.02) t / 0.02 else exp(-(t - 0.02) * 9))
                    out[start + i] += (sin(2 * PI * f * t) * huelle * 0.22).toFloat()
                }
            }
            return out
        }

        private fun wav(samples: FloatArray): ByteArray {
            val daten = samples.size * 2
            val b = ByteBuffer.allocate(44 + daten).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()).putInt(36 + daten).put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            b.put("data".toByteArray()).putInt(daten)
            samples.forEach { b.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
            return b.array()
        }
    }
}
