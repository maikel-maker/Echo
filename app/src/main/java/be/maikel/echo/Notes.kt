package be.maikel.echo

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Notes en notation belge/française : Do3 = do du milieu du piano (MIDI 60), La3 = 440 Hz. */
object Notes {
    private val NAMES = arrayOf("Do", "Do#", "Ré", "Ré#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")

    fun freq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun midiExact(freq: Double): Double = 69 + 12 * log2(freq / 440.0)

    fun midi(freq: Double): Int = midiExact(freq).roundToInt()

    fun name(midi: Int): String = NAMES[((midi % 12) + 12) % 12] + (Math.floorDiv(midi, 12) - 2)

    private val EN = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    /** Notation internationale : C4 = do du milieu. */
    fun nameEn(midi: Int): String = EN[((midi % 12) + 12) % 12] + (Math.floorDiv(midi, 12) - 1)

    fun isBlack(midi: Int) = ((midi % 12) + 12) % 12 in setOf(1, 3, 6, 8, 10)

    /** Écart en cents entre une fréquence chantée et la note visée (repliée à ±600 si erreur d'octave). */
    fun cents(freq: Double, targetMidi: Int): Double {
        var c = (midiExact(freq) - targetMidi) * 100
        while (c > 600) c -= 1200
        while (c < -600) c += 1200
        return c
    }
}

