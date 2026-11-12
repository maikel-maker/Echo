package be.maikel.echo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoGameTest {
    private val fps = 21.5

    private fun game(seed: Long = 1) = EchoGame(55, 67, seed, fps)

    /** Chante [midi] pendant [sec] secondes. */
    private fun EchoGame.sing(midi: Double, sec: Double) { repeat((sec * fps).toInt()) { if (phase == Phase.SING) feed(midi) } }

    @Test fun melodyStaysInRangeAndStepsAreSmallAtFirst() {
        val g = game()
        assertEquals(2, g.melody.size)
        assertTrue(g.melody.all { it in 55..67 })
        assertTrue(kotlin.math.abs(g.melody[1] - g.melody[0]) in 1..2)
    }

    @Test fun singingTheMelodyWinsTheRoundAndAddsANote() {
        val g = game()
        g.startSinging()
        for (n in g.melody.toList()) g.sing(n.toDouble(), 0.8)
        assertEquals(Phase.ROUND_WON, g.phase)
        assertEquals(2, g.score)
        g.nextRound()
        assertEquals(3, g.melody.size)
        assertEquals(Phase.LISTEN, g.phase)
    }

    @Test fun anyOctaveCounts() {
        val g = game()
        g.startSinging()
        for (n in g.melody.toList()) g.sing(n - 12.0 + 0.2, 0.8)   // une octave plus bas, 20 cents haut
        assertEquals(Phase.ROUND_WON, g.phase)
    }

    @Test fun holdingAWrongNoteLoses() {
        val g = game()
        g.startSinging()
        g.sing(g.melody[0] + 4.0, 1.5)
        assertEquals(Phase.OVER, g.phase)
        assertEquals(Miss.WRONG, g.miss)
        assertEquals(0, g.score)
    }

    @Test fun silenceTimesOut() {
        val g = game()
        g.startSinging()
        g.sing(Double.NaN, 6.0)
        assertEquals(Phase.OVER, g.phase)
        assertEquals(Miss.TIMEOUT, g.miss)
    }

    @Test fun sameSeedSameMelody() {
        assertEquals(game(42).melody, game(42).melody)
    }

    @Test fun parrotSpeedsUp() {
        val g = game()
        val first = g.noteMs()
        repeat(6) {
            g.startSinging()
            for (n in g.melody.toList()) g.sing(n.toDouble(), 0.8)
            g.nextRound()
        }
        assertTrue(g.noteMs() < first)
        assertEquals(8, g.melody.size)
    }
}
