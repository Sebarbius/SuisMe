package com.suisme.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CycleScreen(private val a: MainActivity) : Screen {
    private val page = Page(a)
    override val view: View get() = page.scroll
    private var selected = -1

    override fun refresh() {
        val b = page.body
        b.removeAllViews()
        val starts = Cycle.starts(a)
        val cur = Cycle.current(a)
        val avg = Cycle.average(a)
        val past = Cycle.past(a)

        // ---------- Carte principale : J-x ----------
        val hero = a.vbox()
        hero.background = gradient(Color.parseColor("#FF8FB1"), Color.parseColor("#B48CF2"), a.dpf(26f))
        hero.setPadding(a.dp(22), a.dp(20), a.dp(22), a.dp(20))
        hero.layoutParams = lpMW().apply { bottomMargin = a.dp(12) }
        if (cur == null) {
            hero.addView(a.txt("Bienvenue 🌸", 24f, Color.WHITE, Typeface.BOLD))
            val t = a.txt("Indique le premier jour de tes dernières règles pour commencer le suivi de ton cycle.", 15f, Color.WHITE)
            t.setPadding(0, a.dp(6), 0, a.dp(14))
            hero.addView(t)
        } else {
            val day = (D.today() - cur + 1).toInt()
            hero.addView(a.txt("Aujourd'hui", 14f, soft(Color.WHITE, 0f)))
            val row = a.hbox()
            row.gravity = Gravity.BOTTOM
            val big = a.txt("J$day", 56f, Color.WHITE, Typeface.BOLD)
            big.typeface = Typeface.create("serif", Typeface.BOLD)
            row.addView(big)
            val dc = a.txt("  du cycle", 18f, Color.WHITE)
            dc.setPadding(0, 0, 0, a.dp(12))
            row.addView(dc)
            hero.addView(row)
            hero.addView(a.txt("Début : " + D.long(cur), 14.5f, Color.WHITE))

            val ref = avg ?: 28.0
            val len = ref.roundToInt().coerceAtLeast(1)
            // Barre de progression du cycle
            val fill = View(a)
            fill.background = shape(Color.WHITE, a.dpf(6f))
            val frac = min(1f, day.toFloat() / len)
            val outer = a.hbox()
            outer.background = shape(Color.argb(70, 255, 255, 255), a.dpf(6f))
            outer.addView(fill, LinearLayout.LayoutParams(0, a.dp(10), max(0.02f, frac)))
            outer.addView(View(a), LinearLayout.LayoutParams(0, a.dp(10), max(0.0001f, 1f - frac)))
            val olp = lpMW()
            olp.topMargin = a.dp(14)
            olp.bottomMargin = a.dp(8)
            hero.addView(outer, olp)

            val next = cur + len
            val delta = next - D.today()
            val info = when {
                delta > 1 -> "Prochaines règles vers le ${D.long(next)} (dans $delta jours)"
                delta == 1L -> "Prochaines règles prévues demain"
                delta == 0L -> "Prochaines règles prévues aujourd'hui"
                else -> "${-delta} jour${if (-delta > 1) "s" else ""} de plus que ta durée moyenne"
            }
            hero.addView(a.txt(info + if (avg == null) " · estimation sur 28 j" else "", 13.5f, Color.WHITE))
            val sp = a.gap(14)
            hero.addView(sp)
        }
        val startBtn = a.button("🩸  Mes règles ont commencé", C.ROSE_DEEP, filled = false) { addStart() }
        startBtn.background = shape(Color.WHITE, a.dpf(26f))
        hero.addView(startBtn, lpMW())
        b.addView(hero)

        // ---------- Auto-palpation ----------
        if (cur != null) b.addView(palpCard(cur))

        // ---------- Durée moyenne ----------
        val stats = a.card()
        stats.addView(a.heading("Durée moyenne des cycles"))
        if (avg == null) {
            stats.addView(a.caption("Elle s'affichera dès que tu auras enregistré le début de deux cycles."))
        } else {
            val r = a.hbox()
            val v = a.txt(String.format(Locale.FRENCH, "%.1f", avg).replace(",0", ""), 40f, C.ROSE, Typeface.BOLD)
            v.typeface = Typeface.create("serif", Typeface.BOLD)
            r.addView(v)
            val u = a.txt("  jours", 17f, C.TEXT)
            u.setPadding(0, a.dp(10), 0, 0)
            r.addView(u)
            stats.addView(r)
            val mn = past.minOf { it.length }
            val mx = past.maxOf { it.length }
            stats.addView(a.caption("Sur ${past.size} cycle${if (past.size > 1) "s" else ""} · plus court $mn j · plus long $mx j"))
        }
        b.addView(stats)

        // ---------- Graphique ----------
        if (past.isNotEmpty()) {
            val ch = a.card()
            ch.addView(a.heading("Durée des cycles passés"))
            val shown = past.takeLast(12)
            val detail = a.caption("Touche une barre pour voir les dates")
            detail.gravity = Gravity.CENTER
            detail.setPadding(0, a.dp(6), 0, 0)
            val bars = CycleBars(a) { i ->
                selected = i
                val p = shown[i]
                detail.text = "Du ${D.longY(p.start)}\nau ${D.longY(p.end)} · ${p.length} jours"
                detail.setTextColor(C.TEXT)
            }
            bars.data = shown
            bars.avg = avg
            if (selected in shown.indices) {
                bars.selected = selected
                val p = shown[selected]
                detail.text = "Du ${D.longY(p.start)}\nau ${D.longY(p.end)} · ${p.length} jours"
                detail.setTextColor(C.TEXT)
            }
            val blp = lpMW()
            blp.topMargin = a.dp(10)
            ch.addView(bars, blp)
            ch.addView(detail, lpMW())
            if (past.size > 12) ch.addView(a.caption("12 derniers cycles affichés"))
            b.addView(ch)
        }

        // ---------- Historique ----------
        if (starts.isNotEmpty()) {
            val h = a.card()
            h.addView(a.heading("Historique des règles"))
            h.addView(a.caption("Touche une ligne pour la modifier ou la supprimer"))
            val l = starts.filter { it <= D.today() }
            for (i in l.indices.reversed()) {
                val s = l[i]
                val row = a.hbox()
                row.setPadding(0, a.dp(12), 0, a.dp(12))
                val dot = a.txt("●", 12f, if (i == l.size - 1) C.ROSE else soft(C.ROSE, 0.5f))
                row.addView(dot)
                val col = a.vbox()
                col.setPadding(a.dp(12), 0, 0, 0)
                if (i == l.size - 1) {
                    col.addView(a.txt("En cours · depuis le ${D.medium(s)}", 15f, C.TEXT, Typeface.BOLD))
                    col.addView(a.caption("J${D.today() - s + 1}"))
                } else {
                    val end = l[i + 1] - 1
                    col.addView(a.txt("${D.short(s)} → ${D.medium(end)}", 15f, C.TEXT))
                    col.addView(a.caption("${l[i + 1] - s} jours"))
                }
                row.addView(col, lpW(1f))
                if (Cycle.palpDone(a, s) != null) row.addView(a.txt("🤍 ✓", 13f, C.MINT))
                row.ripple(a.dpf(12f))
                row.setOnClickListener { editStart(s) }
                h.addView(row, lpMW())
                if (i > 0) h.addView(View(a).apply { setBackgroundColor(C.LINE) }, LinearLayout.LayoutParams(MATCH, a.dp(1)))
            }
            b.addView(h)
        }
    }

    private fun palpCard(cur: Long): View {
        val pd = Cycle.palpDay(cur)
        val done = Cycle.palpDone(a, cur)
        val due = D.today() >= pd && done == null
        val c = if (due) a.card(soft(C.ROSE, 0.92f), soft(C.ROSE, 0.55f)) else a.card()
        val top = a.hbox()
        top.addView(a.txt("🤍", 22f))
        val tt = a.heading(if (done != null) "Auto-palpation faite" else if (due) "Auto-palpation à faire" else "Auto-palpation")
        tt.setPadding(a.dp(10), 0, 0, 0)
        top.addView(tt, lpW(1f))
        c.addView(top)
        val hm = D.hm(Cycle.remindHour(a), Cycle.remindMinute(a))
        val msg = when {
            done != null -> "Validée le ${D.long(done)}. Prochaine au cycle suivant, 7 jours après le début des règles."
            due -> "Prévue depuis le ${D.long(pd)}. Tu reçois un rappel chaque jour à $hm tant qu'elle n'est pas validée."
            else -> "Prévue le ${D.long(pd)} (J${pd - cur + 1}), 7 jours après le début des règles. Rappel à $hm."
        }
        val m = a.txt(msg, 14f, C.TEXT)
        m.setPadding(0, a.dp(6), 0, a.dp(12))
        c.addView(m)
        val row = a.hbox()
        if (done == null) {
            row.addView(a.button(if (due) "✓  C'est fait" else "Déjà faite", C.ROSE, filled = due) {
                Cycle.setPalpDone(a, cur, D.today())
                Notifier.cancelPalpation(a)
                a.toast("Bravo, c'est noté 🤍")
                refresh()
            }, lpW(1f))
        } else {
            row.addView(a.smallButton("Annuler la validation", C.MUTED) {
                Cycle.setPalpDone(a, cur, null)
                Reminders.schedule(a)
                refresh()
            })
            row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        }
        val bell = a.smallButton("🔔 $hm", C.LAVENDER) {
            a.pickTime(Cycle.remindHour(a), Cycle.remindMinute(a)) { h, mi ->
                Cycle.setRemind(a, h, mi)
                a.prefs().edit().remove("palp_notified").apply()
                Reminders.schedule(a)
                refresh()
            }
        }
        val blp = lpWW()
        blp.leftMargin = a.dp(10)
        row.addView(bell, blp)
        c.addView(row, lpMW())
        return c
    }

    private fun addStart() {
        a.pickDate(D.today(), maxToday = true) { day ->
            when {
                day > D.today() -> a.toast("Choisis une date passée")
                !Cycle.add(a, day) -> a.toast("Cette date est déjà enregistrée")
                else -> {
                    a.toast("C'est noté 🌸")
                    selected = -1
                    Reminders.check(a)
                    refresh()
                }
            }
        }
    }

    private fun editStart(day: Long) {
        a.choose(D.cap(D.long(day)), listOf("Modifier la date de début", "Supprimer")) { i ->
            if (i == 0) {
                a.pickDate(day, maxToday = true) { nd ->
                    if (!Cycle.move(a, day, nd)) a.toast("Cette date est déjà enregistrée")
                    selected = -1
                    Reminders.schedule(a)
                    refresh()
                }
            } else {
                a.confirm("Supprimer ?", "Le début de règles du ${D.medium(day)} sera retiré de l'historique.", "Supprimer") {
                    Cycle.remove(a, day)
                    selected = -1
                    Reminders.schedule(a)
                    refresh()
                }
            }
        }
    }
}

/** Graphique en bâtonnets : durée de chaque cycle passé. */
class CycleBars(c: Context, private val onSelect: (Int) -> Unit) : View(c) {
    var data: List<Cycle.Past> = emptyList()
    var avg: Double? = null
    var selected = -1
    private val dens = c.resources.displayMetrics.density
    private val pBar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * c.resources.displayMetrics.density
        color = C.LAVENDER
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val hits = ArrayList<RectF>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (220 * dens).toInt())
    }

    override fun onDraw(cv: Canvas) {
        super.onDraw(cv)
        hits.clear()
        if (data.isEmpty()) return
        val top = 28f * dens
        val bottom = height - 26f * dens
        val left = 4f * dens
        val right = width - 4f * dens
        val maxLen = max(data.maxOf { it.length }.toDouble(), avg ?: 0.0)
        val maxV = (maxLen * 1.12).toFloat().coerceAtLeast(1f)
        val n = data.size
        val slot = (right - left) / n
        val bw = min(slot * 0.62f, 34f * dens)
        for (i in 0 until n) {
            val p = data[i]
            val cx = left + slot * (i + 0.5f)
            val h = p.length / maxV * (bottom - top)
            val r = RectF(cx - bw / 2f, bottom - h, cx + bw / 2f, bottom)
            val on = i == selected
            val c1 = if (on) C.ROSE_DEEP else Color.parseColor("#FF9DBC")
            val c2 = if (on) C.ORCHID else Color.parseColor("#C9A6F5")
            pBar.shader = LinearGradient(0f, r.top, 0f, r.bottom, c1, c2, Shader.TileMode.CLAMP)
            cv.drawRoundRect(r, bw / 2.4f, bw / 2.4f, pBar)
            pText.color = if (on) C.ROSE_DEEP else C.TEXT
            pText.textSize = 12.5f * dens
            pText.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            cv.drawText("${p.length}", cx, r.top - 6f * dens, pText)
            if (n <= 8 || i % 2 == (n - 1) % 2) {
                pText.color = C.MUTED
                pText.textSize = 10.5f * dens
                pText.typeface = Typeface.DEFAULT
                cv.drawText(D.num(p.start), cx, bottom + 17f * dens, pText)
            }
            hits.add(RectF(cx - slot / 2f, 0f, cx + slot / 2f, height.toFloat()))
        }
        val av = avg
        if (av != null) {
            val y = bottom - (av.toFloat() / maxV) * (bottom - top)
            cv.drawLine(left, y, right, y, pLine)
            pText.color = C.LAVENDER
            pText.textSize = 11f * dens
            pText.textAlign = Paint.Align.RIGHT
            cv.drawText("moy. " + String.format(Locale.FRENCH, "%.1f", av).replace(",0", "") + " j", right, y - 5f * dens, pText)
            pText.textAlign = Paint.Align.CENTER
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val i = hits.indexOfFirst { it.contains(event.x, event.y) }
            if (i >= 0) {
                selected = i
                invalidate()
                onSelect(i)
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
