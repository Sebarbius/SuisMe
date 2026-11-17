package com.suisme.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Séance en cours : séries, chrono, repos. */
class RunnerScreen(private val a: MainActivity) : Screen {
    private val root = a.vbox()
    private val page = Page(a)
    override val view: View get() = root

    private var s: Runner.State? = null
    private var reps = 10
    private var repsKey = ""
    private var bigText: TextView? = null
    private var barFill: View? = null
    private var barRest: View? = null
    private var elapsedText: TextView? = null
    private var lastPhaseKey = ""
    private var curTimed = false

    override fun refresh() {
        root.removeAllViews()
        val st = Runner.load(a)
        s = st
        if (st == null) {
            a.pop()
            return
        }
        val exMap = Fit.exMap(a)
        elapsedText = a.txt(D.dur(Runner.elapsedSec(st)), 15f, C.PEACH, Typeface.BOLD)
        root.addView(a.topBar(st.name, C.PEACH, { a.pop() }, elapsedText!!), lpMW())
        root.addView(page.scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val b = page.body
        b.removeAllViews()
        bigText = null
        barFill = null
        barRest = null

        // ---- Commandes ----
        val ctr = a.hbox()
        ctr.addView(a.smallButton(if (st.paused) "▶ Reprendre" else "⏸ Pause", C.PEACH) { togglePause() })
        ctr.addView(a.smallButton("↶ Précédente", C.MUTED) { previous() }, lpWW().apply { leftMargin = a.dp(6) })
        ctr.addView(a.smallButton("⏭ Exo suivant", C.MUTED) { nextExercise() }, lpWW().apply { leftMargin = a.dp(6) })
        ctr.addView(View(a), lpW(1f))
        ctr.addView(a.smallButton("✕", C.ROSE) { abandon() })
        b.addView(ctr, lpMW().apply { bottomMargin = a.dp(12) })

        val item = st.items[st.ex]
        val ex = exMap[item.exId]
        val exName = ex?.name ?: "Exercice"
        val timed = ex?.timed ?: false
        val last = Fit.lastSets(a, item.exId)
        curTimed = timed

        if (st.paused) {
            val p = a.txt("Séance en pause", 14f, C.PEACH, Typeface.BOLD)
            p.gravity = Gravity.CENTER
            p.background = shape(soft(C.PEACH, 0.88f), a.dpf(14f))
            p.setPadding(0, a.dp(8), 0, a.dp(8))
            b.addView(p, lpMW().apply { bottomMargin = a.dp(12) })
        }

        if (st.phase == Runner.WORK) {
            val c = a.card()
            c.addView(a.caption("Exercice ${st.ex + 1}/${st.items.size} · ${ex?.group ?: ""}"))
            c.addView(a.txt(exName, 24f, C.TEXT, Typeface.BOLD))
            c.addView(a.txt("Série ${st.setIdx + 1} / ${item.sets}", 16f, C.PEACH))
            c.addView(a.gap(14))
            if (!timed) {
                val key = "${st.ex}:${st.setIdx}"
                if (key != repsKey) {
                    repsKey = key
                    reps = st.done[st.ex].getOrNull(st.setIdx)
                        ?: last.getOrNull(st.setIdx)
                        ?: st.done[st.ex].lastOrNull()
                        ?: last.lastOrNull()
                        ?: 10
                }
                val r = a.hbox()
                r.gravity = Gravity.CENTER
                r.addView(a.roundButton("–", C.PEACH, 64) { if (reps > 0) reps--; bigText?.text = "$reps" })
                val v = a.txt("$reps", 54f, C.TEXT, Typeface.BOLD)
                v.gravity = Gravity.CENTER
                v.typeface = Typeface.create("serif", Typeface.BOLD)
                v.setOnClickListener { a.askNumber("Répétitions", reps) { n -> reps = n; bigText?.text = "$reps" } }
                bigText = v
                r.addView(v, LinearLayout.LayoutParams(a.dp(130), WRAP))
                r.addView(a.roundButton("+", C.PEACH, 64) { reps++; bigText?.text = "$reps" })
                c.addView(r, lpMW())
                c.addView(a.caption("répétitions").apply { gravity = Gravity.CENTER }, lpMW())
                if (last.isNotEmpty()) c.addView(a.caption("Dernière fois : " + last.joinToString(" · ")).apply { gravity = Gravity.CENTER; setPadding(0, a.dp(4), 0, 0) }, lpMW())
                c.addView(a.gap(14))
                c.addView(a.button("✓  Valider la série", C.PEACH) { validate(reps) }, lpMW())
            } else {
                val v = a.txt(D.dur(Runner.chronoMs(st) / 1000L), 56f, C.TEXT, Typeface.BOLD)
                v.gravity = Gravity.CENTER
                v.typeface = Typeface.create("serif", Typeface.BOLD)
                bigText = v
                c.addView(v, lpMW())
                if (last.isNotEmpty()) c.addView(a.caption("Dernière fois : " + last.joinToString(" · ") { D.dur(it.toLong()) }).apply { gravity = Gravity.CENTER }, lpMW())
                c.addView(a.gap(14))
                if (!st.chronoOn) {
                    c.addView(a.button("▶  Démarrer", C.ORCHID) {
                        val x = s ?: return@button
                        if (x.paused) Runner.resume(a, x)
                        x.chronoOn = true
                        x.chronoAcc = 0L
                        x.chronoStart = System.currentTimeMillis()
                        Runner.save(a, x)
                        refresh()
                    }, lpMW())
                } else {
                    c.addView(a.button("■  Stop", C.ROSE) {
                        val x = s ?: return@button
                        val sec = (Runner.chronoMs(x) / 1000L).toInt()
                        x.chronoOn = false
                        x.chronoAcc = 0L
                        validate(sec.coerceAtLeast(1))
                    }, lpMW())
                }
            }
            b.addView(c)
        } else {
            val betweenSets = st.phase == Runner.REST_SET
            val col = if (betweenSets) C.SKY else C.AMBER
            val c = a.card(soft(col, 0.9f), soft(col, 0.6f))
            c.addView(a.txt(if (betweenSets) "Repos entre les séries" else "Repos avant l'exercice suivant", 16f, col, Typeface.BOLD))
            val v = a.txt(D.dur((Runner.restLeftMs(st) + 999L) / 1000L), 60f, C.TEXT, Typeface.BOLD)
            v.gravity = Gravity.CENTER
            v.typeface = Typeface.create("serif", Typeface.BOLD)
            bigText = v
            c.addView(v, lpMW())
            val bar = a.hbox()
            bar.background = shape(soft(col, 0.75f), a.dpf(5f))
            val fill = View(a)
            fill.background = shape(col, a.dpf(5f))
            val rest = View(a)
            bar.addView(fill, LinearLayout.LayoutParams(0, a.dp(8), 0.5f))
            bar.addView(rest, LinearLayout.LayoutParams(0, a.dp(8), 0.5f))
            barFill = fill
            barRest = rest
            c.addView(bar, lpMW().apply { topMargin = a.dp(6); bottomMargin = a.dp(10) })
            c.addView(a.txt("Prochain : " + Runner.nextLabel(a, st), 14.5f, C.TEXT).apply { gravity = Gravity.CENTER }, lpMW())
            c.addView(a.gap(14))
            val r = a.hbox()
            r.addView(a.button("−15 s", col, filled = false) { addRest(-15_000L) }, lpW(1f))
            r.addView(a.button(if (st.paused) "▶" else "⏸", col, filled = false) { togglePause() }, lpW(0.7f).apply { leftMargin = a.dp(6) })
            r.addView(a.button("+15 s", col, filled = false) { addRest(15_000L) }, lpW(1f).apply { leftMargin = a.dp(6) })
            c.addView(r, lpMW())
            c.addView(a.gap(10))
            c.addView(a.button("Passer le repos  ⏭", col) { skipRest() }, lpMW())
            b.addView(c)
            updateBar(st)
        }

        // ---- Séries faites sur l'exercice courant ----
        val doneHere = st.done[st.ex]
        if (doneHere.isNotEmpty()) {
            val c = a.card()
            c.addView(a.heading("Séries faites · $exName"))
            c.addView(a.caption("Touche une série pour la corriger"))
            val row = a.hbox()
            row.setPadding(0, a.dp(8), 0, 0)
            doneHere.forEachIndexed { i, v ->
                val chip = a.smallButton("S${i + 1} · ${Fit.fmt(v, timed)}", C.PEACH) {
                    a.askNumber(if (timed) "Série ${i + 1} (secondes)" else "Série ${i + 1} (répétitions)", v) { n ->
                        val x = s ?: return@askNumber
                        if (i < x.done[x.ex].size) x.done[x.ex][i] = n
                        Runner.save(a, x)
                        refresh()
                    }
                }
                row.addView(chip, lpWW().apply { rightMargin = a.dp(6) })
            }
            val hs = android.widget.HorizontalScrollView(a)
            hs.isHorizontalScrollBarEnabled = false
            hs.addView(row)
            c.addView(hs, lpMW())
            b.addView(c)
        }

        // ---- Vue d'ensemble ----
        val ov = a.card()
        ov.addView(a.heading("Programme"))
        st.items.forEachIndexed { i, itx ->
            val e = exMap[itx.exId]
            val n = st.done[i].size
            val r = a.hbox()
            r.setPadding(0, a.dp(9), 0, a.dp(9))
            val mark = when {
                n >= itx.sets -> "✓"
                i == st.ex -> "▶"
                else -> "○"
            }
            r.addView(a.txt(mark, 15f, if (n >= itx.sets) C.MINT else if (i == st.ex) C.PEACH else C.MUTED), LinearLayout.LayoutParams(a.dp(26), WRAP))
            r.addView(a.txt(e?.name ?: "Exercice", 15f, C.TEXT, if (i == st.ex) Typeface.BOLD else Typeface.NORMAL), lpW(1f))
            r.addView(a.caption("$n/${itx.sets}"))
            r.ripple(a.dpf(10f))
            r.setOnClickListener {
                val x = s ?: return@setOnClickListener
                Runner.goTo(a, x, i, x.done[i].size)
                Runner.save(a, x)
                refresh()
            }
            ov.addView(r)
        }
        b.addView(ov)
        b.addView(a.button("Terminer la séance", C.MINT) {
            a.confirm("Terminer la séance ?", "Les séries faites seront enregistrées dans ton historique.", "Terminer") { finish() }
        }, lpMW())
        lastPhaseKey = phaseKey(st)
    }

    private fun phaseKey(st: Runner.State) = "${st.phase}:${st.ex}:${st.setIdx}:${st.paused}:${st.chronoOn}"

    private fun updateBar(st: Runner.State) {
        val f = barFill ?: return
        val r = barRest ?: return
        val total = st.restTotal.coerceAtLeast(1L).toFloat()
        val left = Runner.restLeftMs(st).toFloat()
        val frac = (left / total).coerceIn(0.001f, 0.999f)
        (f.layoutParams as LinearLayout.LayoutParams).weight = frac
        (r.layoutParams as LinearLayout.LayoutParams).weight = 1f - frac
        f.requestLayout()
    }

    override fun tick() {
        val st = s ?: return
        elapsedText?.text = D.dur(Runner.elapsedSec(st))
        if (st.phase != Runner.WORK) {
            if (!st.paused && System.currentTimeMillis() >= st.restEnd) {
                Runner.endRest(a, st)
                Runner.save(a, st)
                signal()
                refresh()
                return
            }
            bigText?.text = D.dur((Runner.restLeftMs(st) + 999L) / 1000L)
            updateBar(st)
        } else if (st.chronoOn) {
            if (curTimed) bigText?.text = D.dur(Runner.chronoMs(st) / 1000L)
        }
    }

    private fun signal() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 800L)
        } catch (t: Throwable) {
        }
        try {
            val v = a.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(350L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (t: Throwable) {
        }
    }

    private fun validate(v: Int) {
        val st = s ?: return
        if (st.paused) Runner.resume(a, st)
        Runner.record(st, v)
        val finished = Runner.advance(a, st)
        Runner.save(a, st)
        if (finished) {
            finish()
        } else {
            refresh()
        }
    }

    private fun togglePause() {
        val st = s ?: return
        if (st.paused) Runner.resume(a, st) else Runner.pause(a, st)
        Runner.save(a, st)
        refresh()
    }

    private fun addRest(ms: Long) {
        val st = s ?: return
        Runner.addRest(a, st, ms)
        Runner.save(a, st)
        tick()
    }

    private fun skipRest() {
        val st = s ?: return
        Runner.endRest(a, st)
        Runner.save(a, st)
        refresh()
    }

    private fun previous() {
        val st = s ?: return
        when {
            st.phase != Runner.WORK -> {
                // Pendant un repos : on revient à la série qui vient d'être faite
                if (st.setIdx > 0) Runner.goTo(a, st, st.ex, st.setIdx - 1)
                else if (st.ex > 0) Runner.goTo(a, st, st.ex - 1, st.items[st.ex - 1].sets - 1)
                else Runner.goTo(a, st, 0, 0)
            }
            st.setIdx > 0 -> Runner.goTo(a, st, st.ex, st.setIdx - 1)
            st.ex > 0 -> Runner.goTo(a, st, st.ex - 1, st.items[st.ex - 1].sets - 1)
            else -> {
                a.toast("Tu es déjà à la première série")
                return
            }
        }
        repsKey = ""
        Runner.save(a, st)
        refresh()
    }

    private fun nextExercise() {
        val st = s ?: return
        if (st.ex >= st.items.size - 1) {
            a.confirm("C'est le dernier exercice", "Terminer la séance maintenant ?", "Terminer") { finish() }
            return
        }
        Runner.goTo(a, st, st.ex + 1, 0)
        Runner.save(a, st)
        refresh()
    }

    private fun abandon() {
        val st = s ?: return
        if (Runner.doneSets(st) == 0) {
            a.confirm("Abandonner la séance ?", "Aucune série n'a encore été validée.", "Abandonner") {
                Runner.clear(a)
                a.pop()
            }
            return
        }
        AlertDialog.Builder(a).setTitle("Abandonner la séance ?")
            .setMessage("Tu as déjà validé ${Runner.doneSets(st)} série(s). Tu les gardes dans ton historique ?")
            .setPositiveButton("Garder les séries") { _, _ -> finish() }
            .setNegativeButton("Supprimer") { _, _ ->
                Runner.finish(a, st, false)
                a.pop()
            }
            .setNeutralButton("Continuer", null)
            .show()
    }

    private fun finish() {
        val st = s ?: return
        val sess = Runner.finish(a, st, true)
        s = null
        a.pop()
        if (sess != null) a.push(SummaryScreen(a, sess.id, true)) else a.toast("Aucune série enregistrée")
    }

    override fun onBack(): Boolean = false
}

/** Résumé d'une séance (fin de séance ou depuis l'historique). */
class SummaryScreen(private val a: MainActivity, private val sessionId: Long, private val fresh: Boolean) : Screen {
    private val root = a.vbox()
    private val page = Page(a)
    override val view: View get() = root

    override fun refresh() {
        root.removeAllViews()
        val sess = Fit.sessions(a).firstOrNull { it.id == sessionId }
        if (sess == null) {
            a.pop()
            return
        }
        root.addView(a.topBar("Résumé", C.ORCHID, { a.pop() }), lpMW())
        root.addView(page.scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val b = page.body
        b.removeAllViews()
        val exMap = Fit.exMap(a)

        val hero = a.vbox()
        hero.background = gradient(C.PEACH, C.ORCHID, a.dpf(24f))
        hero.setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18))
        if (fresh) hero.addView(a.txt("Bravo ! 🎉", 26f, Color.WHITE, Typeface.BOLD))
        hero.addView(a.txt(sess.name, 19f, Color.WHITE, Typeface.BOLD))
        hero.addView(a.txt(D.cap(D.longY(D.dayOf(sess.start))) + " · " + D.time(sess.start), 14f, Color.WHITE))
        hero.addView(a.gap(14))
        val stats = a.hbox()
        val totalSets = sess.exs.sumOf { it.sets.size }
        for ((v, l) in listOf(D.minutes(sess.activeSec) to "durée", "${sess.exs.size}" to "exercices", "$totalSets" to "séries")) {
            val col = a.vbox()
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.addView(a.txt(v, 22f, Color.WHITE, Typeface.BOLD))
            col.addView(a.txt(l, 12.5f, Color.WHITE))
            stats.addView(col, lpW(1f))
        }
        hero.addView(stats, lpMW())
        b.addView(hero, lpMW().apply { bottomMargin = a.dp(12) })

        for (e in sess.exs) {
            val ex = exMap[e.exId]
            val timed = ex?.timed ?: false
            val c = a.card()
            c.addView(a.txt(ex?.name ?: "Exercice supprimé", 16f, C.TEXT, Typeface.BOLD))
            c.addView(a.txt(e.sets.mapIndexed { i, v -> "S${i + 1} ${Fit.fmt(v, timed)}" }.joinToString("  ·  "), 14.5f, C.PEACH).apply { setPadding(0, a.dp(4), 0, 0) })
            val total = e.sets.sum()
            c.addView(a.caption(if (timed) "Total ${D.dur(total.toLong())}" else "Total $total répétitions"))
            b.addView(c)
        }
        if (!fresh) {
            val del = a.caption("Supprimer cette séance de l'historique")
            del.setTextColor(C.ROSE)
            del.gravity = Gravity.CENTER
            del.setPadding(0, a.dp(12), 0, a.dp(12))
            del.setOnClickListener {
                a.confirm("Supprimer cette séance ?", "Elle disparaîtra de l'historique et des courbes.", "Supprimer") {
                    Fit.deleteSession(a, sess.id)
                    a.pop()
                }
            }
            b.addView(del, lpMW())
        } else {
            b.addView(a.button("Voir mes progrès 📈", C.ORCHID) { a.go(MainActivity.ACTIV, 1) }, lpMW())
        }
    }
}
