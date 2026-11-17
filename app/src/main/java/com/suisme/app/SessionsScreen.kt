package com.suisme.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Onglet « Séances » : séances créées, reprise d'une séance en cours. */
class SessionsScreen(private val a: MainActivity) : Screen {
    private val page = Page(a)
    override val view: View get() = page.scroll

    override fun refresh() {
        val b = page.body
        b.removeAllViews()
        val run = Runner.load(a)
        if (run != null) {
            val c = a.vbox()
            c.background = gradient(C.PEACH, Color.parseColor("#FF9EC0"), a.dpf(22f))
            c.setPadding(a.dp(18), a.dp(16), a.dp(18), a.dp(16))
            c.addView(a.txt("Séance en cours", 13f, Color.WHITE))
            c.addView(a.txt(run.name, 19f, Color.WHITE, Typeface.BOLD))
            c.addView(a.txt("${Runner.doneSets(run)} série(s) faite(s) · ${D.minutes(Runner.elapsedSec(run))}", 13.5f, Color.WHITE))
            c.addView(a.gap(10))
            val go = a.button("▶  Reprendre", C.PEACH) { a.push(RunnerScreen(a)) }
            go.background = shape(Color.WHITE, a.dpf(26f))
            go.setTextColor(C.PEACH)
            c.addView(go, lpMW())
            b.addView(c, lpMW().apply { bottomMargin = a.dp(12) })
        }

        val row = a.hbox()
        row.addView(a.button("＋  Nouvelle séance", C.PEACH) { a.push(WorkoutEditScreen(a, null)) }, lpW(1f))
        val ex = a.button("Mes exercices", C.PEACH, filled = false) { a.push(ExercisesScreen(a)) }
        val elp = lpWW()
        elp.leftMargin = a.dp(10)
        row.addView(ex, elp)
        b.addView(row, lpMW().apply { bottomMargin = a.dp(14) })

        val ws = Fit.workouts(a)
        val exs = Fit.exMap(a)
        if (ws.isEmpty()) {
            val c = a.card()
            c.addView(a.heading("Crée ta première séance ✨"))
            c.addView(a.txt("Choisis tes exercices, le nombre de séries et les temps de repos. Pendant la séance, SuisMe te guide et retient tes performances.", 14.5f).apply { setPadding(0, a.dp(6), 0, 0) })
            b.addView(c)
            return
        }
        for (w in ws) {
            val c = a.card()
            val h = a.hbox()
            h.addView(a.txt(w.name, 17f, C.TEXT, Typeface.BOLD), lpW(1f))
            h.addView(a.smallButton("✎", C.PEACH) { a.push(WorkoutEditScreen(a, w.id)) })
            c.addView(h)
            val names = w.items.mapNotNull { exs[it.exId]?.name }
            c.addView(a.caption("${w.items.size} exercice${if (w.items.size > 1) "s" else ""} · ${w.items.sumOf { it.sets }} séries"))
            if (names.isNotEmpty()) c.addView(a.txt(names.joinToString(" · "), 14f, C.TEXT).apply { setPadding(0, a.dp(6), 0, a.dp(10)) })
            c.addView(a.button("▶  Démarrer", C.PEACH) { startWorkout(w) }, lpMW())
            b.addView(c)
        }
    }

    private fun startWorkout(w: Workout) {
        if (w.items.isEmpty()) {
            a.toast("Ajoute au moins un exercice à cette séance")
            return
        }
        if (Runner.isActive(a)) {
            AlertDialog.Builder(a).setTitle("Une séance est déjà en cours")
                .setMessage("Tu peux la reprendre, ou l'abandonner pour démarrer « ${w.name} ».")
                .setPositiveButton("Reprendre") { _, _ -> a.push(RunnerScreen(a)) }
                .setNeutralButton("Démarrer quand même") { _, _ ->
                    val s = Runner.load(a)
                    if (s != null) Runner.finish(a, s, true)
                    Runner.start(a, w)
                    a.push(RunnerScreen(a))
                }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }
        Runner.start(a, w)
        a.push(RunnerScreen(a))
    }
}

/** Bibliothèque d'exercices : recherche, filtres, création, modification. */
class ExercisesScreen(private val a: MainActivity) : Screen {
    private val root = a.vbox()
    private val page = Page(a)
    private val listBox = a.vbox()
    private var query = ""
    private var group = 0 // 0 = tous
    private var type = 0 // 0 tous, 1 répétitions, 2 chrono
    private val filters = a.vbox()
    override val view: View get() = root

    init {
        root.addView(a.topBar("Mes exercices", C.PEACH, { a.pop() }, a.smallButton("＋ Nouveau", C.PEACH) { exerciseDialog(a, null) { refresh() } }), lpMW())
        val search = a.edit("🔍  Rechercher un exercice")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                buildList()
            }
        })
        page.body.addView(search, lpMW().apply { bottomMargin = a.dp(10) })
        page.body.addView(filters, lpMW())
        page.body.addView(listBox, lpMW())
        root.addView(page.scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    override fun refresh() {
        filters.removeAllViews()
        filters.addView(a.chips(listOf("Tous") + Fit.GROUPS, group, C.PEACH) { i -> group = i; refresh() })
        filters.addView(a.gap(8))
        filters.addView(a.chips(listOf("Tous types", "Répétitions", "Chrono ⏱"), type, C.ORCHID) { i -> type = i; refresh() })
        filters.addView(a.gap(12))
        buildList()
    }

    private fun buildList() {
        listBox.removeAllViews()
        val l = Fit.exercises(a).filter {
            (query.isEmpty() || it.name.contains(query, ignoreCase = true)) &&
                (group == 0 || it.group == Fit.GROUPS[group - 1]) &&
                (type == 0 || (type == 2) == it.timed)
        }.sortedBy { it.name.lowercase() }
        if (l.isEmpty()) {
            listBox.addView(a.caption("Aucun exercice trouvé.").apply { gravity = Gravity.CENTER; setPadding(0, a.dp(20), 0, 0) }, lpMW())
            return
        }
        for (e in l) {
            val c = a.card()
            val r = a.hbox()
            val col = a.vbox()
            col.addView(a.txt(e.name, 16f, C.TEXT, Typeface.BOLD))
            col.addView(a.caption(e.group))
            r.addView(col, lpW(1f))
            val badge = a.txt(if (e.timed) "Chrono ⏱" else "Répétitions", 12.5f, if (e.timed) C.ORCHID else C.PEACH)
            badge.background = shape(soft(if (e.timed) C.ORCHID else C.PEACH, 0.87f), a.dpf(12f))
            badge.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(4))
            r.addView(badge)
            c.addView(r)
            c.ripple(a.dpf(22f))
            c.setOnClickListener { exerciseDialog(a, e) { refresh() } }
            listBox.addView(c)
        }
    }
}

/** Création / modification d'un exercice. */
fun exerciseDialog(a: MainActivity, e: Exercise?, onDone: (Exercise?) -> Unit) {
    val box = a.dialogBox()
    val name = a.edit("Nom (ex. Squats sautés)", e?.name ?: "")
    box.addView(name, lpMW())
    box.addView(a.gap(12))
    box.addView(a.caption("Groupe musculaire"))
    var group = e?.group ?: Fit.GROUPS[0]
    var timed = e?.timed ?: false
    val gHolder = a.vbox()
    val tHolder = a.vbox()
    fun build() {
        gHolder.removeAllViews()
        gHolder.addView(a.chips(Fit.GROUPS, Fit.GROUPS.indexOf(group), C.PEACH) { i -> group = Fit.GROUPS[i]; build() })
        tHolder.removeAllViews()
        tHolder.addView(a.chips(listOf("Répétitions", "Chrono ⏱"), if (timed) 1 else 0, C.ORCHID) { i -> timed = i == 1; build() })
    }
    build()
    box.addView(gHolder, lpMW().apply { topMargin = a.dp(6) })
    box.addView(a.gap(12))
    box.addView(a.caption("Type"))
    box.addView(tHolder, lpMW().apply { topMargin = a.dp(6) })
    val bld = AlertDialog.Builder(a).setTitle(if (e == null) "Nouvel exercice" else "Modifier l'exercice").setView(box)
        .setPositiveButton("Enregistrer") { _, _ ->
            val n = name.text.toString().trim()
            if (n.isEmpty()) {
                a.toast("Donne un nom à l'exercice")
                return@setPositiveButton
            }
            val l = Fit.exercises(a)
            var result: Exercise? = null
            if (e == null) {
                val ne = Exercise(newId(), n, group, timed)
                l.add(ne)
                result = ne
            } else {
                val x = l.firstOrNull { it.id == e.id }
                if (x != null) {
                    x.name = n
                    x.group = group
                    x.timed = timed
                    result = x
                }
            }
            Fit.saveExercises(a, l)
            onDone(result)
        }
        .setNegativeButton("Annuler", null)
    if (e != null) {
        bld.setNeutralButton("Supprimer") { _, _ ->
            a.confirm("Supprimer « ${e.name} » ?", "Il sera retiré de tes séances. Ton historique est conservé.", "Supprimer") {
                Fit.saveExercises(a, Fit.exercises(a).filter { it.id != e.id })
                val ws = Fit.workouts(a)
                for (w in ws) w.items.removeAll { it.exId == e.id }
                Fit.saveWorkouts(a, ws)
                onDone(null)
            }
        }
    }
    bld.show()
}

/** Création / modification d'une séance. */
class WorkoutEditScreen(private val a: MainActivity, workoutId: Long?) : Screen {
    private val root = a.vbox()
    private val page = Page(a)
    private val w: Workout
    private val isNew: Boolean
    override val view: View get() = root
    override val refreshOnResume: Boolean get() = false
    private val nameEdit = a.edit("Nom de la séance (ex. Fessiers & abdos)")
    private val itemsBox = a.vbox()

    init {
        val found = if (workoutId == null) null else Fit.workouts(a).firstOrNull { it.id == workoutId }
        isNew = found == null
        w = if (found != null) Workout(found.id, found.name, found.items.map { it.copy() }.toMutableList())
        else Workout(newId(), "", ArrayList())
        nameEdit.setText(w.name)
        nameEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                w.name = s?.toString() ?: ""
            }
        })
        val save = a.smallButton("Enregistrer", C.PEACH) { save() }
        root.addView(a.topBar(if (isNew) "Nouvelle séance" else "Modifier la séance", C.PEACH, { a.pop() }, save), lpMW())
        page.body.addView(nameEdit, lpMW().apply { bottomMargin = a.dp(12) })
        page.body.addView(itemsBox, lpMW())
        page.body.addView(a.button("＋  Ajouter un exercice", C.PEACH, filled = false) { addExercise() }, lpMW())
        page.body.addView(a.gap(12))
        page.body.addView(a.button("Enregistrer la séance", C.PEACH) { save() }, lpMW())
        if (!isNew) {
            page.body.addView(a.gap(20))
            val del = a.caption("Supprimer cette séance")
            del.setTextColor(C.ROSE)
            del.gravity = Gravity.CENTER
            del.setPadding(0, a.dp(8), 0, a.dp(8))
            del.setOnClickListener {
                a.confirm("Supprimer « ${w.name} » ?", "Ton historique de séances est conservé.", "Supprimer") {
                    Fit.deleteWorkout(a, w.id)
                    a.pop()
                }
            }
            page.body.addView(del, lpMW())
        }
        root.addView(page.scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    private fun save() {
        if (w.name.isBlank()) {
            a.toast("Donne un nom à ta séance")
            return
        }
        if (w.items.isEmpty()) {
            a.toast("Ajoute au moins un exercice")
            return
        }
        w.name = w.name.trim()
        Fit.saveWorkout(a, w)
        a.toast("Séance enregistrée 💪")
        a.pop()
    }

    private fun addExercise() {
        val l = Fit.exercises(a).sortedBy { it.name.lowercase() }
        val labels = l.map { "${it.name}  ·  ${it.group}${if (it.timed) "  ⏱" else ""}" } + "＋ Créer un nouvel exercice"
        a.choose("Ajouter un exercice", labels) { i ->
            if (i < l.size) {
                w.items.add(Item(l[i].id, 3, 60, 90))
                refresh()
            } else {
                exerciseDialog(a, null) { e ->
                    if (e != null) {
                        w.items.add(Item(e.id, 3, 60, 90))
                        refresh()
                    }
                }
            }
        }
    }

    override fun refresh() {
        itemsBox.removeAllViews()
        val exs = Fit.exMap(a)
        w.items.removeAll { !exs.containsKey(it.exId) }
        w.items.forEachIndexed { idx, item ->
            val e = exs[item.exId] ?: return@forEachIndexed
            val c = a.card()
            val h = a.hbox()
            val num = a.txt("${idx + 1}", 14f, Color.WHITE, Typeface.BOLD)
            num.gravity = Gravity.CENTER
            num.background = shape(C.PEACH, a.dpf(13f))
            h.addView(num, LinearLayout.LayoutParams(a.dp(26), a.dp(26)))
            val col = a.vbox()
            col.setPadding(a.dp(10), 0, 0, 0)
            col.addView(a.txt(e.name, 16f, C.TEXT, Typeface.BOLD))
            col.addView(a.caption("${e.group} · ${if (e.timed) "chrono" else "répétitions"}"))
            h.addView(col, lpW(1f))
            h.addView(a.smallButton("↑", C.MUTED) { move(idx, -1) })
            h.addView(a.smallButton("↓", C.MUTED) { move(idx, 1) }, lpWW().apply { leftMargin = a.dp(4) })
            h.addView(a.smallButton("✕", C.ROSE) { w.items.removeAt(idx); refresh() }, lpWW().apply { leftMargin = a.dp(4) })
            c.addView(h)
            c.addView(a.gap(8))
            c.addView(stepper("Séries", "${item.sets}", { item.sets = (item.sets - 1).coerceAtLeast(1) }, { item.sets = (item.sets + 1).coerceAtMost(20) }))
            c.addView(stepper("Repos entre les séries", D.dur(item.restSets.toLong()), { item.restSets = (item.restSets - 15).coerceAtLeast(0) }, { item.restSets += 15 }))
            c.addView(stepper("Repos après l'exercice", D.dur(item.restAfter.toLong()), { item.restAfter = (item.restAfter - 15).coerceAtLeast(0) }, { item.restAfter += 15 }))
            itemsBox.addView(c)
        }
        if (w.items.isEmpty()) itemsBox.addView(a.caption("Aucun exercice pour l'instant.").apply { setPadding(a.dp(4), 0, 0, a.dp(12)) })
    }

    private fun move(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= w.items.size) return
        val t = w.items[i]
        w.items[i] = w.items[j]
        w.items[j] = t
        refresh()
    }

    private fun stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit): View {
        val r = a.hbox()
        r.setPadding(0, a.dp(4), 0, a.dp(4))
        r.addView(a.txt(label, 14f, C.TEXT), lpW(1f))
        r.addView(a.roundButton("–", C.PEACH, 36) { minus(); refresh() })
        val v = a.txt(value, 15f, C.TEXT, Typeface.BOLD)
        v.gravity = Gravity.CENTER
        r.addView(v, LinearLayout.LayoutParams(a.dp(64), WRAP))
        r.addView(a.roundButton("+", C.PEACH, 36) { plus(); refresh() })
        return r
    }
}
