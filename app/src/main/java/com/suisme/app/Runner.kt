package com.suisme.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Séance en cours : état sauvegardé à chaque action, pour pouvoir reprendre. */
object Runner {
    const val WORK = 0
    const val REST_SET = 1
    const val REST_EX = 2

    // Événements renvoyés par catchUp
    const val EV_STOP = 1
    const val EV_START = 2
    const val EV_REST = 4
    const val EV_FINISH = 8

    fun has(ev: Int, flag: Int): Boolean = (ev and flag) != 0

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
        scheduleNext(c, s)
    }

    /** Durée imposée de la série courante en ms (0 si répétitions ou chrono libre). */
    fun timedDur(c: Context, s: State): Long {
        val it = s.items.getOrNull(s.ex) ?: return 0L
        if (it.duration <= 0) return 0L
        val ex = Fit.exMap(c)[it.exId]
        return if (ex?.timed == true) it.duration * 1000L else 0L
    }

    /** Une seule alarme : prochaine fin de repos ou fin de série chronométrée. */
    fun scheduleNext(c: Context, s: State) {
        val d = timedDur(c, s)
        val at = when {
            s.paused -> 0L
            s.phase != WORK -> s.restEnd
            s.chronoOn && d > 0L -> s.chronoStart + d - s.chronoAcc
            else -> 0L
        }
        if (at > 0L) Reminders.setRest(c, at) else Reminders.cancelRest(c)
    }

    fun startChrono(s: State, at: Long) {
        s.chronoOn = true
        s.chronoStart = at
        s.chronoAcc = 0L
    }

    /**
     * Fait avancer la séance jusqu'à « now » : fin des séries chronométrées, fin des repos,
     * démarrage automatique des séries à durée imposée. Renvoie les événements (EV_*).
     */
    fun catchUp(c: Context, s: State, now: Long): Int {
        var ev = 0
        var guard = 0
        while (!s.paused && guard < 60) {
            guard++
            if (s.phase == WORK && s.chronoOn) {
                val d = timedDur(c, s)
                if (d <= 0L) break
                val end = s.chronoStart + d - s.chronoAcc
                if (now < end) break
                record(s, (d / 1000L).toInt())
                s.chronoOn = false
                s.chronoAcc = 0L
                ev = ev or EV_STOP
                if (advance(c, s, end)) {
                    ev = ev or EV_FINISH
                    break
                }
                if (s.chronoOn) ev = ev or EV_START
                continue
            }
            if (s.phase != WORK) {
                if (now < s.restEnd) break
                val t = s.restEnd
                s.phase = WORK
                ev = ev or EV_REST
                if (timedDur(c, s) > 0L) {
                    startChrono(s, t)
                    ev = ev or EV_START
                }
                continue
            }
            break
        }
        return ev
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

    private fun startRest(s: State, sec: Int, phase: Int, at: Long) {
        if (sec <= 0) {
            s.phase = WORK
            return
        }
        s.phase = phase
        s.restTotal = sec * 1000L
        s.restEnd = at + s.restTotal
        s.restLeft = s.restTotal
    }

    /**
     * Passe à la série suivante (repos compris). Si la série suivante a une durée imposée
     * et qu'il n'y a pas de repos, elle démarre tout de suite. Renvoie true si la séance est terminée.
     */
    fun advance(c: Context, s: State, at: Long = System.currentTimeMillis()): Boolean {
        val it = s.items[s.ex]
        if (s.setIdx < it.sets - 1) {
            s.setIdx++
            startRest(s, it.restSets, REST_SET, at)
        } else if (s.ex < s.items.size - 1) {
            s.ex++
            s.setIdx = 0
            startRest(s, it.restAfter, REST_EX, at)
        } else {
            return true
        }
        if (s.phase == WORK && timedDur(c, s) > 0L) startChrono(s, at)
        return false
    }

    fun endRest(c: Context, s: State) {
        s.phase = WORK
        Notifier.cancelRest(c)
    }

    fun addRest(c: Context, s: State, deltaMs: Long) {
        if (s.paused) {
            s.restLeft = (s.restLeft + deltaMs).coerceAtLeast(0L)
        } else {
            s.restEnd += deltaMs
        }
        s.restTotal = (s.restTotal + deltaMs).coerceAtLeast(1000L)
    }

    fun pause(c: Context, s: State) {
        if (s.paused) return
        val now = System.currentTimeMillis()
        s.paused = true
        s.pausedAt = now
        if (s.phase != WORK) s.restLeft = (s.restEnd - now).coerceAtLeast(0L)
        if (s.chronoOn) s.chronoAcc += now - s.chronoStart
    }

    fun resume(c: Context, s: State) {
        if (!s.paused) return
        val now = System.currentTimeMillis()
        s.paused = false
        s.pausedTotal += now - s.pausedAt
        if (s.phase != WORK) s.restEnd = now + s.restLeft
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
