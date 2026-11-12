package be.maikel.echo

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("echo", MODE_PRIVATE) }
    private var listener: Listener? = null
    private var back: (() -> Unit)? = null
    private var pendingMic: (() -> Unit)? = null

    /** Zone où chante le perroquet (sol4 → sol5) ; toi, tu chantes dans l'octave que tu veux. */
    private val low = 67
    private val high = 79

    private val bold = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private var best: Int
        get() = prefs.getInt("best", 0)
        set(v) = prefs.edit().putInt("best", v).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        when (intent.getStringExtra("screen")) {
            "game" -> withMic { play(System.currentTimeMillis(), duel = null) }
            "over" -> showOver(EchoGame(low, high, 7, Listener.FPS, 6), Miss.WRONG, null)
            "duel" -> showDuelResult(5, 7)
            else -> showHome()
        }
    }

    override fun onPause() { super.onPause(); stop() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { val b = back; if (b != null) b() else super.onBackPressed() }

    private fun stop() {
        listener?.stop(); listener = null
        main.removeCallbacksAndMessages(null)
    }

    // ---------------------------------------------------------------- outils d'affichage
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun text(t: String, size: Float, color: Int = Palette.INK, face: Typeface? = null, center: Boolean = true) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (face != null) typeface = face
            if (center) gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }

    private fun button(t: String, filled: Boolean = true, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 18f; typeface = medium; stateListAnimator = null
        setTextColor(if (filled) Palette.BG1 else Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(30).toFloat()
            if (filled) setColor(Color.WHITE) else { setColor(Color.TRANSPARENT); setStroke(dp(2), Color.WHITE) }
        }
        layoutParams = lp(h = dp(58), top = 12)
        setOnClickListener { onClick() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Palette.CARD) }
        layoutParams = lp(top = 16)
    }

    private fun screen(build: LinearLayout.() -> Unit) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        col.build()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Palette.BG1, Palette.BG2))
            addView(col)
        }
        setContentView(scroll)
    }

    private fun parrot(size: Float = 110f) = text("🦜", size).apply { layoutParams = lp(top = 8) }

    private fun bob(v: View) = ObjectAnimator.ofFloat(v, "translationY", 0f, -dp(10).toFloat()).apply {
        duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start()
    }

    private fun hop(v: View) {
        v.animate().cancel()
        v.scaleX = 1.18f; v.scaleY = 1.18f; v.rotation = -6f
        v.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(220).start()
    }

    // ---------------------------------------------------------------- accueil
    private fun showHome() {
        stop(); back = null
        screen {
            val p = parrot(130f)
            addView(p); bob(p)
            addView(text("ÉCHO", 44f, Color.WHITE, bold).apply { letterSpacing = 0.12f })
            addView(text("Le perroquet chante, tu répètes.\nChaque tour, il ajoute une note.", 16f, Palette.MUTED).apply { layoutParams = lp(top = 4) })
            addView(text(if (best > 0) "🏆 Record : $best notes" else "🏆 Pas encore de record", 18f, Palette.YELLOW, medium).apply { layoutParams = lp(top = 18) })
            addView(button("▶  Jouer") { withMic { play(System.currentTimeMillis(), duel = null) } })
            addView(button("👥  Duel à deux", filled = false) {
                val seed = System.currentTimeMillis()
                withMic { play(seed, duel = DuelState(seed)) }
            })
            addView(card().apply {
                addView(text("Comment jouer", 17f, Color.WHITE, medium, center = false))
                for (l in listOf(
                    "1. Écoute la mélodie du perroquet.",
                    "2. Rechante-la note par note, sur « la » ou « ou ». Tiens chaque note un court instant.",
                    "3. Grave ou aigu, peu importe : chante dans l'octave qui te va.",
                    "4. Une fausse note tenue ou trop d'hésitation… et le perroquet s'envole, vexé !",
                )) addView(text(l, 14.5f, Palette.MUTED, center = false).apply { setPadding(0, dp(6), 0, 0) })
            })
            addView(text("Pose le téléphone à 30-50 cm, dans une pièce calme.", 13f, Palette.MUTED).apply { layoutParams = lp(top = 14) })
        }
    }

    // ---------------------------------------------------------------- partie
    /** Duel : le joueur 1 joue, puis le joueur 2 rejoue la même suite de mélodies. */
    private class DuelState(val seed: Long, var first: Int? = null)

    private fun play(seed: Long, duel: DuelState?) {
        stop()
        back = { stop(); showHome() }
        val game = EchoGame(low, high, seed, Listener.FPS)

        lateinit var bird: TextView
        lateinit var melody: MelodyView
        lateinit var status: TextView
        lateinit var sub: TextView
        lateinit var meter: PitchMeter
        lateinit var scoreTxt: TextView

        screen {
            if (duel != null) addView(text(if (duel.first == null) "👤 JOUEUR 1" else "👤 JOUEUR 2 · à battre : ${duel.first} notes", 14f, Palette.YELLOW, medium))
            scoreTxt = text("Mélodie de 2 notes", 15f, Palette.MUTED, medium)
            addView(scoreTxt)
            bird = parrot(120f)
            addView(bird)
            melody = MelodyView(this@MainActivity).apply { layoutParams = lp(top = 8) }
            addView(melody)
            status = text("Écoute…", 28f, Color.WHITE, bold).apply { layoutParams = lp(top = 18) }
            addView(status)
            sub = text("", 15f, Palette.MUTED).apply { layoutParams = lp(top = 4) }
            addView(sub)
            meter = PitchMeter(this@MainActivity).apply { layoutParams = lp(top = 22) }
            addView(meter)
            addView(button("■  Arrêter", filled = false) { stop(); showHome() })
        }

        fun refresh() {
            melody.notes = game.melody.toList()
            scoreTxt.text = "Mélodie de ${game.melody.size} notes" + if (game.score > 0) "  ·  réussi : ${game.score}" else ""
        }

        fun round() {
            refresh()
            melody.done = 0; melody.hideAhead = false
            status.text = "Écoute…"; sub.text = "Le perroquet chante ${game.melody.size} notes"
            meter.cents = null; meter.hold = 0.0
            val ms = game.noteMs()
            game.melody.forEachIndexed { i, m ->
                main.postDelayed({
                    melody.current = i
                    hop(bird)
                    Player.play(Tone.parrot(m, ms - 60))
                }, (i * ms + 400).toLong())
            }
            main.postDelayed({
                game.startSinging()
                melody.current = 0; melody.hideAhead = true
                status.text = "À toi !"
                sub.text = "Note 1 sur ${game.melody.size}"
            }, (game.melody.size * ms + 600).toLong())
        }

        val l = Listener(frame@{ m ->
            if (game.phase != Phase.SING) return@frame
            val changed = game.feed(m)
            meter.cents = game.cents; meter.hold = game.holdProgress
            if (!changed) return@frame
            when (game.phase) {
                Phase.SING -> {
                    melody.done = game.index; melody.current = game.index
                    hop(bird)
                    sub.text = "Note ${game.index + 1} sur ${game.melody.size}"
                }
                Phase.ROUND_WON -> {
                    melody.done = game.melody.size; melody.current = -1
                    meter.cents = null; meter.hold = 0.0
                    status.text = "Bravo ! 🎉"
                    sub.text = "Le perroquet ajoute une note…"
                    Player.play(Tone.win())
                    hop(bird)
                    main.postDelayed({ game.nextRound(); round() }, 1300)
                }
                Phase.OVER -> {
                    Player.play(Tone.lose())
                    bird.animate().translationY(-dp(400).toFloat()).translationX(dp(200).toFloat()).rotation(25f).alpha(0f).setDuration(900).start()
                    status.text = "Oups !"
                    main.postDelayed({ showOver(game, game.miss, duel) }, 1100)
                }
                Phase.LISTEN -> {}
            }
        }, {
            toast("Micro indisponible")
            showHome()
        })
        listener = l
        l.start()
        round()
    }

    // ---------------------------------------------------------------- fin de partie
    private fun showOver(game: EchoGame, miss: Miss?, duel: DuelState?) {
        stop()
        back = { showHome() }
        val s = game.score
        val record = s > best
        if (record) best = s
        if (duel != null && duel.first != null) { showDuelResult(duel.first!!, s); return }
        screen {
            addView(text("🦜💨", 90f).apply { layoutParams = lp(top = 20) })
            addView(text("Le perroquet s'envole, vexé !", 24f, Color.WHITE, bold).apply { layoutParams = lp(top = 8) })
            addView(text(when (miss) {
                Miss.WRONG -> "Fausse note à la note ${game.index + 1}."
                Miss.TIMEOUT -> "Trop d'hésitation à la note ${game.index + 1}."
                null -> ""
            }, 16f, Palette.MUTED).apply { layoutParams = lp(top = 6) })
            addView(text("$s", 96f, Color.WHITE, bold).apply { layoutParams = lp(top = 18) })
            addView(text(if (s > 1) "notes répétées" else "note répétée", 18f, Palette.MUTED))
            addView(text(if (record && s > 0) "🏆 Nouveau record !" else "🏆 Record : $best", 20f, Palette.YELLOW, medium).apply { layoutParams = lp(top = 14) })
            if (duel != null) {
                duel.first = s
                addView(text("Passe le téléphone au joueur 2 : même mélodie, à lui de battre $s.", 16f, Color.WHITE).apply { layoutParams = lp(top = 18) })
                addView(button("▶  Joueur 2, à toi") { withMic { play(duel.seed, duel) } })
            } else {
                addView(button("↻  Rejouer") { withMic { play(System.currentTimeMillis(), null) } })
            }
            addView(button("Accueil", filled = false) { showHome() })
        }
    }

    private fun showDuelResult(p1: Int, p2: Int) {
        stop()
        back = { showHome() }
        screen {
            addView(text(if (p1 == p2) "🤝" else "🏆", 90f).apply { layoutParams = lp(top = 30) })
            addView(text(when {
                p1 > p2 -> "Le joueur 1 gagne !"
                p2 > p1 -> "Le joueur 2 gagne !"
                else -> "Égalité parfaite !"
            }, 28f, Color.WHITE, bold).apply { layoutParams = lp(top = 8) })
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = lp(top = 22) }
            for ((name, v) in listOf("Joueur 1" to p1, "Joueur 2" to p2)) {
                row.addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    addView(text("$v", 64f, Color.WHITE, bold))
                    addView(text(name, 16f, Palette.MUTED))
                })
            }
            addView(row)
            addView(button("👥  Revanche") { val seed = System.currentTimeMillis(); withMic { play(seed, DuelState(seed)) } })
            addView(button("Accueil", filled = false) { showHome() })
        }
    }

    // ---------------------------------------------------------------- micro
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun withMic(action: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else { pendingMic = action; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val a = pendingMic; pendingMic = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) a?.invoke()
        else { toast("Écho a besoin du micro pour t'écouter chanter."); showHome() }
    }
}
