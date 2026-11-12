package be.maikel.echo

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

const val SR = 22050

/** Voix du perroquet : un sifflement doux avec un petit trille au début de chaque note. Pur Kotlin. */
object Tone {
    fun parrot(midi: Int, ms: Int, vol: Float = 0.55f): ShortArray {
        val n = SR * ms / 1000
        val out = ShortArray(n)
        val f = Notes.freq(midi)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            // petite glissade vers la note (≈ 40 ms) puis léger vibrato
            val glide = if (t < 0.04) 1.0 - 0.06 * (1 - t / 0.04) else 1.0
            val vib = 1.0 + 0.006 * sin(2 * PI * 6.0 * t)
            phase += 2 * PI * f * glide * vib / SR
            val attack = minOf(1.0, i / (SR * 0.015))
            val release = minOf(1.0, (n - i) / (SR * 0.04))
            val env = attack * release * (0.7 + 0.3 * exp(-t * 4.0))
            val s = sin(phase) + 0.25 * sin(2 * phase) + 0.08 * sin(3 * phase)
            out[i] = (s / 1.33 * env * vol * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Petit son de victoire (arpège montant). */
    fun win(): ShortArray = join(listOf(72, 76, 79, 84).map { parrot(it, 110, 0.45f) })

    /** Son de défaite (deux notes qui descendent). */
    fun lose(): ShortArray = join(listOf(64, 58).map { parrot(it, 260, 0.45f) })

    fun join(parts: List<ShortArray>): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var k = 0
        for (p in parts) { p.copyInto(out, k); k += p.size }
        return out
    }
}

/** Joue un son court. */
object Player {
    private var track: AudioTrack? = null
    fun play(pcm: ShortArray) {
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        if (pcm.isEmpty()) return
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            t.write(pcm, 0, pcm.size); t.play(); track = t
        } catch (_: Exception) { }
    }
}

/**
 * Écoute du micro : une mesure de hauteur toutes les [HOP] échantillons (≈ 21 par seconde),
 * livrée sur le fil de l'écran (MIDI exact, ou NaN = silence).
 */
class Listener(private val onFrame: (Double) -> Unit, private val onError: () -> Unit) {
    companion object {
        const val HOP = 1024
        const val SIZE = 2048
        val FPS = SR / HOP.toDouble()
    }

    @Volatile private var running = false
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        Thread({
            val yin = Yin(SR, SIZE)
            val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val ar = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SIZE * 2))
            } catch (e: Exception) { null }
            if (ar == null || ar.state != AudioRecord.STATE_INITIALIZED) {
                ar?.release(); running = false; main.post { onError() }; return@Thread
            }
            val buf = ShortArray(HOP)
            val win = FloatArray(SIZE)
            try {
                ar.startRecording()
                while (running) {
                    val n = ar.read(buf, 0, HOP)
                    if (n <= 0) { Thread.sleep(20); continue }
                    // fenêtre glissante des SIZE derniers échantillons
                    System.arraycopy(win, n, win, 0, SIZE - n)
                    for (i in 0 until n) win[SIZE - n + i] = buf[i] / 32768f
                    val f = yin.detect(win)
                    val m = if (f > 60f && yin.clarity > 0.8f) Notes.midiExact(f.toDouble()) else Double.NaN
                    main.post { if (running) onFrame(m) }
                }
            } catch (_: Exception) {
            } finally {
                try { ar.stop() } catch (_: Exception) {}
                ar.release()
            }
        }, "echo-mic").start()
    }

    fun stop() { running = false }
    fun isRunning() = running
}
