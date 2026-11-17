package com.suisme.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// =====================================================================
//  Cycle menstruel
// =====================================================================

object Cycle {
    /** Jour du cycle où l'auto-palpation est demandée : début + 7 jours. */
    const val PALP_OFFSET = 7L

    data class Past(val start: Long, val end: Long, val length: Int)

    fun starts(c: Context): MutableList<Long> {
        val out = ArrayList<Long>()
        try {
            val arr = JSONArray(c.prefs().getString("cycles", "[]"))
            for (i in 0 until arr.length()) out.add(arr.getLong(i))
        } catch (t: Throwable) {
        }
        out.sort()
        return out
    }

    private fun save(c: Context, list: List<Long>) {
        val arr = JSONArray()
        for (d in list.distinct().sorted()) arr.put(d)
        c.prefs().edit().putString("cycles", arr.toString()).apply()
    }

    fun add(c: Context, day: Long): Boolean {
        val l = starts(c)
        if (l.contains(day)) return false
        l.add(day)
        save(c, l)
        return true
    }

    fun remove(c: Context, day: Long) {
        val l = starts(c)
        l.remove(day)
        save(c, l)
        setPalpDone(c, day, null)
    }

    fun move(c: Context, old: Long, new: Long): Boolean {
        val l = starts(c)
        if (old != new && l.contains(new)) return false
        l.remove(old)
        l.add(new)
        save(c, l)
        val done = palpDone(c, old)
        setPalpDone(c, old, null)
        if (done != null) setPalpDone(c, new, done)
        return true
    }

    /** Début du cycle en cours (dernier début passé ou aujourd'hui). */
    fun current(c: Context): Long? = starts(c).lastOrNull { it <= D.today() }

    fun dayOfCycle(c: Context): Int? {
        val cur = current(c) ?: return null
        return (D.today() - cur + 1).toInt()
    }

    fun past(c: Context): List<Past> {
        val l = starts(c).filter { it <= D.today() }
        val out = ArrayList<Past>()
        for (i in 0 until l.size - 1) {
            out.add(Past(l[i], l[i + 1] - 1, (l[i + 1] - l[i]).toInt()))
        }
        return out
    }

    fun average(c: Context): Double? {
        val p = past(c)
        if (p.isEmpty()) return null
        return p.map { it.length }.average()
    }

    // ----- Auto-palpation -----

    fun palpDay(start: Long): Long = start + PALP_OFFSET

    fun palpDone(c: Context, start: Long): Long? {
        val o = palpObj(c)
        return if (o.has(start.toString())) o.optLong(start.toString()) else null
    }

    fun setPalpDone(c: Context, start: Long, day: Long?) {
        val o = palpObj(c)
        if (day == null) o.remove(start.toString()) else o.put(start.toString(), day)
        c.prefs().edit().putString("palp", o.toString()).apply()
    }

    private fun palpObj(c: Context): JSONObject =
        try {
            JSONObject(c.prefs().getString("palp", "{}") ?: "{}")
        } catch (t: Throwable) {
            JSONObject()
        }

    /** Vrai si l'auto-palpation du cycle en cours est due et pas encore validée. */
    fun palpDue(c: Context): Boolean {
        val cur = current(c) ?: return false
        return D.today() >= palpDay(cur) && palpDone(c, cur) == null
    }

    fun remindHour(c: Context): Int = c.prefs().getInt("remind_h", 9)
    fun remindMinute(c: Context): Int = c.prefs().getInt("remind_m", 30)
    fun setRemind(c: Context, h: Int, m: Int) {
        c.prefs().edit().putInt("remind_h", h).putInt("remind_m", m).apply()
    }
}

// =====================================================================
//  Suivi médical
// =====================================================================

class Note(val id: Long, var day: Long, var text: String)

class Rdv(
    val id: Long,
    var title: String,
    var at: Long,
    var remind: Long,
    var notified: Boolean,
    var info: String
)

class Domain(
    val id: Long,
    var name: String,
    var emoji: String,
    var color: Int,
    val notes: MutableList<Note>,
    val rdvs: MutableList<Rdv>
)

object Med {
    val EMOJIS = listOf("🌸", "🩺", "🦷", "👁️", "🤰", "💊", "🧴", "🫀", "🧠", "🦴", "🩻", "💉", "🍼", "🌿")
    val SUGGESTIONS = listOf(
        "Gynécologie" to "🌸", "Médecin traitant" to "🩺", "Dentiste" to "🦷",
        "Ophtalmologie" to "👁️", "Dermatologie" to "🧴"
    )

    fun load(c: Context): MutableList<Domain> {
        val out = ArrayList<Domain>()
        try {
            val arr = JSONArray(c.prefs().getString("med", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val notes = ArrayList<Note>()
                val na = o.optJSONArray("notes") ?: JSONArray()
                for (j in 0 until na.length()) {
                    val n = na.getJSONObject(j)
                    notes.add(Note(n.getLong("id"), n.getLong("day"), n.optString("text")))
                }
                val rdvs = ArrayList<Rdv>()
                val ra = o.optJSONArray("rdv") ?: JSONArray()
                for (j in 0 until ra.length()) {
                    val r = ra.getJSONObject(j)
                    rdvs.add(
                        Rdv(
                            r.getLong("id"), r.optString("title"), r.getLong("at"),
                            r.optLong("remind", 0L), r.optBoolean("notified", false), r.optString("info")
                        )
                    )
                }
                out.add(Domain(o.getLong("id"), o.optString("name"), o.optString("emoji", "🩺"), o.optInt("color", C.LAVENDER), notes, rdvs))
            }
        } catch (t: Throwable) {
        }
        return out
    }

    fun save(c: Context, list: List<Domain>) {
        val arr = JSONArray()
        for (d in list) {
            val o = JSONObject()
            o.put("id", d.id)
            o.put("name", d.name)
            o.put("emoji", d.emoji)
            o.put("color", d.color)
            val na = JSONArray()
            for (n in d.notes) na.put(JSONObject().put("id", n.id).put("day", n.day).put("text", n.text))
            o.put("notes", na)
            val ra = JSONArray()
            for (r in d.rdvs) {
                ra.put(
                    JSONObject().put("id", r.id).put("title", r.title).put("at", r.at)
                        .put("remind", r.remind).put("notified", r.notified).put("info", r.info)
                )
            }
            o.put("rdv", ra)
            arr.put(o)
        }
        c.prefs().edit().putString("med", arr.toString()).apply()
    }
}

// =====================================================================
//  Activités : exercices, séances, historique
// =====================================================================

class Exercise(val id: Long, var name: String, var group: String, var timed: Boolean)

/** duration : durée imposée d'une série en secondes (exercices chrono), 0 = chrono libre. */
class Item(var exId: Long, var sets: Int, var restSets: Int, var restAfter: Int, var duration: Int = 0) {
    fun copy(): Item = Item(exId, sets, restSets, restAfter, duration)
}

class Workout(val id: Long, var name: String, val items: MutableList<Item>)

class ExLog(val exId: Long, val sets: MutableList<Int>)

class Session(
    val id: Long,
    val workoutId: Long,
    val name: String,
    val start: Long,
    val end: Long,
    val activeSec: Long,
    val exs: List<ExLog>
)

object Fit {
    val GROUPS = listOf("Jambes", "Fessiers", "Abdos", "Haut du corps", "Dos", "Bras", "Cardio", "Étirements", "Autre")

    fun itemToJson(i: Item): JSONObject =
        JSONObject().put("ex", i.exId).put("sets", i.sets).put("rs", i.restSets).put("ra", i.restAfter).put("dur", i.duration)

    fun itemFromJson(o: JSONObject): Item =
        Item(o.getLong("ex"), o.optInt("sets", 3), o.optInt("rs", 60), o.optInt("ra", 90), o.optInt("dur", 0))

    // ----- Exercices -----

    fun exercises(c: Context): MutableList<Exercise> {
        val p = c.prefs()
        if (!p.contains("exercises")) {
            var id = 1L
            val seed = listOf(
                Exercise(id++, "Squats", "Jambes", false),
                Exercise(id++, "Fentes", "Jambes", false),
                Exercise(id++, "Hip thrust", "Fessiers", false),
                Exercise(id++, "Donkey kicks", "Fessiers", false),
                Exercise(id++, "Crunchs", "Abdos", false),
                Exercise(id++, "Gainage", "Abdos", true),
                Exercise(id++, "Chaise", "Jambes", true),
                Exercise(id++, "Pompes", "Haut du corps", false)
            )
            saveExercises(c, seed)
        }
        val out = ArrayList<Exercise>()
        try {
            val arr = JSONArray(p.getString("exercises", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Exercise(o.getLong("id"), o.optString("name"), o.optString("group", "Autre"), o.optBoolean("timed", false)))
            }
        } catch (t: Throwable) {
        }
        return out
    }

    fun saveExercises(c: Context, list: List<Exercise>) {
        val arr = JSONArray()
        for (e in list) arr.put(JSONObject().put("id", e.id).put("name", e.name).put("group", e.group).put("timed", e.timed))
        c.prefs().edit().putString("exercises", arr.toString()).apply()
    }

    fun exMap(c: Context): Map<Long, Exercise> = exercises(c).associateBy { it.id }

    // ----- Séances (modèles) -----

    fun workouts(c: Context): MutableList<Workout> {
        val out = ArrayList<Workout>()
        try {
            val arr = JSONArray(c.prefs().getString("workouts", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val items = ArrayList<Item>()
                val ia = o.optJSONArray("items") ?: JSONArray()
                for (j in 0 until ia.length()) items.add(itemFromJson(ia.getJSONObject(j)))
                out.add(Workout(o.getLong("id"), o.optString("name"), items))
            }
        } catch (t: Throwable) {
        }
        return out
    }

    fun saveWorkouts(c: Context, list: List<Workout>) {
        val arr = JSONArray()
        for (w in list) {
            val ia = JSONArray()
            for (i in w.items) ia.put(itemToJson(i))
            arr.put(JSONObject().put("id", w.id).put("name", w.name).put("items", ia))
        }
        c.prefs().edit().putString("workouts", arr.toString()).apply()
    }

    fun saveWorkout(c: Context, w: Workout) {
        val l = workouts(c)
        val i = l.indexOfFirst { it.id == w.id }
        if (i >= 0) l[i] = w else l.add(w)
        saveWorkouts(c, l)
    }

    fun deleteWorkout(c: Context, id: Long) {
        saveWorkouts(c, workouts(c).filter { it.id != id })
    }

    // ----- Historique -----

    fun sessions(c: Context): MutableList<Session> {
        val out = ArrayList<Session>()
        try {
            val arr = JSONArray(c.prefs().getString("sessions", "[]"))
            for (i in 0 until arr.length()) out.add(sessionFromJson(arr.getJSONObject(i)))
        } catch (t: Throwable) {
        }
        out.sortBy { it.start }
        return out
    }

    fun sessionToJson(s: Session): JSONObject {
        val ea = JSONArray()
        for (e in s.exs) {
            val sa = JSONArray()
            for (v in e.sets) sa.put(v)
            ea.put(JSONObject().put("id", e.exId).put("s", sa))
        }
        return JSONObject().put("id", s.id).put("wid", s.workoutId).put("name", s.name)
            .put("start", s.start).put("end", s.end).put("active", s.activeSec).put("ex", ea)
    }

    fun sessionFromJson(o: JSONObject): Session {
        val exs = ArrayList<ExLog>()
        val ea = o.optJSONArray("ex") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val e = ea.getJSONObject(i)
            val sets = ArrayList<Int>()
            val sa = e.optJSONArray("s") ?: JSONArray()
            for (j in 0 until sa.length()) sets.add(sa.getInt(j))
            exs.add(ExLog(e.getLong("id"), sets))
        }
        val start = o.getLong("start")
        val end = o.optLong("end", start)
        return Session(
            o.getLong("id"), o.optLong("wid", 0L), o.optString("name"), start, end,
            o.optLong("active", (end - start) / 1000L), exs
        )
    }

    fun saveSessions(c: Context, list: List<Session>) {
        val arr = JSONArray()
        for (s in list) arr.put(sessionToJson(s))
        c.prefs().edit().putString("sessions", arr.toString()).apply()
    }

    fun addSession(c: Context, s: Session) {
        val l = sessions(c)
        l.add(s)
        saveSessions(c, l)
    }

    fun deleteSession(c: Context, id: Long) {
        saveSessions(c, sessions(c).filter { it.id != id })
    }

    /** Séries réalisées la dernière fois sur cet exercice. */
    fun lastSets(c: Context, exId: Long): List<Int> {
        val l = sessions(c)
        for (i in l.indices.reversed()) {
            val e = l[i].exs.firstOrNull { it.exId == exId && it.sets.isNotEmpty() }
            if (e != null) return e.sets
        }
        return emptyList()
    }

    fun fmt(v: Int, timed: Boolean): String = if (timed) D.dur(v.toLong()) else "$v"
    fun unit(timed: Boolean): String = if (timed) "" else " reps"
}
