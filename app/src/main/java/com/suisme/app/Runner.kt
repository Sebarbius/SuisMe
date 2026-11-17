package com.suisme.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Séance en cours : état sauvegardé à chaque action, pour pouvoir reprendre. */
object Runner {
    const val WORK = 0
    const val REST_SET = 1
    const val REST_EX = 2

    class State(
        var workoutId: Long,
        var name: String,
        var start: Long,
        val items: MutableList<Item>,
        val done: MutableList<MutableList<Int>>,
        var ex: Int,
        var setIdx: Int,
        var phase: Int,
        var restEnd: Long,
        var restTotal: Long,
        var restLeft: Long,
        var chronoOn: Boolean,
        var chronoStart: Long,
        var chronoAcc: Long,
        var paused: Boolean,
        var pausedAt: Long,
        var pausedTotal: Long
    )

    fun isActive(c: Context): Boolean = c.prefs().contains("run")

    fun load(c: Context): State? {
        val raw = c.prefs().getString("run", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val items = ArrayList<Item>()
            val ia = o.getJSONArray("items")
            for (i in 0 until ia.length()) items.add(Fit.itemFromJson(ia.getJSONObject(i)))
            val done = ArrayList<MutableList<Int>>()
            val da = o.getJSONArray("done")
            for (i in 0 until da.length()) {
                val l = ArrayList<Int>()
                val a = da.getJSONArray(i)
                for (j in 0 until a.length()) l.add(a.getInt(j))
                done.add(l)
            }
            while (done.size < items.size) done.add(ArrayList())
            State(
                o.optLong("wid"), o.optString("name"), o.getLong("start"), items, done,
                o.optInt("ex"), o.optInt("set"), o.optInt("phase"),
                o.optLong("restEnd"), o.optLong("restTotal"), o.optLong("restLeft"),
                o.optBoolean("chronoOn"), o.optLong("chronoStart"), o.optLong("chronoAcc"),
                o.optBoolean("paused"), o.optLong("pausedAt"), o.optLong("pausedTotal")
            )
        } catch (t: Throwable) {
            null
        }
    }

    fun save(c: Context, s: State) {
        val ia = JSONArray()
        for (i in s.items) ia.put(Fit.itemToJson(i))
        val da = JSONArray()
        for (l in s.done) {
            val a = JSONArray()
            for (v in l) a.put(v)
            da.put(a)
        }
        val o = JSONObject()
            .put("wid", s.workoutId).put("name", s.name).put("start", s.start)
            .put("items", ia).put("done", da)
            .put("ex", s.ex).put("set", s.setIdx).put("phase", s.phase)
            .put("restEnd", s.restEnd).put("restTotal", s.restTotal).put("restLeft", s.restLeft)
            .put("chronoOn", s.chronoOn).put("chronoStart", s.chronoStart).put("chronoAcc", s.chronoAcc)
            .put("paused", s.paused).put("pausedAt", s.pausedAt).put("pausedTotal", s.pausedTotal)
        c.prefs().edit().putString("run", o.toString()).apply()
    }

    fun clear(c: Context) {
        c.prefs().edit().remove("run").apply()
        Reminders.cancelRest(c)
        Notifier.cancelRest(c)
    }

    fun start(c: Context, w: Workout): State {
        val items = ArrayList<Item>()
        for (i in w.items) items.add(i.copy())
        val done = ArrayList<MutableList<Int>>()
        for (i in items) done.add(ArrayList())
        val s = State(
            w.id, w.name, System.currentTimeMillis(), items, done, 0, 0, WORK,
            0L, 0L, 0L, false, 0L, 0L, false, 0L, 0L
        )
        save(c, s)
        return s
    }

    fun doneSets(s: State): Int = s.done.sumOf { it.size }

    fun elapsedSec(s: State): Long {
        val now = if (s.paused) s.pausedAt else System.currentTimeMillis()
        return (now - s.start - s.pausedTotal) / 1000L
    }

    fun chronoMs(s: State): Long =
        s.chronoAcc + if (s.chronoOn && !s.paused) System.currentTimeMillis() - s.chronoStart else 0L

    fun restLeftMs(s: State): Long =
        if (s.paused) s.restLeft else (s.restEnd - System.currentTimeMillis()).coerceAtLeast(0L)

    fun nextLabel(c: Context, s: State): String {
        val ex = Fit.exMap(c)[s.items.getOrNull(s.ex)?.exId ?: -1L]
        val it = s.items.getOrNull(s.ex) ?: return "fin de séance"
        return "${ex?.name ?: "Exercice"} · série ${s.setIdx + 1}/${it.sets}"
    }

    /** Enregistre la valeur de la série courante (remplace si elle existe déjà). */
    fun record(s: State, v: Int) {
        val l = s.done[s.ex]
        if (s.setIdx < l.size) {
            l[s.setIdx] = v
        } else {
            while (l.size < s.setIdx) l.add(0)
            l.add(v)
        }
    }

    private fun startRest(c: Context, s: State, sec: Int, phase: Int) {
        if (sec <= 0) {
            s.phase = WORK
            return
        }
        s.phase = phase
        s.restTotal = sec * 1000L
        s.restEnd = System.currentTimeMillis() + s.restTotal
        s.restLeft = s.restTotal
        if (!s.paused) Reminders.setRest(c, s.restEnd)
    }

    /** Passe à la série suivante. Renvoie true si la séance est terminée. */
    fun advance(c: Context, s: State): Boolean {
        val it = s.items[s.ex]
        if (s.setIdx < it.sets - 1) {
            s.setIdx++
            startRest(c, s, it.restSets, REST_SET)
            return false
        }
        if (s.ex < s.items.size - 1) {
            s.ex++
            s.setIdx = 0
            startRest(c, s, it.restAfter, REST_EX)
            return false
        }
        return true
    }

    fun endRest(c: Context, s: State) {
        s.phase = WORK
        Reminders.cancelRest(c)
        Notifier.cancelRest(c)
    }

    fun addRest(c: Context, s: State, deltaMs: Long) {
        if (s.paused) {
            s.restLeft = (s.restLeft + deltaMs).coerceAtLeast(0L)
        } else {
            s.restEnd += deltaMs
            Reminders.setRest(c, s.restEnd)
        }
        s.restTotal = (s.restTotal + deltaMs).coerceAtLeast(1000L)
    }

    fun pause(c: Context, s: State) {
        if (s.paused) return
        val now = System.currentTimeMillis()
        s.paused = true
        s.pausedAt = now
        if (s.phase != WORK) {
            s.restLeft = (s.restEnd - now).coerceAtLeast(0L)
            Reminders.cancelRest(c)
        }
        if (s.chronoOn) s.chronoAcc += now - s.chronoStart
    }

    fun resume(c: Context, s: State) {
        if (!s.paused) return
        val now = System.currentTimeMillis()
        s.paused = false
        s.pausedTotal += now - s.pausedAt
        if (s.phase != WORK) {
            s.restEnd = now + s.restLeft
            Reminders.setRest(c, s.restEnd)
        }
        if (s.chronoOn) s.chronoStart = now
    }

    fun goTo(c: Context, s: State, ex: Int, idx: Int) {
        endRest(c, s)
        s.chronoOn = false
        s.chronoAcc = 0L
        s.ex = ex.coerceIn(0, s.items.size - 1)
        s.setIdx = idx.coerceIn(0, s.items[s.ex].sets - 1)
    }

    /** Termine : ajoute la séance à l'historique (si au moins une série). */
    fun finish(c: Context, s: State, keep: Boolean): Session? {
        if (s.paused) resume(c, s)
        var result: Session? = null
        if (keep) {
            val exs = ArrayList<ExLog>()
            s.items.forEachIndexed { i, item ->
                val sets = s.done[i].filter { v -> v > 0 }
                if (sets.isNotEmpty()) {
                    val existing = exs.firstOrNull { e -> e.exId == item.exId }
                    if (existing != null) existing.sets.addAll(sets) else exs.add(ExLog(item.exId, sets.toMutableList()))
                }
            }
            if (exs.isNotEmpty()) {
                val end = System.currentTimeMillis()
                val sess = Session(newId(), s.workoutId, s.name, s.start, end, elapsedSec(s), exs)
                Fit.addSession(c, sess)
                result = sess
            }
        }
        clear(c)
        return result
    }
}
