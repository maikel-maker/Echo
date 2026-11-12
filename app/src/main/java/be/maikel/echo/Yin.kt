package be.maikel.echo

import kotlin.math.sqrt

/**
 * Détection de hauteur YIN (de Cheveigné & Kawahara).
 * Renvoie la fréquence en Hz, ou -1 si pas de note claire (silence, souffle, bruit).
 */
class Yin(private val sampleRate: Int, private val size: Int = 2048) {
    private val half = size / 2
    private val diff = FloatArray(half)
    private val minTau = maxOf(2, sampleRate / 1200)        // jusqu'à ~1200 Hz
    private val maxTau = minOf(half - 1, sampleRate / 60)   // jusqu'à ~60 Hz
    var threshold = 0.15f
    var minRms = 0.01f

    /** Dernière "clarté" (1 = son très pur), utile pour filtrer. */
    var clarity = 0f
        private set

    fun detect(buf: FloatArray): Float {
        clarity = 0f
        if (buf.size < size) return -1f
        var rms = 0.0
        for (i in 0 until size) rms += buf[i] * buf[i]
        rms = sqrt(rms / size)
        if (rms < minRms) return -1f

        // différence
        diff[0] = 0f
        for (tau in 1..maxTau) {
            var s = 0f
            for (i in 0 until half) {
                val d = buf[i] - buf[i + tau]
                s += d * d
            }
            diff[tau] = s
        }
        // différence normalisée cumulée
        diff[0] = 1f
        var running = 0f
        for (tau in 1..maxTau) {
            running += diff[tau]
            diff[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }
        // premier creux sous le seuil
        var tau = minTau
        var found = -1
        while (tau <= maxTau) {
            if (diff[tau] < threshold) {
                while (tau + 1 <= maxTau && diff[tau + 1] < diff[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) return -1f
        clarity = 1f - diff[found]

        // interpolation parabolique
        val better = if (found in 1 until maxTau) {
            val s0 = diff[found - 1]; val s1 = diff[found]; val s2 = diff[found + 1]
            val den = 2 * (2 * s1 - s2 - s0)
            if (den != 0f) found + (s2 - s0) / den else found.toFloat()
        } else found.toFloat()
        return sampleRate / better
    }
}
