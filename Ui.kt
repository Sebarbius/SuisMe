package com.suisme.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Palette « girly » : fond rose poudré, rose, lavande, pêche, orchidée. */
object C {
    val BG = Color.parseColor("#FFF6F9")
    val SURFACE = Color.WHITE
    val SURFACE2 = Color.parseColor("#FFF0F5")
    val LINE = Color.parseColor("#F7DCE6")
    val TEXT = Color.parseColor("#3D2438")
    val MUTED = Color.parseColor("#A28298")
    val ROSE = Color.parseColor("#E8578A")
    val ROSE_DEEP = Color.parseColor("#C93F74")
    val LAVENDER = Color.parseColor("#9C7BE8")
    val PEACH = Color.parseColor("#FF7F73")
    val ORCHID = Color.parseColor("#C25FC9")
    val MINT = Color.parseColor("#3FBF95")
    val SKY = Color.parseColor("#6FA8F0")
    val AMBER = Color.parseColor("#F2A33A")
    val SERIES = intArrayOf(
        Color.parseColor("#E8578A"), Color.parseColor("#9C7BE8"), Color.parseColor("#FF9A62"),
        Color.parseColor("#3FBF95"), Color.parseColor("#6FA8F0"), Color.parseColor("#C25FC9"),
        Color.parseColor("#F2A33A"), Color.parseColor("#8E6C88")
    )
}

/** Mélange une couleur avec du blanc (amount = part de blanc). */
fun soft(c: Int, amount: Float = 0.86f): Int {
    val r = Color.red(c) + ((255 - Color.red(c)) * amount).toInt()
    val g = Color.green(c) + ((255 - Color.green(c)) * amount).toInt()
    val b = Color.blue(c) + ((255 - Color.blue(c)) * amount).toInt()
    return Color.rgb(r, g, b)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.prefs(): SharedPreferences = getSharedPreferences("suisme", Context.MODE_PRIVATE)
fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

private var lastId = 0L
fun newId(): Long {
    var t = System.currentTimeMillis()
    if (t <= lastId) t = lastId + 1
    lastId = t
    return t
}

/** Dates en français. Les jours sont stockés en « epochDay » (Long). */
object D {
    private val FR = Locale.FRENCH
    private val fShort = DateTimeFormatter.ofPattern("d MMM", FR)
    private val fMedium = DateTimeFormatter.ofPattern("d MMM yyyy", FR)
    private val fLong = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)
    private val fLongY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", FR)
    private val fNum = DateTimeFormatter.ofPattern("d/MM", FR)
    private val fTime = DateTimeFormatter.ofPattern("HH:mm", FR)

    fun today(): Long = LocalDate.now().toEpochDay()
    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)
    fun short(day: Long): String = date(day).format(fShort)
    fun medium(day: Long): String = date(day).format(fMedium)
    fun long(day: Long): String = date(day).format(fLong)
    fun longY(day: Long): String = date(day).format(fLongY)
    fun num(day: Long): String = date(day).format(fNum)

    fun ldt(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    fun ms(day: Long, h: Int, m: Int): Long =
        date(day).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun dayOf(ms: Long): Long = ldt(ms).toLocalDate().toEpochDay()
    fun time(ms: Long): String = ldt(ms).format(fTime)
    fun dateTime(ms: Long): String = long(dayOf(ms)) + " à " + time(ms)
    fun hm(h: Int, m: Int): String = String.format(Locale.FRENCH, "%02d:%02d", h, m)

    fun dur(sec: Long): String {
        val s = if (sec < 0) 0 else sec
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format(Locale.FRENCH, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.FRENCH, "%d:%02d", m, ss)
    }

    fun minutes(sec: Long): String {
        val m = sec / 60
        return if (m >= 60) "${m / 60} h ${String.format(Locale.FRENCH, "%02d", m % 60)}" else "$m min"
    }

    fun cap(s: String): String = if (s.isEmpty()) s else s.substring(0, 1).uppercase(FR) + s.substring(1)
}

// ---------- Fabrique de vues ----------

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun lpMW(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
fun lpWW(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
fun lpW(weight: Float): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, WRAP, weight)

fun shape(color: Int, radius: Float, strokeW: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radius
    if (strokeW > 0) g.setStroke(strokeW, strokeColor)
    return g
}

fun gradient(c1: Int, c2: Int, radius: Float): GradientDrawable {
    val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
    g.cornerRadius = radius
    return g
}

fun View.ripple(radius: Float) {
    val mask = shape(Color.WHITE, radius)
    foreground = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 120, 40, 90)), null, mask)
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.txt(s: CharSequence, size: Float = 15f, color: Int = C.TEXT, style: Int = Typeface.NORMAL): TextView =
    TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", style)
    }

fun Context.heading(s: String, color: Int = C.TEXT): TextView =
    txt(s, 18f, color).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

fun Context.caption(s: String, color: Int = C.MUTED): TextView = txt(s, 13f, color)

fun Context.gap(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

fun Context.card(bg: Int = C.SURFACE, border: Int = C.LINE): LinearLayout = vbox().apply {
    background = shape(bg, dpf(22f), dp(1), border)
    setPadding(dp(18), dp(16), dp(18), dp(16))
    layoutParams = lpMW().apply { bottomMargin = dp(12) }
}

fun Context.button(label: String, color: Int, filled: Boolean = true, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) Color.WHITE else color)
        background = if (filled) shape(color, dpf(26f)) else shape(soft(color, 0.87f), dpf(26f))
        setPadding(dp(18), dp(12), dp(18), dp(12))
        ripple(dpf(26f))
        setOnClickListener { onClick() }
    }

fun Context.smallButton(label: String, color: Int, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 13.5f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(color)
        background = shape(soft(color, 0.87f), dpf(18f))
        setPadding(dp(14), dp(8), dp(14), dp(8))
        ripple(dpf(18f))
        setOnClickListener { onClick() }
    }

/** Bouton rond « – » / « + ». */
fun Context.roundButton(label: String, color: Int, size: Int, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 24f
        gravity = Gravity.CENTER
        setTextColor(color)
        background = shape(soft(color, 0.85f), dpf(size / 2f))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        ripple(dpf(size / 2f))
        setOnClickListener { onClick() }
    }

fun Context.chips(labels: List<String>, selected: Int, color: Int, onPick: (Int) -> Unit): HorizontalScrollView {
    val row = hbox()
    labels.forEachIndexed { i, l ->
        val on = i == selected
        val t = txt(l, 13.5f, if (on) Color.WHITE else color)
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.background = if (on) shape(color, dpf(18f)) else shape(soft(color, 0.89f), dpf(18f))
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        val lp = lpWW()
        lp.rightMargin = dp(8)
        t.layoutParams = lp
        t.ripple(dpf(18f))
        t.setOnClickListener { onPick(i) }
        row.addView(t)
    }
    val hs = HorizontalScrollView(this)
    hs.isHorizontalScrollBarEnabled = false
    hs.addView(row)
    hs.layoutParams = lpMW()
    return hs
}

fun Context.divider(): View = View(this).apply {
    setBackgroundColor(C.LINE)
    layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
        topMargin = dp(10)
        bottomMargin = dp(10)
    }
}

/** Barre du haut pour les écrans ouverts par-dessus un onglet. */
fun Context.topBar(title: String, color: Int, onBack: () -> Unit, vararg actions: View): LinearLayout {
    val bar = hbox()
    bar.setPadding(dp(8), dp(10), dp(12), dp(6))
    val back = txt("‹", 30f, color)
    back.setPadding(dp(12), 0, dp(12), dp(4))
    back.ripple(dpf(20f))
    back.setOnClickListener { onBack() }
    bar.addView(back)
    val t = txt(title, 20f, C.TEXT)
    t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    t.maxLines = 1
    t.ellipsize = android.text.TextUtils.TruncateAt.END
    bar.addView(t, lpW(1f))
    for (a in actions) {
        val lp = lpWW()
        lp.leftMargin = dp(6)
        bar.addView(a, lp)
    }
    return bar
}

/** Page défilante standard. */
class Page(val a: Context) {
    val body: LinearLayout = a.vbox().apply { setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(32)) }
    val scroll: ScrollView = ScrollView(a).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(body, ViewGroup.LayoutParams(MATCH, WRAP))
    }
}

// ---------- Dialogues ----------

fun Context.edit(hint: String, initial: String = "", multi: Boolean = false, number: Boolean = false): EditText =
    EditText(this).apply {
        setText(initial)
        this.hint = hint
        setTextColor(C.TEXT)
        setHintTextColor(C.MUTED)
        textSize = 16f
        inputType = when {
            number -> InputType.TYPE_CLASS_NUMBER
            multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        if (multi) {
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
        }
        background = shape(C.SURFACE2, dpf(14f), dp(1), C.LINE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

fun Context.dialogBox(): LinearLayout = vbox().apply { setPadding(dp(22), dp(10), dp(22), dp(4)) }

fun Context.askText(title: String, initial: String, hint: String, multi: Boolean = false, onOk: (String) -> Unit) {
    val e = edit(hint, initial, multi)
    val box = dialogBox()
    box.addView(e, lpMW())
    AlertDialog.Builder(this).setTitle(title).setView(box)
        .setPositiveButton("OK") { _, _ ->
            val s = e.text.toString().trim()
            if (s.isNotEmpty()) onOk(s)
        }
        .setNegativeButton("Annuler", null)
        .show()
}

fun Context.askNumber(title: String, initial: Int, onOk: (Int) -> Unit) {
    val e = edit("", initial.toString(), number = true)
    val box = dialogBox()
    box.addView(e, lpMW())
    AlertDialog.Builder(this).setTitle(title).setView(box)
        .setPositiveButton("OK") { _, _ ->
            val v = e.text.toString().trim().toIntOrNull()
            if (v != null && v >= 0) onOk(v)
        }
        .setNegativeButton("Annuler", null)
        .show()
}

fun Context.confirm(title: String, msg: String, ok: String, onOk: () -> Unit) {
    AlertDialog.Builder(this).setTitle(title).setMessage(msg)
        .setPositiveButton(ok) { _, _ -> onOk() }
        .setNegativeButton("Annuler", null)
        .show()
}

fun Context.choose(title: String, items: List<String>, onPick: (Int) -> Unit) {
    AlertDialog.Builder(this).setTitle(title)
        .setItems(items.toTypedArray()) { _, i -> onPick(i) }
        .setNegativeButton("Annuler", null)
        .show()
}

fun Context.pickDate(day: Long, maxToday: Boolean = false, onPick: (Long) -> Unit) {
    val d = LocalDate.ofEpochDay(day)
    val dlg = DatePickerDialog(this, { _, y, m, dd ->
        onPick(LocalDate.of(y, m + 1, dd).toEpochDay())
    }, d.year, d.monthValue - 1, d.dayOfMonth)
    if (maxToday) dlg.datePicker.maxDate = System.currentTimeMillis()
    dlg.show()
}

fun Context.pickTime(h: Int, m: Int, onPick: (Int, Int) -> Unit) {
    TimePickerDialog(this, { _, hh, mm -> onPick(hh, mm) }, h, m, true).show()
}
