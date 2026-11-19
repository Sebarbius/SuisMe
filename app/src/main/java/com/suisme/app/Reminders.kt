package com.suisme.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.ZoneId

/** Mélodie de fin de repos (carillon). */
object Sound {
    fun play(c: Context) {
        try {
            val mp = MediaPlayer.create(c.applicationContext, R.raw.rest_end) ?: return
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (t: Throwable) {
        }
    }
}

object App {
    @Volatile
    var visible = false
}

object Notifier {
    const val CH_REMIND = "rappels"
    const val CH_REST = "repos_melodie"
    const val ID_PALP = 10
    const val ID_REST = 20

    fun nm(c: Context): NotificationManager = c.getSystemService(NotificationManager::class.java)

    fun channels(c: Context) {
        val n = nm(c)
        val r = NotificationChannel(CH_REMIND, "Rappels santé", NotificationManager.IMPORTANCE_HIGH)
        r.description = "Auto-palpation et rendez-vous médicaux"
        n.createNotificationChannel(r)
        // Ancien canal (bip standard) remplacé par celui avec la mélodie
        try {
            n.deleteNotificationChannel("repos")
        } catch (t: Throwable) {
        }
        val s = NotificationChannel(CH_REST, "Fin des repos", NotificationManager.IMPORTANCE_HIGH)
        s.description = "Mélodie de fin de repos pendant une séance"
        s.enableVibration(true)
        s.setSound(
            Uri.parse("android.resource://" + c.packageName + "/" + R.raw.rest_end),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        )
        n.createNotificationChannel(s)
    }

    fun openIntent(c: Context, req: Int, space: Int, tab: Int, run: Boolean = false): PendingIntent {
        val i = Intent(c, MainActivity::class.java)
            .putExtra("space", space)
            .putExtra("tab", tab)
            .putExtra("run", run)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(c: Context, id: Int, ch: String, title: String, text: String, pi: PendingIntent) {
        try {
            channels(c)
            val n = Notification.Builder(c, ch)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(C.ROSE)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm(c).notify(id, n)
        } catch (t: Throwable) {
        }
    }

    fun palpation(c: Context) {
        post(
            c, ID_PALP, CH_REMIND, "Auto-palpation 🤍",
            "C'est le moment de faire ton auto-palpation. Valide-la dans SuisMe pour arrêter ce rappel.",
            openIntent(c, 1, MainActivity.SANTE, 0)
        )
    }

    fun cancelPalpation(c: Context) {
        try {
            nm(c).cancel(ID_PALP)
        } catch (t: Throwable) {
        }
    }

    fun rdv(c: Context, d: Domain, r: Rdv) {
        var text = "Rendez-vous " + D.dateTime(r.at)
        if (r.info.isNotBlank()) text += "\n" + r.info
        post(
            c, 1000 + (r.id % 100000L).toInt(), CH_REMIND, "${d.emoji} ${d.name} · ${r.title}", text,
            openIntent(c, 2, MainActivity.SANTE, 1)
        )
    }

    fun restEnd(c: Context, next: String) {
        post(c, ID_REST, CH_REST, "Repos terminé 💪", "C'est reparti : $next", openIntent(c, 3, MainActivity.ACTIV, 0, true))
    }

    fun sessionDone(c: Context) {
        post(c, ID_REST, CH_REST, "Séance terminée 🎉", "Bravo ! Ta séance est enregistrée.", openIntent(c, 4, MainActivity.ACTIV, 1))
    }

    fun cancelRest(c: Context) {
        try {
            nm(c).cancel(ID_REST)
        } catch (t: Throwable) {
        }
    }
}

object Reminders {
    const val ACTION_CHECK = "com.suisme.app.CHECK"
    const val ACTION_REST = "com.suisme.app.REST"
    private const val REQ_CHECK = 1
    private const val REQ_REST = 2

    private fun pi(c: Context, action: String, req: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAlarm(c: Context, at: Long, p: PendingIntent) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (t: Throwable) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } catch (t2: Throwable) {
            }
        }
    }

    /** Heure du rappel quotidien (auto-palpation) pour aujourd'hui, en ms. */
    private fun slotToday(c: Context): Long =
        LocalDate.now().atTime(Cycle.remindHour(c), Cycle.remindMinute(c))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Programme la prochaine vérification : rappel quotidien ou rappel de rendez-vous, le plus proche. */
    fun schedule(c: Context) {
        val now = System.currentTimeMillis()
        var next = slotToday(c)
        if (next <= now) next += 24L * 3600L * 1000L
        for (d in Med.load(c)) for (r in d.rdvs) {
            if (r.remind > 0L && !r.notified) {
                val t = if (r.remind <= now) now + 3000L else r.remind
                if (t < next) next = t
            }
        }
        setAlarm(c, next, pi(c, ACTION_CHECK, REQ_CHECK))
    }

    /** Envoie ce qui est dû, puis reprogramme. */
    fun check(c: Context) {
        val now = System.currentTimeMillis()
        val p = c.prefs()
        if (Cycle.palpDue(c) && now >= slotToday(c) && p.getLong("palp_notified", -1L) != D.today()) {
            Notifier.palpation(c)
            p.edit().putLong("palp_notified", D.today()).apply()
        }
        val list = Med.load(c)
        var changed = false
        for (d in list) for (r in d.rdvs) {
            if (r.remind > 0L && !r.notified && r.remind <= now + 60_000L) {
                Notifier.rdv(c, d, r)
                r.notified = true
                changed = true
            }
        }
        if (changed) Med.save(c, list)
        schedule(c)
    }

    fun setRest(c: Context, at: Long) = setAlarm(c, at, pi(c, ACTION_REST, REQ_REST))

    fun cancelRest(c: Context) {
        try {
            c.getSystemService(AlarmManager::class.java)?.cancel(pi(c, ACTION_REST, REQ_REST))
        } catch (t: Throwable) {
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Reminders.ACTION_REST) {
            // App au premier plan : c'est l'écran de séance qui gère.
            if (App.visible) return
            val s = Runner.load(context) ?: return
            val ev = Runner.catchUp(context, s, System.currentTimeMillis())
            val words = ArrayList<String>()
            val later = ArrayList<String>()
            if (Runner.has(ev, Runner.EV_STOP)) words.add("Arrêter")
            if (Runner.has(ev, Runner.EV_FINISH)) {
                Runner.finish(context, s, true)
                words.add("Séance terminée, bravo")
                Notifier.sessionDone(context)
            } else {
                Runner.save(context, s)
                if (Runner.has(ev, Runner.EV_START)) {
                    if (Runner.has(ev, Runner.EV_REST)) {
                        // Fin de repos + départ auto : mélodie, puis « Commencer »
                        Sound.play(context)
                        later.add("Commencer")
                    } else {
                        words.add("Commencer")
                    }
                } else if (Runner.has(ev, Runner.EV_REST)) {
                    Notifier.restEnd(context, Runner.nextLabel(context, s))
                }
            }
            if (words.isNotEmpty() || later.isNotEmpty()) {
                val pr = goAsync()
                for (w in words) Voice.say(context, w)
                if (later.isNotEmpty()) Voice.init(context)
                Handler(Looper.getMainLooper()).postDelayed({ for (w in later) Voice.say(context, w) }, 1500L)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        pr.finish()
                    } catch (t: Throwable) {
                    }
                }, 6000L)
            }
        } else {
            Reminders.check(context)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.check(context)
    }
}
