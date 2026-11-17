package com.suisme.app

import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Onglet « Suivi médical » : liste des domaines + prochains rendez-vous. */
class MedicalScreen(private val a: MainActivity) : Screen {
    private val page = Page(a)
    override val view: View get() = page.scroll

    override fun refresh() {
        val b = page.body
        b.removeAllViews()
        val list = Med.load(a)
        val now = System.currentTimeMillis()

        // Prochains rendez-vous, tous domaines confondus
        val upcoming = ArrayList<Pair<Domain, Rdv>>()
        for (d in list) for (r in d.rdvs) if (r.at >= now - 3600_000L) upcoming.add(d to r)
        upcoming.sortBy { it.second.at }
        if (upcoming.isNotEmpty()) {
            val c = a.card(soft(C.LAVENDER, 0.9f), soft(C.LAVENDER, 0.6f))
            c.addView(a.heading("📅  Prochains rendez-vous"))
            for ((d, r) in upcoming.take(4)) {
                val row = a.vbox()
                row.setPadding(0, a.dp(10), 0, 0)
                row.addView(a.txt("${d.emoji}  ${r.title}", 15f, C.TEXT, Typeface.BOLD))
                row.addView(a.caption("${d.name} · ${D.cap(D.dateTime(r.at))}"))
                row.setOnClickListener { a.push(DomainScreen(a, d.id)) }
                c.addView(row)
            }
            b.addView(c)
        }

        val head = a.hbox()
        head.setPadding(a.dp(4), a.dp(4), 0, a.dp(10))
        head.addView(a.heading("Mes domaines"), lpW(1f))
        head.addView(a.smallButton("＋ Domaine", C.LAVENDER) { editDomain(null) })
        b.addView(head, lpMW())

        if (list.isEmpty()) {
            val c = a.card()
            c.addView(a.txt("Crée un domaine pour chaque suivi (gynéco, dentiste…). Tu pourras y prendre des notes et programmer des rappels de rendez-vous.", 14.5f))
            c.addView(a.gap(12))
            c.addView(a.caption("Suggestions :"))
            c.addView(a.gap(6))
            val labels = Med.SUGGESTIONS.map { "${it.second} ${it.first}" }
            c.addView(a.chips(labels, -1, C.LAVENDER) { i ->
                val (n, e) = Med.SUGGESTIONS[i]
                val l = Med.load(a)
                l.add(Domain(newId(), n, e, C.SERIES[l.size % C.SERIES.size], ArrayList(), ArrayList()))
                Med.save(a, l)
                refresh()
            })
            b.addView(c)
            return
        }

        for (d in list) {
            val c = a.card()
            val row = a.hbox()
            val ic = a.txt(d.emoji, 24f)
            ic.gravity = Gravity.CENTER
            ic.background = shape(soft(d.color, 0.85f), a.dpf(16f))
            row.addView(ic, LinearLayout.LayoutParams(a.dp(48), a.dp(48)))
            val col = a.vbox()
            col.setPadding(a.dp(14), 0, 0, 0)
            col.addView(a.txt(d.name, 16.5f, C.TEXT, Typeface.BOLD))
            val next = d.rdvs.filter { it.at >= now }.minByOrNull { it.at }
            col.addView(a.caption(if (next != null) "Prochain RDV : ${D.short(D.dayOf(next.at))} à ${D.time(next.at)}" else "Aucun rendez-vous prévu"))
            val n = d.notes.size
            col.addView(a.caption(if (n == 0) "Pas encore de note" else "$n note${if (n > 1) "s" else ""}"))
            row.addView(col, lpW(1f))
            row.addView(a.txt("›", 24f, C.MUTED))
            c.addView(row)
            c.ripple(a.dpf(22f))
            c.setOnClickListener { a.push(DomainScreen(a, d.id)) }
            c.setOnLongClickListener {
                editDomain(d)
                true
            }
            b.addView(c)
        }
        b.addView(a.caption("Appui long sur un domaine pour le renommer ou le supprimer.").apply { gravity = Gravity.CENTER })
    }

    private fun editDomain(d: Domain?) {
        a.domainDialog(d) { refresh() }
    }
}

/** Dialogue de création / modification d'un domaine (nom + emoji). */
fun MainActivity.domainDialog(d: Domain?, onDone: () -> Unit) {
    val a = this
    val box = dialogBox()
    val name = edit("Nom du domaine (ex. Gynécologie)", d?.name ?: "")
    box.addView(name, lpMW())
    box.addView(gap(14))
    box.addView(caption("Icône"))
    var emoji = d?.emoji ?: Med.EMOJIS[0]
    val grid = vbox()
    val views = ArrayList<TextView>()
    var row: LinearLayout? = null
    Med.EMOJIS.forEachIndexed { i, e ->
        if (i % 7 == 0) {
            row = hbox()
            grid.addView(row, lpMW())
        }
        val t = txt(e, 22f)
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(6), 0, dp(6))
        views.add(t)
        t.setOnClickListener {
            emoji = e
            for (v in views) v.background = null
            t.background = shape(soft(C.LAVENDER, 0.8f), dpf(12f))
        }
        if (e == emoji) t.background = shape(soft(C.LAVENDER, 0.8f), dpf(12f))
        row?.addView(t, lpW(1f))
    }
    val glp = lpMW()
    glp.topMargin = dp(6)
    box.addView(grid, glp)

    val bld = AlertDialog.Builder(this)
        .setTitle(if (d == null) "Nouveau domaine" else "Modifier le domaine")
        .setView(box)
        .setPositiveButton("Enregistrer") { _, _ ->
            val n = name.text.toString().trim()
            if (n.isEmpty()) {
                a.toast("Donne un nom au domaine")
                return@setPositiveButton
            }
            val l = Med.load(a)
            if (d == null) {
                l.add(Domain(newId(), n, emoji, C.SERIES[l.size % C.SERIES.size], ArrayList(), ArrayList()))
            } else {
                val x = l.firstOrNull { it.id == d.id }
                if (x != null) {
                    x.name = n
                    x.emoji = emoji
                }
            }
            Med.save(a, l)
            onDone()
        }
        .setNegativeButton("Annuler", null)
    if (d != null) {
        bld.setNeutralButton("Supprimer") { _, _ ->
            a.confirm("Supprimer « ${d.name} » ?", "Les notes et rendez-vous de ce domaine seront effacés.", "Supprimer") {
                Med.save(a, Med.load(a).filter { it.id != d.id })
                Reminders.schedule(a)
                a.pop()
                onDone()
            }
        }
    }
    bld.show()
}

/** Détail d'un domaine : rendez-vous / rappels et notes. */
class DomainScreen(private val a: MainActivity, private val domainId: Long) : Screen {
    private val root = a.vbox()
    private val page = Page(a)
    override val view: View get() = root

    override fun refresh() {
        root.removeAllViews()
        val d = Med.load(a).firstOrNull { it.id == domainId }
        if (d == null) {
            a.pop()
            return
        }
        val edit = a.smallButton("✎", d.color) { a.domainDialog(d) { refresh() } }
        root.addView(a.topBar("${d.emoji}  ${d.name}", d.color, { a.pop() }, edit), lpMW())
        root.addView(page.scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val b = page.body
        b.removeAllViews()
        val now = System.currentTimeMillis()

        // ---------- Rendez-vous & rappels ----------
        val rc = a.card()
        val rh = a.hbox()
        rh.addView(a.heading("📅  Rendez-vous & rappels"), lpW(1f))
        rc.addView(rh)
        val upcoming = d.rdvs.filter { it.at >= now - 3600_000L }.sortedBy { it.at }
        val past = d.rdvs.filter { it.at < now - 3600_000L }.sortedByDescending { it.at }
        if (upcoming.isEmpty()) rc.addView(a.caption("Aucun rendez-vous à venir.").apply { setPadding(0, a.dp(6), 0, 0) })
        for (r in upcoming) rc.addView(rdvRow(d, r, false))
        rc.addView(a.gap(12))
        rc.addView(a.button("＋  Ajouter un rendez-vous", d.color) { rdvDialog(d, null) }, lpMW())
        if (past.isNotEmpty()) {
            rc.addView(a.divider())
            rc.addView(a.caption("Passés"))
            for (r in past.take(10)) rc.addView(rdvRow(d, r, true))
        }
        b.addView(rc)

        // ---------- Notes ----------
        val nc = a.card()
        nc.addView(a.heading("📝  Notes"))
        val notes = d.notes.sortedWith(compareByDescending<Note> { it.day }.thenByDescending { it.id })
        if (notes.isEmpty()) nc.addView(a.caption("Résultats, questions à poser, traitements… note ce qui compte.").apply { setPadding(0, a.dp(6), 0, 0) })
        for (n in notes) {
            val box = a.vbox()
            box.background = shape(C.SURFACE2, a.dpf(16f))
            box.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(12))
            box.addView(a.txt(D.cap(D.longY(n.day)), 12.5f, d.color, Typeface.BOLD))
            box.addView(a.txt(n.text, 15f).apply { setPadding(0, a.dp(4), 0, 0) })
            box.ripple(a.dpf(16f))
            box.setOnClickListener { noteDialog(d, n) }
            val lp = lpMW()
            lp.topMargin = a.dp(10)
            nc.addView(box, lp)
        }
        nc.addView(a.gap(12))
        nc.addView(a.button("＋  Ajouter une note", d.color, filled = false) { noteDialog(d, null) }, lpMW())
        b.addView(nc)
    }

    private fun rdvRow(d: Domain, r: Rdv, past: Boolean): View {
        val row = a.vbox()
        row.setPadding(0, a.dp(12), 0, a.dp(4))
        row.addView(a.txt(r.title, 15.5f, if (past) C.MUTED else C.TEXT, Typeface.BOLD))
        row.addView(a.txt(D.cap(D.dateTime(r.at)), 14f, if (past) C.MUTED else d.color))
        if (r.info.isNotBlank()) row.addView(a.caption(r.info))
        if (!past) {
            val rem = when {
                r.remind <= 0L -> "🔕 Pas de rappel"
                r.notified -> "🔔 Rappel envoyé"
                else -> "🔔 Rappel le ${D.short(D.dayOf(r.remind))} à ${D.time(r.remind)}"
            }
            row.addView(a.caption(rem))
        }
        row.ripple(a.dpf(12f))
        row.setOnClickListener { rdvDialog(d, r) }
        return row
    }

    private fun noteDialog(d: Domain, n: Note?) {
        val box = a.dialogBox()
        var day = n?.day ?: D.today()
        val dateBtn = a.smallButton("📅 " + D.cap(D.longY(day)), d.color) {}
        dateBtn.setOnClickListener {
            a.pickDate(day) { nd ->
                day = nd
                dateBtn.text = "📅 " + D.cap(D.longY(day))
            }
        }
        box.addView(dateBtn, lpWW())
        box.addView(a.gap(10))
        val e = a.edit("Ta note…", n?.text ?: "", multi = true)
        box.addView(e, lpMW())
        val bld = AlertDialog.Builder(a).setTitle(if (n == null) "Nouvelle note" else "Modifier la note").setView(box)
            .setPositiveButton("Enregistrer") { _, _ ->
                val t = e.text.toString().trim()
                if (t.isEmpty()) return@setPositiveButton
                val l = Med.load(a)
                val x = l.firstOrNull { it.id == d.id } ?: return@setPositiveButton
                if (n == null) {
                    x.notes.add(Note(newId(), day, t))
                } else {
                    val y = x.notes.firstOrNull { it.id == n.id }
                    if (y != null) {
                        y.text = t
                        y.day = day
                    }
                }
                Med.save(a, l)
                refresh()
            }
            .setNegativeButton("Annuler", null)
        if (n != null) {
            bld.setNeutralButton("Supprimer") { _, _ ->
                a.confirm("Supprimer cette note ?", n.text.take(80), "Supprimer") {
                    val l = Med.load(a)
                    l.firstOrNull { it.id == d.id }?.notes?.removeAll { it.id == n.id }
                    Med.save(a, l)
                    refresh()
                }
            }
        }
        bld.show()
    }

    private fun rdvDialog(d: Domain, r: Rdv?) {
        val box = a.dialogBox()
        val title = a.edit("Objet (ex. Contrôle annuel, échographie…)", r?.title ?: "")
        box.addView(title, lpMW())
        box.addView(a.gap(12))

        val init = if (r != null) D.ldt(r.at) else java.time.LocalDate.now().plusDays(7).atTime(9, 0)
        var day = init.toLocalDate().toEpochDay()
        var hour = init.hour
        var minute = init.minute
        // Choix du rappel : 0 aucun, 1 veille 18h, 2 2h avant, 3 une semaine avant, 4 date perso
        var mode = if (r == null) 1 else if (r.remind <= 0L) 0 else 4
        var customAt = r?.remind ?: 0L

        val when_ = a.hbox()
        val dateBtn = a.smallButton("", d.color) {}
        val timeBtn = a.smallButton("", d.color) {}
        fun upd() {
            dateBtn.text = "📅 " + D.cap(D.longY(day))
            timeBtn.text = "🕒 " + D.hm(hour, minute)
        }
        upd()
        dateBtn.setOnClickListener { a.pickDate(day) { nd -> day = nd; upd() } }
        timeBtn.setOnClickListener { a.pickTime(hour, minute) { h, m -> hour = h; minute = m; upd() } }
        when_.addView(dateBtn, lpWW())
        val tlp = lpWW()
        tlp.leftMargin = a.dp(8)
        when_.addView(timeBtn, tlp)
        box.addView(when_, lpMW())
        box.addView(a.gap(12))
        val info = a.edit("Lieu, praticien, infos (optionnel)", r?.info ?: "")
        box.addView(info, lpMW())
        box.addView(a.gap(14))
        box.addView(a.caption("Me rappeler"))
        box.addView(a.gap(6))

        val customTxt = a.caption("")
        val chipsHolder = a.vbox()
        val labels = listOf("Pas de rappel", "La veille (18:00)", "2 h avant", "1 semaine avant", "Date perso…")
        fun rebuild() {
            chipsHolder.removeAllViews()
            chipsHolder.addView(a.chips(labels, mode, d.color) { i ->
                if (i == 4) {
                    val base = if (customAt > 0L) customAt else D.ms(day, hour, minute) - 24L * 3600_000L
                    val bd = D.ldt(base)
                    a.pickDate(bd.toLocalDate().toEpochDay()) { cd ->
                        a.pickTime(bd.hour, bd.minute) { h, m ->
                            customAt = D.ms(cd, h, m)
                            mode = 4
                            rebuild()
                        }
                    }
                } else {
                    mode = i
                    rebuild()
                }
            })
            customTxt.text = if (mode == 4 && customAt > 0L) "Rappel le ${D.short(D.dayOf(customAt))} à ${D.time(customAt)}" else ""
        }
        rebuild()
        box.addView(chipsHolder, lpMW())
        box.addView(customTxt, lpMW())

        val sv = android.widget.ScrollView(a)
        sv.addView(box)

        val bld = AlertDialog.Builder(a).setTitle(if (r == null) "Nouveau rendez-vous" else "Modifier le rendez-vous").setView(sv)
            .setPositiveButton("Enregistrer") { _, _ ->
                val t = title.text.toString().trim().ifEmpty { "Rendez-vous" }
                val at = D.ms(day, hour, minute)
                val remind = when (mode) {
                    1 -> D.ms(day - 1, 18, 0)
                    2 -> at - 2 * 3600_000L
                    3 -> at - 7L * 24L * 3600_000L
                    4 -> customAt
                    else -> 0L
                }
                val l = Med.load(a)
                val x = l.firstOrNull { it.id == d.id } ?: return@setPositiveButton
                val now = System.currentTimeMillis()
                if (r == null) {
                    x.rdvs.add(Rdv(newId(), t, at, remind, remind in 1..now, info.text.toString().trim()))
                } else {
                    val y = x.rdvs.firstOrNull { it.id == r.id }
                    if (y != null) {
                        val changed = y.remind != remind
                        y.title = t
                        y.at = at
                        y.remind = remind
                        y.info = info.text.toString().trim()
                        if (changed) y.notified = remind in 1..now
                    }
                }
                Med.save(a, l)
                Reminders.schedule(a)
                if (remind in 1..now) a.toast("Rappel déjà passé : il ne sera pas envoyé")
                refresh()
            }
            .setNegativeButton("Annuler", null)
        if (r != null) {
            bld.setNeutralButton("Supprimer") { _, _ ->
                a.confirm("Supprimer ce rendez-vous ?", r.title, "Supprimer") {
                    val l = Med.load(a)
                    l.firstOrNull { it.id == d.id }?.rdvs?.removeAll { it.id == r.id }
                    Med.save(a, l)
                    Reminders.schedule(a)
                    refresh()
                }
            }
        }
        bld.show()
    }
}
