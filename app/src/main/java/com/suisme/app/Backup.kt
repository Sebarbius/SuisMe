package com.suisme.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Voix de synthèse (« Commencer », « Arrêter »). */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()

    fun init(c: Context) {
        if (tts != null) return
        try {
            tts = TextToSpeech(c.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts?.setLanguage(Locale.FRENCH)
                    } catch (t: Throwable) {
                    }
                    ready = true
                    val l = ArrayList(pending)
                    pending.clear()
                    for (w in l) speak(w)
                } else {
                    tts = null
                }
            }
        } catch (t: Throwable) {
            tts = null
        }
    }

    fun say(c: Context, text: String) {
        init(c)
        if (ready) speak(text) else pending.add(text)
    }

    private fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "suisme" + System.nanoTime())
        } catch (t: Throwable) {
        }
    }
}

/** Sauvegarde / restauration de toutes les données dans un fichier JSON. */
object Backup {
    /** Clés d'interface ou techniques : jamais sauvegardées, et ne déclenchent pas la sauvegarde auto. */
    private val EXCLUDE = setOf(
        "space", "tab", "run", "palp_notified", "prog_ex", "prog_mode", "prog_period",
        "auto_on", "auto_uri", "auto_name", "auto_error", "last_backup", "last_hash"
    )

    fun isExcluded(key: String): Boolean = key in EXCLUDE

    fun data(c: Context): JSONObject {
        val o = JSONObject()
        val all = c.prefs().all
        for (k in all.keys.sorted()) {
            if (k in EXCLUDE) continue
            val v = all[k]
            val e = JSONObject()
            when (v) {
                is String -> e.put("t", "s").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Boolean -> e.put("t", "b").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    fun hash(c: Context): String = data(c).toString().hashCode().toString()

    fun upToDate(c: Context): Boolean = c.prefs().getString("last_hash", "") == hash(c)

    fun lastBackup(c: Context): Long = c.prefs().getLong("last_backup", 0L)

    fun fileName(): String =
        "SuisMe-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH'h'mm", Locale.FRENCH)) + ".json"

    /** Écrit la sauvegarde dans le fichier choisi (Drive, téléphone…). */
    fun write(c: Context, uri: Uri): Boolean {
        val d = data(c)
        val h = d.toString().hashCode().toString()
        val bytes = JSONObject()
            .put("app", "SuisMe")
            .put("version", 1)
            .put("date", System.currentTimeMillis())
            .put("data", d)
            .toString(1)
            .toByteArray(Charsets.UTF_8)
        var ok = false
        for (mode in listOf("wt", "w")) {
            try {
                val out = c.contentResolver.openOutputStream(uri, mode) ?: continue
                out.use { it.write(bytes) }
                ok = true
                break
            } catch (t: Throwable) {
            }
        }
        if (ok) c.prefs().edit().putLong("last_backup", System.currentTimeMillis()).putString("last_hash", h).apply()
        return ok
    }

    fun read(c: Context, uri: Uri): JSONObject? =
        try {
            val txt = c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val o = JSONObject(txt)
            if (o.optString("app") == "SuisMe" && o.has("data")) o else null
        } catch (t: Throwable) {
            null
        }

    /** Remplace toutes les données par celles de la sauvegarde. */
    fun restore(c: Context, o: JSONObject) {
        val d = o.getJSONObject("data")
        val p = c.prefs()
        val ed = p.edit()
        for (k in p.all.keys) if (k !in EXCLUDE || k == "run") ed.remove(k)
        val keys = d.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val e = d.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "s" -> ed.putString(k, e.optString("v"))
                "i" -> ed.putInt(k, e.optInt("v"))
                "l" -> ed.putLong(k, e.optLong("v"))
                "b" -> ed.putBoolean(k, e.optBoolean("v"))
                "f" -> ed.putFloat(k, e.optDouble("v").toFloat())
            }
        }
        ed.commit()
        Reminders.cancelRest(c)
    }

    fun autoOn(c: Context): Boolean = c.prefs().getBoolean("auto_on", false) && c.prefs().getString("auto_uri", null) != null

    /** Sauvegarde auto : seulement si des données ont changé depuis la dernière sauvegarde. */
    fun auto(c: Context) {
        if (!autoOn(c) || upToDate(c)) return
        val u = c.prefs().getString("auto_uri", null) ?: return
        val ok = write(c, Uri.parse(u))
        c.prefs().edit().putBoolean("auto_error", !ok).apply()
    }

    fun displayName(c: Context, uri: Uri): String =
        try {
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "fichier de sauvegarde"
        } catch (t: Throwable) {
            "fichier de sauvegarde"
        }
}

/** Écran « Sauvegarde » : maintenant, auto, restaurer. */
class BackupScreen(private val a: MainActivity) : Screen {
    private val root = a.vbox()
    private val page = Page(a)
    override val view: View get() = root

    override fun refresh() {
        root.removeAllViews()
        root.addView(a.topBar("Sauvegarde", C.LAVENDER, { a.pop() }), lpMW())
        root.addView(page.scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val b = page.body
        b.removeAllViews()

        // ---- État ----
        val ok = Backup.upToDate(a)
        val last = Backup.lastBackup(a)
        val st = a.vbox()
        st.background = gradient(if (ok) C.MINT else Color.parseColor("#FF8FB1"), if (ok) Color.parseColor("#7FD8C0") else C.LAVENDER, a.dpf(24f))
        st.setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18))
        st.addView(a.txt(if (ok) "☁ ✓  Tout est sauvegardé" else "☁ ↑  Des données ne sont pas sauvegardées", 17f, Color.WHITE, Typeface.BOLD))
        st.addView(a.txt(if (last > 0L) "Dernière sauvegarde : ${D.cap(D.dateTime(last))}" else "Aucune sauvegarde pour l'instant", 14f, Color.WHITE).apply { setPadding(0, a.dp(6), 0, 0) })
        b.addView(st, lpMW().apply { bottomMargin = a.dp(12) })

        // ---- Sauvegarder maintenant ----
        val now = a.card()
        now.addView(a.heading("Sauvegarder maintenant"))
        now.addView(a.txt("Choisis Google Drive dans la liste qui s'ouvre (ou un dossier du téléphone). Le fichier contient tout : cycles, palpations, suivi médical, séances et progrès.", 14f).apply { setPadding(0, a.dp(6), 0, a.dp(12)) })
        now.addView(a.button("☁  Enregistrer sur Google Drive…", C.LAVENDER) { saveNow() }, lpMW())
        b.addView(now)

        // ---- Sauvegarde automatique ----
        val au = a.card()
        val on = Backup.autoOn(a)
        val h = a.hbox()
        h.addView(a.heading("Sauvegarde automatique"), lpW(1f))
        val sw = android.widget.Switch(a)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, checked ->
            if (checked) chooseAutoFile()
            else {
                a.prefs().edit().putBoolean("auto_on", false).apply()
                refresh()
            }
        }
        h.addView(sw)
        au.addView(h)
        if (on) {
            val name = a.prefs().getString("auto_name", "fichier de sauvegarde") ?: ""
            au.addView(a.txt("Le fichier « $name » est mis à jour quelques secondes après chaque nouvelle donnée (règles, palpation, note, rendez-vous, séance…).", 14f).apply { setPadding(0, a.dp(6), 0, a.dp(10)) })
            if (a.prefs().getBoolean("auto_error", false)) {
                au.addView(a.txt("⚠ La dernière mise à jour a échoué. Rechoisis l'emplacement du fichier.", 14f, C.ROSE).apply { setPadding(0, 0, 0, a.dp(10)) })
            }
            au.addView(a.smallButton("Changer d'emplacement", C.LAVENDER) { chooseAutoFile() })
        } else {
            au.addView(a.txt("Active-la et choisis une seule fois où mettre le fichier, par exemple sur Google Drive. SuisMe le met ensuite à jour tout seul à chaque nouvelle donnée.", 14f).apply { setPadding(0, a.dp(6), 0, 0) })
        }
        b.addView(au)

        // ---- Restaurer ----
        val rs = a.card()
        rs.addView(a.heading("Restaurer"))
        rs.addView(a.txt("Sur un nouveau téléphone : installe SuisMe, puis choisis ton fichier de sauvegarde. Les données actuelles seront remplacées.", 14f).apply { setPadding(0, a.dp(6), 0, a.dp(12)) })
        rs.addView(a.button("Restaurer depuis un fichier", C.ROSE, filled = false) { restore() }, lpMW())
        b.addView(rs)

        b.addView(a.caption("Astuce : sur Google Drive, le fichier apparaît dans « Mon Drive » ou dans le dossier que tu choisis.").apply { gravity = Gravity.CENTER })
    }

    private fun createIntent(name: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )

    private fun saveNow() {
        a.pickFile(createIntent(Backup.fileName())) { uri ->
            if (Backup.write(a, uri)) a.toast("Sauvegarde enregistrée ☁") else a.toast("Échec de l'enregistrement")
            refresh()
            a.refreshCloud()
        }
    }

    private fun chooseAutoFile() {
        a.pickFile(createIntent("SuisMe-sauvegarde-auto.json"), { refresh() }) { uri ->
            var persisted = true
            try {
                a.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (t: Throwable) {
                persisted = false
            }
            if (!persisted) {
                a.toast("Cet emplacement n'autorise pas la mise à jour automatique. Choisis-en un autre.")
                a.prefs().edit().putBoolean("auto_on", false).apply()
            } else {
                a.prefs().edit()
                    .putBoolean("auto_on", true)
                    .putString("auto_uri", uri.toString())
                    .putString("auto_name", Backup.displayName(a, uri))
                    .putBoolean("auto_error", false)
                    .apply()
                if (Backup.write(a, uri)) a.toast("Sauvegarde automatique activée ☁")
                else a.prefs().edit().putBoolean("auto_error", true).apply()
            }
            refresh()
            a.refreshCloud()
        }
    }

    private fun restore() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        a.pickFile(i) { uri ->
            val o = Backup.read(a, uri)
            if (o == null) {
                a.toast("Ce fichier n'est pas une sauvegarde SuisMe")
                return@pickFile
            }
            val date = o.optLong("date", 0L)
            a.confirm(
                "Restaurer cette sauvegarde ?",
                (if (date > 0L) "Sauvegarde du ${D.dateTime(date)}.\n\n" else "") + "Toutes les données actuelles seront remplacées.",
                "Restaurer"
            ) {
                Backup.restore(a, o)
                Reminders.check(a)
                a.toast("Données restaurées 🌸")
                a.go(MainActivity.SANTE, 0)
            }
        }
    }
}
