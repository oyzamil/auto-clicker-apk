package com.example.openclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

class ClickerService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickerService? = null
    }

    private class MarkerRef(
        val view: View,
        val params: WindowManager.LayoutParams,
        val size: Int,
        val onMoved: (Float, Float) -> Unit
    )

    private lateinit var wm: WindowManager

    /** Run-loop handler only (cleared on stop). */
    private val runHandler = Handler(Looper.getMainLooper())

    /** UI housekeeping handler (watchdog, z-order). Never cleared by stopRun. */
    private val ui = Handler(Looper.getMainLooper())
    private val rnd = Random()

    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var startBtn: TextView? = null
    private var addBtn: TextView? = null
    private var recBtn: TextView? = null
    private var centerBtn: TextView? = null
    private var hideBtn: TextView? = null
    private var closeBtn: TextView? = null
    private var collapsed = false

    private val markers = mutableListOf<MarkerRef>()
    private var markersMulti: Boolean? = null
    private var recordView: View? = null
    private var lastW = 0
    private var lastH = 0

    private var running = false
    private var steps: List<ClickAction> = emptyList()
    private var idx = 0
    private var loopCount = 0

    // ---------- lifecycle ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.init(this)
        Session.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopRun()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ui.postDelayed({ housekeeping() }, 300)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hidePanel()
        instance = null
        return super.onUnbind(intent)
    }

    // ---------- helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    private fun lp(w: Int, h: Int): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun btn(t: String, onClick: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(Color.WHITE)
        v.textSize = 18f
        v.gravity = Gravity.CENTER
        v.minWidth = dp(40)
        v.minHeight = dp(40)
        v.setOnClickListener { onClick() }
        return v
    }

    // ---------- panel ----------

    fun showPanel() {
        if (panel != null) return
        val p = LinearLayout(this)
        p.orientation = LinearLayout.HORIZONTAL
        p.gravity = Gravity.CENTER_VERTICAL
        p.setPadding(dp(6), dp(4), dp(6), dp(4))
        p.background = GradientDrawable().apply {
            setColor(0xF2171A22.toInt())
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), 0x33FFFFFF)
        }
        val params = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        params.x = dp(8)
        params.y = dp(160)

        // drag handle (tap when collapsed = expand)
        val handle = btn("⠿") {}
        var sx = 0f
        var sy = 0f
        var px = 0
        var py = 0
        var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; px = params.x; py = params.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - sx) > dp(6) || abs(e.rawY - sy) > dp(6)) moved = true
                    if (moved) {
                        val (w, h) = screenSize()
                        params.x = (px + (e.rawX - sx).toInt()).coerceIn(0, (w - p.width).coerceAtLeast(0))
                        params.y = (py + (e.rawY - sy).toInt()).coerceIn(0, (h - p.height).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(p, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && collapsed) toggleCollapse()
                }
            }
            true
        }

        val sb = btn("▶") { if (running) stopRun() else startRun() }
        sb.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFF7A00.toInt(), 0xFFFF4D1A.toInt())
        ).apply { shape = GradientDrawable.OVAL }
        startBtn = sb
        addBtn = btn("+") { addTarget() }
        recBtn = btn("●") { if (recordView != null) stopRecord() else startRecord() }
        centerBtn = btn("⌖") { recenter() }
        hideBtn = btn("◀") { toggleCollapse() }
        closeBtn = btn("✕") { hidePanel() }

        p.addView(handle)
        p.addView(sb, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(4), 0, dp(4), 0) })
        p.addView(addBtn)
        p.addView(recBtn)
        p.addView(centerBtn)
        p.addView(hideBtn)
        p.addView(closeBtn)

        panel = p
        panelLp = params
        wm.addView(p, params)
        val (w, h) = screenSize()
        lastW = w
        lastH = h
        updatePanelButtons()
        refreshMarkers()
        ui.removeCallbacks(tick)
        ui.postDelayed(tick, 1000)
    }

    fun hidePanel() {
        ui.removeCallbacks(tick)
        stopRun()
        stopRecord(refresh = false)
        clearMarkers()
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        panelLp = null
    }

    fun isPanelVisible() = panel != null

    /** Called by the activity when mode/actions change. */
    fun onSettingsChanged() {
        if (panel == null) return
        updatePanelButtons()
        refreshMarkers()
    }

    private fun updatePanelButtons() {
        val multi = Prefs.multi
        addBtn?.visibility = if (!collapsed && multi) View.VISIBLE else View.GONE
        recBtn?.visibility = if (!collapsed && multi) View.VISIBLE else View.GONE
        centerBtn?.visibility = if (!collapsed && !multi) View.VISIBLE else View.GONE
        hideBtn?.visibility = if (collapsed) View.GONE else View.VISIBLE
        closeBtn?.visibility = if (collapsed) View.GONE else View.VISIBLE
    }

    private fun toggleCollapse() {
        collapsed = !collapsed
        if (collapsed) panelLp?.x = 0
        updatePanelButtons()
        val p = panel
        val l = panelLp
        if (p != null && l != null) runCatching { wm.updateViewLayout(p, l) }
    }

    private fun bringPanelToFront() {
        ui.post {
            val p = panel
            val l = panelLp
            if (p != null && l != null && p.isAttachedToWindow) {
                runCatching { wm.removeView(p) }
                runCatching { wm.addView(p, l) }
            }
        }
    }

    // ---------- watchdog: keeps panel/markers attached and on-screen ----------

    private val tick = object : Runnable {
        override fun run() {
            if (panel == null) return
            housekeeping()
            ui.postDelayed(this, 1000)
        }
    }

    private fun housekeeping() {
        if (panel == null) return
        val (w, h) = screenSize()
        if (w != lastW || h != lastH) {
            lastW = w
            lastH = h
            clampAll(w, h)
        }
        val p = panel
        val l = panelLp
        if (p != null && l != null && !p.isAttachedToWindow) runCatching { wm.addView(p, l) }
        for (m in markers) {
            if (!m.view.isAttachedToWindow) runCatching { wm.addView(m.view, m.params) }
        }
    }

    private fun clampAll(w: Int, h: Int) {
        for (m in markers) {
            val cx = (m.params.x + m.size / 2f).coerceIn(0f, w.toFloat())
            val cy = (m.params.y + m.size / 2f).coerceIn(0f, h.toFloat())
            m.params.x = (cx - m.size / 2f).toInt()
            m.params.y = (cy - m.size / 2f).toInt()
            runCatching { wm.updateViewLayout(m.view, m.params) }
            m.onMoved(cx, cy)
        }
        Session.persist()
        val p = panel
        val l = panelLp
        if (p != null && l != null) {
            l.x = l.x.coerceIn(0, (w - p.width).coerceAtLeast(0))
            l.y = l.y.coerceIn(0, (h - p.height).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(p, l) }
        }
    }

    // ---------- markers (target points) ----------

    private fun clearMarkers() {
        markers.forEach { runCatching { wm.removeView(it.view) } }
        markers.clear()
        markersMulti = null
    }

    private fun sanitizeSingle() {
        val (w, h) = screenSize()
        if (Prefs.singleX <= 0f || Prefs.singleX > w || Prefs.singleY <= 0f || Prefs.singleY > h) {
            Prefs.singleX = w / 2f
            Prefs.singleY = h / 2f
        }
    }

    private fun recenter() {
        val (w, h) = screenSize()
        Prefs.singleX = w / 2f
        Prefs.singleY = h / 2f
        refreshMarkers()
    }

    fun refreshMarkers() {
        if (panel == null) return
        if (recordView != null) {
            clearMarkers()
            return
        }
        val multi = Prefs.multi
        if (!multi) sanitizeSingle()
        val want = if (multi) Session.script.actions.size else 1
        if (markersMulti != multi || markers.size != want) {
            clearMarkers()
            markersMulti = multi
            if (multi) {
                Session.script.actions.forEachIndexed { i, a ->
                    val tint = if (a.type == ActionType.TAP) 0xFF1E88E5.toInt() else 0xFFFB8C00.toInt()
                    addMarker("${i + 1}", a.x, a.y, tint, false) { nx, ny ->
                        Session.script.actions.getOrNull(i)?.let { it.x = nx; it.y = ny }
                    }
                }
            } else {
                addMarker("", Prefs.singleX, Prefs.singleY, 0xFFFF5A1F.toInt(), true) { nx, ny ->
                    Prefs.singleX = nx
                    Prefs.singleY = ny
                }
            }
            bringPanelToFront()
        } else {
            markers.forEachIndexed { i, m ->
                val x: Float
                val y: Float
                if (multi) {
                    val a = Session.script.actions[i]
                    x = a.x
                    y = a.y
                } else {
                    x = Prefs.singleX
                    y = Prefs.singleY
                }
                m.params.x = (x - m.size / 2f).toInt()
                m.params.y = (y - m.size / 2f).toInt()
                runCatching { wm.updateViewLayout(m.view, m.params) }
            }
        }
        setMarkersTouchable(!running)
    }

    private fun addMarker(
        label: String, x: Float, y: Float, tint: Int, crosshair: Boolean,
        onMoved: (Float, Float) -> Unit
    ) {
        val size = dp(52)
        val v = TargetView(this, label, tint, crosshair)
        val params = lp(size, size)
        params.x = (x - size / 2f).toInt()
        params.y = (y - size / 2f).toInt()
        var sx = 0f
        var sy = 0f
        var px = 0
        var py = 0
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; px = params.x; py = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val (w, h) = screenSize()
                    params.x = (px + (e.rawX - sx).toInt()).coerceIn(-size / 2, w - size / 2)
                    params.y = (py + (e.rawY - sy).toInt()).coerceIn(-size / 2, h - size / 2)
                    runCatching { wm.updateViewLayout(v, params) }
                    onMoved(params.x + size / 2f, params.y + size / 2f)
                }
                MotionEvent.ACTION_UP -> Session.persist()
            }
            true
        }
        markers.add(MarkerRef(v, params, size, onMoved))
        runCatching { wm.addView(v, params) }
    }

    private fun setMarkersTouchable(touchable: Boolean) {
        for (m in markers) {
            m.params.flags = if (touchable) {
                m.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                m.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            runCatching { wm.updateViewLayout(m.view, m.params) }
        }
    }

    private fun addTarget() {
        val (w, h) = screenSize()
        val n = Session.script.actions.size
        Session.script.actions.add(
            ClickAction(
                ActionType.TAP,
                (w / 2f + n * dp(12)).coerceAtMost(w - dp(30).toFloat()),
                (h / 2f + n * dp(12)).coerceAtMost(h - dp(30).toFloat()),
                durationMs = Prefs.tapDurationMs,
                delayMs = Prefs.delayMs
            )
        )
        Session.persist()
        refreshMarkers()
    }

    // ---------- recording ----------

    private fun startRecord() {
        if (running) stopRun()
        clearMarkers()
        val v = View(this)
        v.setBackgroundColor(0x33FF0000)
        var downT = 0L
        var lastUp = 0L
        var dx = 0f
        var dy = 0f
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downT = SystemClock.uptimeMillis(); dx = e.rawX; dy = e.rawY
                }
                MotionEvent.ACTION_UP -> {
                    val now = SystemClock.uptimeMillis()
                    val dist = hypot(e.rawX - dx, e.rawY - dy)
                    val dur = now - downT
                    val acts = Session.script.actions
                    if (acts.isNotEmpty() && lastUp > 0L) {
                        acts.last().delayMs = (downT - lastUp).coerceAtLeast(0L)
                    }
                    if (dist < dp(16).toFloat()) {
                        acts.add(ClickAction(ActionType.TAP, dx, dy, durationMs = dur.coerceIn(1L, 500L), delayMs = Prefs.delayMs))
                    } else {
                        acts.add(ClickAction(ActionType.SWIPE, dx, dy, e.rawX, e.rawY, dur.coerceIn(50L, 3000L), Prefs.delayMs))
                    }
                    lastUp = now
                    Session.persist()
                }
            }
            true
        }
        wm.addView(v, lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
        recordView = v
        recBtn?.text = "■"
        toast("Recording: tap/swipe on screen. Press ■ to finish.")
        ui.post {
            val p = panel
            val l = panelLp
            if (p != null && l != null && recordView != null) {
                runCatching { wm.removeView(p) }
                runCatching { wm.addView(p, l) }
            }
        }
    }

    private fun stopRecord(refresh: Boolean = true) {
        val v = recordView ?: return
        runCatching { wm.removeView(v) }
        recordView = null
        recBtn?.text = "●"
        if (refresh) {
            toast("Recorded ${Session.script.actions.size} actions")
            refreshMarkers()
        }
    }

    // ---------- run loop ----------

    private fun startRun() {
        if (recordView != null) stopRecord()
        steps = if (Prefs.multi) {
            Session.script.actions.map { it.copy() }
        } else {
            listOf(
                ClickAction(
                    ActionType.TAP, Prefs.singleX, Prefs.singleY,
                    durationMs = Prefs.tapDurationMs, delayMs = Prefs.delayMs
                )
            )
        }
        if (steps.isEmpty()) {
            toast("No actions. Use + or ● first.")
            return
        }
        running = true
        idx = 0
        loopCount = 0
        startBtn?.text = "■"
        setMarkersTouchable(false)
        runHandler.postDelayed({ runStep() }, 300)
    }

    private fun stopRun() {
        if (!running) return
        running = false
        runHandler.removeCallbacksAndMessages(null)
        startBtn?.text = "▶"
        setMarkersTouchable(true)
    }

    private fun jit(v: Float): Float {
        val o = Prefs.randomOffsetPx
        return if (o > 0) (v + rnd.nextInt(2 * o + 1) - o).coerceAtLeast(0f) else v
    }

    private fun runStep() {
        if (!running) return
        val a = steps[idx]
        val path = Path()
        path.moveTo(jit(a.x), jit(a.y))
        if (a.type == ActionType.SWIPE) path.lineTo(jit(a.x2), jit(a.y2))
        val stroke = GestureDescription.StrokeDescription(path, 0L, a.durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = advance(a)
            override fun onCancelled(gestureDescription: GestureDescription?) = advance(a)
        }
        val ok = runCatching { dispatchGesture(gesture, cb, null) }.getOrDefault(false)
        if (!ok) advance(a)
    }

    private fun advance(a: ClickAction) {
        if (!running) return
        idx++
        if (idx >= steps.size) {
            idx = 0
            loopCount++
            if (Prefs.loops > 0 && loopCount >= Prefs.loops) {
                stopRun()
                return
            }
        }
        val ri = Prefs.randomIntervalMs
        val extra = if (ri > 0) rnd.nextInt(2 * ri + 1) - ri else 0
        val d = (a.delayMs + extra).coerceAtLeast(0L)
        runHandler.postDelayed({ runStep() }, d)
    }
}
