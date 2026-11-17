package com.suisme.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

interface Screen {
    val view: View
    fun tick() {}
    fun refresh() {}
    /** true si l'écran gère lui-même le retour. */
    fun onBack(): Boolean = false
    /** false pour les écrans d'édition (on ne reconstruit pas au retour dans l'app). */
    val refreshOnResume: Boolean get() = true
}

class MainActivity : Activity() {
    companion object {
        const val SANTE = 0
        const val ACTIV = 1
    }

    private class Tab(val name: String, val emoji: String, val color: Int)

    private val tabs = listOf(
        listOf(Tab("Cycle", "🌸", C.ROSE), Tab("Suivi médical", "🩺", C.LAVENDER)),
        listOf(Tab("Séances", "💪", C.PEACH), Tab("Progrès", "📈", C.ORCHID))
    )

    private lateinit var header: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var navBar: LinearLayout
    private lateinit var segSante: TextView
    private lateinit var segActiv: TextView
    private lateinit var subtitle: TextView
    private var screen: Screen? = null
    private val stack = ArrayList<Screen>()
    private var space = SANTE
    private var tab = 0
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var gestures: GestureDetector
    private var blockSwipe = false

    private val ticker = object : Runnable {
        override fun run() {
            current()?.tick()
            ui.postDelayed(this, 250L)
        }
    }

    private fun current(): Screen? = stack.lastOrNull() ?: screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.SURFACE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        Notifier.channels(this)

        val root = vbox()
        root.setBackgroundColor(C.BG)

        // ---- En-tête : logo + sélecteur Santé / Activités ----
        header = vbox()
        header.setPadding(dp(20), dp(14), dp(20), dp(8))
        val top = hbox()
        val logo = txt("SuisMe", 28f, C.ROSE)
        logo.typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
        top.addView(logo)
        val heart = txt("  ♡", 20f, C.LAVENDER)
        top.addView(heart, lpW(1f))
        subtitle = caption(D.cap(D.long(D.today())))
        top.addView(subtitle)
        header.addView(top)

        val seg = hbox()
        seg.background = shape(C.SURFACE, dpf(26f), dp(1), C.LINE)
        seg.setPadding(dp(4), dp(4), dp(4), dp(4))
        segSante = segItem("🌸  Santé") { switchSpace(SANTE) }
        segActiv = segItem("💪  Activités") { switchSpace(ACTIV) }
        seg.addView(segSante, lpW(1f))
        seg.addView(segActiv, lpW(1f))
        val slp = lpMW()
        slp.topMargin = dp(12)
        header.addView(seg, slp)
        root.addView(header, lpMW())

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // ---- Barre d'onglets du bas ----
        navBar = hbox()
        navBar.setBackgroundColor(C.SURFACE)
        navBar.setPadding(dp(10), dp(6), dp(10), dp(8))
        navBar.elevation = dpf(8f)
        root.addView(navBar, lpMW())

        setContentView(root)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || stack.isNotEmpty() || blockSwipe) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > dp(90) && abs(dx) > abs(dy) * 2f && abs(velocityX) > 600f) {
                    moveTab(if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })

        val p = prefs()
        space = p.getInt("space", SANTE).coerceIn(0, 1)
        tab = p.getInt("tab", 0).coerceIn(0, tabs[space].size - 1)
        show()
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Reminders.check(this)
    }

    private fun segItem(label: String, onClick: () -> Unit): TextView {
        val t = txt(label, 15f, C.MUTED)
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.setPadding(0, dp(10), 0, dp(10))
        t.setOnClickListener { onClick() }
        return t
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null || !i.hasExtra("space")) return
        space = i.getIntExtra("space", SANTE).coerceIn(0, 1)
        tab = i.getIntExtra("tab", 0).coerceIn(0, tabs[space].size - 1)
        show()
        if (i.getBooleanExtra("run", false) && Runner.isActive(this)) push(RunnerScreen(this))
        i.removeExtra("space")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) blockSwipe = inHScroll(content, ev.rawX, ev.rawY)
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** Vrai si le doigt est sur une rangée défilante horizontale (filtres…). */
    private fun inHScroll(v: View, x: Float, y: Float): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        if (x < loc[0] || x > loc[0] + v.width || y < loc[1] || y > loc[1] + v.height) return false
        if (v is HorizontalScrollView && (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1))) return true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) if (inHScroll(v.getChildAt(i), x, y)) return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        App.visible = true
        subtitle.text = D.cap(D.long(D.today()))
        val cur = current()
        if (cur != null && cur.refreshOnResume) cur.refresh()
        ui.removeCallbacks(ticker)
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        App.visible = false
        ui.removeCallbacks(ticker)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val cur = current()
        if (cur != null && cur.onBack()) return
        if (stack.isNotEmpty()) {
            pop()
            return
        }
        super.onBackPressed()
    }

    // ---------- Navigation ----------

    private fun makeScreen(sp: Int, t: Int): Screen = when {
        sp == SANTE && t == 0 -> CycleScreen(this)
        sp == SANTE -> MedicalScreen(this)
        t == 0 -> SessionsScreen(this)
        else -> ProgressScreen(this)
    }

    private fun show() {
        stack.clear()
        val s = makeScreen(space, tab)
        screen = s
        attach(s)
        prefs().edit().putInt("space", space).putInt("tab", tab).apply()
    }

    private fun attach(s: Screen) {
        content.removeAllViews()
        val parent = s.view.parent
        if (parent is android.view.ViewGroup) parent.removeView(s.view)
        content.addView(s.view, FrameLayout.LayoutParams(MATCH, MATCH))
        s.refresh()
        updateChrome()
    }

    fun push(s: Screen) {
        stack.add(s)
        attach(s)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        val cur = current() ?: return
        attach(cur)
    }

    fun go(sp: Int, t: Int) {
        space = sp
        tab = t
        show()
    }

    private fun switchSpace(sp: Int) {
        if (sp == space && stack.isEmpty()) return
        go(sp, 0)
    }

    private fun moveTab(d: Int) {
        var sp = space
        var t = tab + d
        if (t < 0) {
            if (sp == ACTIV) {
                sp = SANTE
                t = tabs[SANTE].size - 1
            } else return
        }
        if (t >= tabs[sp].size) {
            if (sp == SANTE) {
                sp = ACTIV
                t = 0
            } else return
        }
        go(sp, t)
    }

    private fun updateChrome() {
        val pushed = stack.isNotEmpty()
        header.visibility = if (pushed) View.GONE else View.VISIBLE
        navBar.visibility = if (pushed) View.GONE else View.VISIBLE
        val col = tabs[space][tab].color
        for ((v, sp) in listOf(segSante to SANTE, segActiv to ACTIV)) {
            val on = sp == space
            v.setTextColor(if (on) Color.WHITE else C.MUTED)
            v.background = if (on) gradient(col, soft(col, 0.25f), dpf(22f)) else null
        }
        navBar.removeAllViews()
        tabs[space].forEachIndexed { i, tb ->
            val on = i == tab
            val item = vbox()
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(0, dp(4), 0, dp(2))
            val ic = txt(tb.emoji, 21f, C.TEXT)
            ic.gravity = Gravity.CENTER
            ic.setPadding(dp(18), dp(3), dp(18), dp(3))
            if (on) ic.background = shape(soft(tb.color, 0.82f), dpf(16f))
            item.addView(ic, lpWW())
            val lb = txt(tb.name, 12.5f, if (on) tb.color else C.MUTED)
            lb.typeface = Typeface.create(if (on) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            lb.gravity = Gravity.CENTER
            val llp = lpWW()
            llp.topMargin = dp(2)
            item.addView(lb, llp)
            item.setOnClickListener { if (i != tab || stack.isNotEmpty()) go(space, i) }
            navBar.addView(item, lpW(1f))
        }
    }
}
