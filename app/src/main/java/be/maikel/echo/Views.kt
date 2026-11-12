package be.maikel.echo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs

object Palette {
    val BG1 = Color.parseColor("#0B7285")
    val BG2 = Color.parseColor("#0CA678")
    val CARD = Color.parseColor("#1AFFFFFF")
    val INK = Color.WHITE
    val MUTED = Color.parseColor("#CCFFFFFF")
    val YELLOW = Color.parseColor("#FCC419")
    val RED = Color.parseColor("#FF6B6B")
    val GREEN = Color.parseColor("#51CF66")

    /** Une couleur par note (do = rouge … si = violet), comme un xylophone d'enfant. */
    private val NOTE = listOf(
        "#FF6B6B", "#FF8787", "#FF922B", "#FFA94D", "#FCC419", "#94D82D",
        "#51CF66", "#22B8CF", "#3BC9DB", "#4DABF7", "#748FFC", "#CC5DE8",
    ).map { Color.parseColor(it) }.toIntArray()

    fun note(midi: Int) = NOTE[((midi % 12) + 12) % 12]
}

/** Les notes de la mélodie, en ronds colorés. */
class MelodyView(ctx: Context) : View(ctx) {
    var notes: List<Int> = emptyList(); set(v) { field = v; invalidate() }
    /** Indice de la note mise en avant (jouée ou à chanter), −1 = aucune. */
    var current = -1; set(v) { field = v; invalidate() }
    /** Nombre de notes déjà rechantées. */
    var done = 0; set(v) { field = v; invalidate() }
    /** true = on cache les couleurs des notes à venir (pendant que tu chantes). */
    var hideAhead = false; set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (56 * d).toInt())

    override fun onDraw(c: Canvas) {
        if (notes.isEmpty()) return
        val n = notes.size
        val gap = 8 * d
        val r = minOf(18 * d, (width - gap * (n + 1)) / n / 2)
        val total = n * 2 * r + (n - 1) * gap
        var x = (width - total) / 2 + r
        val y = height / 2f
        for (i in 0 until n) {
            val big = i == current
            val rr = if (big) r * 1.25f else r
            p.style = Paint.Style.FILL
            p.color = when {
                i < done -> Palette.GREEN
                hideAhead && i > current -> Color.parseColor("#33FFFFFF")
                else -> Palette.note(notes[i])
            }
            c.drawCircle(x, y, rr, p)
            if (big) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.WHITE
                c.drawCircle(x, y, rr + 3 * d, p)
            }
            if (i < done) {
                p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = rr; p.textAlign = Paint.Align.CENTER
                c.drawText("✓", x, y + rr / 3, p)
            }
            x += 2 * r + gap
        }
    }
}

/** Jauge de justesse : le trait montre si tu es trop bas ou trop haut, la zone verte = bonne note. */
class PitchMeter(ctx: Context) : View(ctx) {
    var cents: Double? = null; set(v) { field = v; invalidate() }
    var hold = 0.0; set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (64 * d).toInt())

    override fun onDraw(c: Canvas) {
        val top = 10 * d; val bottom = height - 22 * d
        val rad = (bottom - top) / 2
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#26FFFFFF")
        c.drawRoundRect(RectF(0f, top, width.toFloat(), bottom), rad, rad, p)
        val range = 300.0
        val zone = (EchoGame.TOL / range * width / 2).toFloat()
        val mid = width / 2f
        p.color = Color.parseColor("#6651CF66")
        c.drawRect(mid - zone, top, mid + zone, bottom, p)
        // progression de la tenue
        if (hold > 0) {
            p.color = Palette.GREEN
            c.drawRect(mid - zone, bottom - 4 * d, mid - zone + (2 * zone * hold).toFloat(), bottom, p)
        }
        val v = cents
        if (v != null) {
            val x = (mid + (v.coerceIn(-range, range) / range) * width / 2).toFloat()
            p.color = if (abs(v) <= EchoGame.TOL) Palette.GREEN else Palette.YELLOW
            c.drawRoundRect(RectF(x - 3 * d, top - 6 * d, x + 3 * d, bottom + 6 * d), 3 * d, 3 * d, p)
        }
        p.color = Palette.MUTED; p.textSize = 12 * d
        p.textAlign = Paint.Align.LEFT; c.drawText("trop bas", 4 * d, height - 4 * d, p)
        p.textAlign = Paint.Align.RIGHT; c.drawText("trop haut", width - 4 * d, height - 4 * d, p)
    }
}
