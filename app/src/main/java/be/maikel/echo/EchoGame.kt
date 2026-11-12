package be.maikel.echo

import kotlin.math.abs
import kotlin.random.Random

enum class Phase { LISTEN, SING, ROUND_WON, OVER }

/** Pourquoi la partie s'arrête. */
enum class Miss(val text: String) {
    WRONG("Mauvaise note"),
    TIMEOUT("Trop lent"),
}

/**
 * Le jeu Écho : le perroquet chante une mélodie, tu la rechantes note par note.
 * À chaque tour réussi, il ajoute une note. Pur Kotlin, testable.
 *
 * Une note est validée quand ta voix reste à ±[TOL] cents de la note pendant [HOLD_SEC] s.
 * L'octave ne compte pas : une voix grave ou aiguë peut jouer avec n'importe quelle mélodie.
 * Erreur : une autre note tenue [WRONG_SEC] s (à plus de [WRONG_CENTS] cents), ou rien de bon en [TIMEOUT_SEC] s.
 */
class EchoGame(
    val low: Int,
    val high: Int,
    seed: Long,
    val fps: Double,
    startLength: Int = 2,
) {
    companion object {
        const val TOL = 60.0
        const val WRONG_CENTS = 150.0
        const val HOLD_SEC = 0.3
        const val WRONG_SEC = 0.7
        const val TIMEOUT_SEC = 5.0
        const val SMOOTH_SEC = 0.2

        /** Écart en cents à la note visée, octave repliée (−600..600). */
        fun foldedCents(midi: Double, target: Int): Double {
            var c = (midi - target) * 100
            while (c > 600) c -= 1200
            while (c < -600) c += 1200
            return c
        }
    }

    private val rng = Random(seed)
    val melody = ArrayList<Int>()

    var phase = Phase.LISTEN; private set
    /** Note à chanter (indice dans la mélodie). */
    var index = 0; private set
    /** Plus longue mélodie réussie. */
    var score = 0; private set
    var miss: Miss? = null; private set
    /** Dernier écart (cents, octave repliée) à la note visée, ou null si silence. */
    var cents: Double? = null; private set

    private val window = ArrayDeque<Double>()
    private val w = maxOf(3, Math.round(SMOOTH_SEC * fps).toInt())
    private var okFrames = 0
    private var wrongFrames = 0
    private var noteFrames = 0

    init {
        melody += rng.nextInt(low, high + 1)
        while (melody.size < startLength) melody += nextNote()
    }

    val target: Int? get() = if (phase == Phase.SING) melody.getOrNull(index) else null
    val holdProgress: Double get() = (okFrames / (HOLD_SEC * fps)).coerceIn(0.0, 1.0)

    /** Durée d'une note jouée par le perroquet : de plus en plus vite. */
    fun noteMs(): Int = maxOf(380, 700 - (melody.size - 2) * 35)

    /** Saut maximal entre deux notes : de plus en plus grand. */
    fun maxStep(): Int = when {
        melody.size < 4 -> 2
        melody.size < 7 -> 4
        else -> 7
    }

    private fun nextNote(): Int {
        val last = melody.last()
        val step = maxStep()
        val choices = (-step..step).filter { it != 0 && last + it in low..high }
        return last + choices[rng.nextInt(choices.size)]
    }

    /** Le perroquet a fini de chanter : à toi. */
    fun startSinging() {
        if (phase != Phase.LISTEN) return
        phase = Phase.SING
        index = 0
        resetNote()
    }

    /** Tour réussi : on ajoute une note et le perroquet rechante. */
    fun nextRound() {
        if (phase != Phase.ROUND_WON) return
        melody += nextNote()
        phase = Phase.LISTEN
        index = 0
    }

    private fun resetNote() {
        okFrames = 0; wrongFrames = 0; noteFrames = 0
        window.clear()
        cents = null
    }

    private fun smooth(midi: Double): Double {
        window.addLast(midi)
        while (window.size > w) window.removeFirst()
        val v = window.filter { !it.isNaN() }.sorted()
        if (v.size < (w + 1) / 2) return Double.NaN
        return v[v.size / 2]
    }

    /**
     * Une mesure du micro (MIDI exact, NaN = silence). À appeler ≈ [fps] fois par seconde pendant [Phase.SING].
     * Renvoie true si l'état a changé (note validée, tour gagné, partie perdue).
     */
    fun feed(midi: Double): Boolean {
        if (phase != Phase.SING) return false
        val t = melody[index]
        noteFrames++
        val m = smooth(midi)
        cents = if (m.isNaN()) null else foldedCents(m, t)
        val c = cents
        when {
            c != null && abs(c) <= TOL -> { okFrames++; wrongFrames = 0 }
            c != null && abs(c) > WRONG_CENTS -> { wrongFrames++; okFrames = 0 }
            else -> okFrames = 0
        }
        if (okFrames >= HOLD_SEC * fps) {
            index++
            if (index >= melody.size) {
                score = melody.size
                phase = Phase.ROUND_WON
                cents = null
            } else resetNote()
            return true
        }
        if (wrongFrames >= WRONG_SEC * fps) return lose(Miss.WRONG)
        if (noteFrames >= TIMEOUT_SEC * fps) return lose(Miss.TIMEOUT)
        return false
    }

    private fun lose(m: Miss): Boolean {
        miss = m
        phase = Phase.OVER
        return true
    }
}
