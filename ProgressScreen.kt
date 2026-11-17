package com.suisme.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import kotlin.math.max

/** Onglet « Progrès » : courbes par exercice + historique des séances. */
class ProgressScreen(private val a: MainActivity) : Screen {
    private val page = Page(a)
    override val view: View get() = page.scroll

    override fun refresh() {
        val b = page.body
        b.removeAllViews()
        val p = a.prefs()
        val sessions = Fit.sessions(a)
        val exMap = Fit.exMap(a)

        if (sessions.isEmpty()) {
            val c = a.card()
            c.addView(a.heading("Tes progrès apparaîtront ici ✨"))
            c.addView(a.txt("Termine ta première séance pour voir tes courbes, tes records et ton historique.", 14.5f).apply { setPadding(0, a.dp(6), 0, 0) })
            b.addView(c)
            return
        }

        // Exercices ayant un historique (les plus récents d'abord)
        val ids = ArrayList<Long>()
        for (s in sessions.reversed()) for (e in s.exs) if (!ids.contains(e.exId) && exMap.containsKey(e.exId)) ids.add(e.exId)

        if (ids.isNotEmpty()) {
            var exId = p.getLong("prog_ex", ids[0])
            if (!ids.contains(exId)) exId = ids[0]
            val mode = p.getInt("prog_mode", 0)
            val period = p.getInt("prog_period", 1)
            val ex = exMap[exId]!!
            val timed = ex.timed

            b.addView(a.caption("Exercice").apply { setPadding(a.dp(4), 0, 0, a.dp(6)) })
            b.addView(a.chips(ids.map { exMap[it]?.name ?: "?" }, ids.indexOf(exId), C.ORCHID) { i ->
                p.edit().putLong("prog_ex", ids[i]).apply()
                refresh()
            })
            b.addView(a.gap(12))

            val days = when (period) {
                0 -> 7L
                1 -> 30L
                2 -> 90L
                else -> 100000L
            }
            val from = System.currentTimeMillis() - days * 24L * 3600_000L
            val all = sessions.mapNotNull { s ->
                val e = s.exs.firstOrNull { it.exId == exId && it.sets.isNotEmpty() }
                if (e == null) null else s to e.sets
            }
            val pts = all.filter { it.first.start >= from }

            // ---- Chiffres clés ----
            val best = all.maxOfOrNull { it.second.maxOrNull() ?: 0 } ?: 0
            val lastTot = all.lastOrNull()?.second?.sum() ?: 0
            val prevTot = if (all.size >= 2) all[all.size - 2].second.sum() else null
            val row = a.hbox()
            row.addView(stat("🏆 Record", Fit.fmt(best, timed) + if (timed) "" else " reps", C.ORCHID), lpW(1f))
            row.addView(stat("Dernière séance", Fit.fmt(lastTot, timed), C.PEACH), lpW(1f).apply { leftMargin = a.dp(8) })
            val diff = if (prevTot == null) "–" else {
                val d = lastTot - (prevTot ?: 0)
                (if (d > 0) "+" else if (d < 0) "−" else "") + Fit.fmt(kotlin.math.abs(d), timed)
            }
            val dcol = if (prevTot == null || lastTot == prevTot) C.MUTED else if (lastTot > (prevTot ?: 0)) C.MINT else C.ROSE
            row.addView(stat("Écart", diff, dcol), lpW(1f).apply { leftMargin = a.dp(8) })
            b.addView(row, lpMW().apply { bottomMargin = a.dp(12) })

            // ---- Courbe ----
            val c = a.card()
            c.addView(a.heading(ex.name))
            c.addView(a.gap(8))
            c.addView(a.chips(listOf("Par série", "Meilleure série", "Total"), mode, C.ORCHID) { i ->
                p.edit().putInt("prog_mode", i).apply()
                refresh()
            })
            c.addView(a.gap(6))
            c.addView(a.chips(listOf("7 j", "30 j", "90 j", "Tout"), period, C.LAVENDER) { i ->
                p.edit().putInt("prog_period", i).apply()
                refresh()
            })
            val chart = LineChart(a)
            chart.unit = if (timed) "s" else ""
            chart.xLabels = pts.map { D.num(D.dayOf(it.first.start)) }
            val series = ArrayList<LineChart.Series>()
            when (mode) {
                0 -> {
                    val maxSets = pts.maxOfOrNull { it.second.size } ?: 0
                    for (k in 0 until maxSets) {
                        val sp = ArrayList<Pair<Int, Float>>()
                        pts.forEachIndexed { i, pr -> pr.second.getOrNull(k)?.let { sp.add(i to it.toFloat()) } }
                        series.add(LineChart.Series("S${k + 1}", C.SERIES[k % C.SERIES.size], sp))
                    }
                }
                1 -> series.add(LineChart.Series("Meilleure", C.ORCHID, pts.mapIndexed { i, pr -> i to (pr.second.maxOrNull() ?: 0).toFloat() }))
                else -> series.add(LineChart.Series("Total", C.PEACH, pts.mapIndexed { i, pr -> i to pr.second.sum().toFloat() }))
            }
            chart.series = series
            c.addView(chart, lpMW().apply { topMargin = a.dp(12) })
            if (mode == 0 && series.size > 1) {
                val leg = a.hbox()
                leg.gravity = Gravity.CENTER
                for (s in series) {
                    val t = a.txt("● ${s.name}", 12.5f, s.color)
                    t.setPadding(a.dp(6), a.dp(4), a.dp(6), 0)
                    leg.addView(t)
                }
                c.addView(leg, lpMW())
            }
            if (pts.isEmpty()) c.addView(a.caption("Pas de séance sur cette période.").apply { gravity = Gravity.CENTER }, lpMW())
            b.addView(c)
        }

        // ---- Historique ----
        val h = a.card()
        h.addView(a.heading("Historique des séances"))
        h.addView(a.caption("Touche une séance pour voir le détail"))
        val recent = sessions.reversed().take(40)
        recent.forEachIndexed { i, s ->
            val r = a.hbox()
            r.setPadding(0, a.dp(11), 0, a.dp(11))
            val col = a.vbox()
            col.addView(a.txt(s.name, 15f, C.TEXT, Typeface.BOLD))
            col.addView(a.caption("${D.cap(D.longY(D.dayOf(s.start)))} · ${D.minutes(s.activeSec)} · ${s.exs.size} exo${if (s.exs.size > 1) "s" else ""}"))
            r.addView(col, lpW(1f))
            r.addView(a.txt("›", 22f, C.MUTED))
            r.ripple(a.dpf(10f))
            r.setOnClickListener { a.push(SummaryScreen(a, s.id, false)) }
            h.addView(r, lpMW())
            if (i < recent.size - 1) h.addView(View(a).apply { setBackgroundColor(C.LINE) }, LinearLayout.LayoutParams(MATCH, a.dp(1)))
        }
        b.addView(h)
    }

    private fun stat(label: String, value: String, color: Int): View {
        val c = a.vbox()
        c.background = shape(C.SURFACE, a.dpf(18f), a.dp(1), C.LINE)
        c.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
        c.addView(a.txt(label, 12f, C.MUTED))
        val v = a.txt(value, 19f, color, Typeface.BOLD)
        v.maxLines = 1
        c.addView(v)
        return c
    }
}

/** Courbe simple multi-séries. */
class LineChart(c: Context) : View(c) {
    class Series(val name: String, val color: Int, val pts: List<Pair<Int, Float>>)

    var series: List<Series> = emptyList()
    var xLabels: List<String> = emptyList()
    var unit = ""
    private val dens = c.resources.displayMetrics.density
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * c.resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C.LINE
        strokeWidth = 1f * c.resources.displayMetrics.density
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.MUTED }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (210 * dens).toInt())
    }

    private fun fmtY(v: Float): String =
        if (unit == "s") D.dur(v.toLong()) else if (v >= 100f || v == v.toInt().toFloat()) "${v.toInt()}" else String.format(java.util.Locale.FRENCH, "%.1f", v)

    override fun onDraw(cv: Canvas) {
        super.onDraw(cv)
        val n = xLabels.size
        val left = 40f * dens
        val right = width - 10f * dens
        val top = 10f * dens
        val bottom = height - 24f * dens
        val maxV = max(1f, series.flatMap { s -> s.pts.map { it.second } }.maxOrNull() ?: 1f) * 1.12f
        pText.textSize = 10.5f * dens
        pText.textAlign = Paint.Align.RIGHT
        for (k in 0..4) {
            val y = bottom - (bottom - top) * k / 4f
            cv.drawLine(left, y, right, y, pGrid)
            cv.drawText(fmtY(maxV * k / 4f), left - 6f * dens, y + 4f * dens, pText)
        }
        if (n == 0) return
        fun xOf(i: Int): Float = if (n == 1) (left + right) / 2f else left + (right - left) * i / (n - 1).toFloat()
        fun yOf(v: Float): Float = bottom - (bottom - top) * (v / maxV)
        pText.textAlign = Paint.Align.CENTER
        val idx = if (n <= 3) (0 until n).toList() else listOf(0, n / 2, n - 1)
        for (i in idx) cv.drawText(xLabels[i], xOf(i), height - 6f * dens, pText)
        for (s in series) {
            if (s.pts.isEmpty()) continue
            pLine.color = s.color
            pDot.color = s.color
            val path = Path()
            s.pts.forEachIndexed { j, pt ->
                val x = xOf(pt.first)
                val y = yOf(pt.second)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            cv.drawPath(path, pLine)
            for (pt in s.pts) cv.drawCircle(xOf(pt.first), yOf(pt.second), 3.6f * dens, pDot)
        }
    }
}
