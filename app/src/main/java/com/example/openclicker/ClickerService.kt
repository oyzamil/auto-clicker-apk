package com.example.openclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()

    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var startBtn: TextView? = null
    private var addBtn: TextView? = null
    private var recBtn: TextView? = null
    private var hideBtn: TextView? = null
    private var closeBtn: TextView? = null
    private var collapsed = false

    private val markers = mutableListOf<Pair<View, WindowManager.LayoutParams>>()
    private var recordView: View? = null

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

    override fun onUnbind(intent: Intent?): Boolean {
        hidePanel()
        instance = null
        return super.onUnbind(intent)
    }

    // ---------- helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
            setColor(0xE6202020.toInt())
            cornerRadius = dp(24).toFloat()
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
                        params.x = px + (e.rawX - sx).toInt()
                        params.y = py + (e.rawY - sy).toInt()
                        wm.updateViewLayout(p, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && collapsed) toggleCollapse()
                }
            }
            true
        }

        startBtn = btn("▶") { if (running) stopRun() else startRun() }
        addBtn = btn("+") { addTarget() }
        recBtn = btn("●") { if (recordView != null) stopRecord() else startRecord() }
        hideBtn = btn("◀") { toggleCollapse() }
        closeBtn = btn("✕") { hidePanel() }

        p.addView(handle)
        p.addView(startBtn)
        p.addView(addBtn)
        p.addView(recBtn)
        p.addView(hideBtn)
        p.addView(closeBtn)

        panel = p
        panelLp = params
        wm.addView(p, params)
        updatePanelButtons()
        refreshMarkers()
    }

    fun hidePanel() {
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
        hideBtn?.visibility = if (collapsed) View.GONE else View.VISIBLE
        closeBtn?.visibility = if (collapsed) View.GONE else View.VISIBLE
    }

    private fun toggleCollapse() {
        collapsed = !collapsed
        if (collapsed) panelLp?.x = 0
        updatePanelButtons()
        val p = panel
        val l = panelLp
        if (p != null && l != null) wm.updateViewLayout(p, l)
    }

    // ---------- markers (target points) ----------

    private fun clearMarkers() {
        markers.forEach { runCatching { wm.removeView(it.first) } }
        markers.clear()
    }

    fun refreshMarkers() {
        if (panel == null) return
        clearMarkers()
        if (recordView != null) return
        val dm = resources.displayMetrics
        if (Prefs.multi) {
            Session.script.actions.forEachIndexed { i, a ->
                val color = if (a.type == ActionType.TAP) 0xCC1565C0.toInt() else 0xCCE65100.toInt()
                addMarker("${i + 1}", a.x, a.y, color) { nx, ny -> a.x = nx; a.y = ny }
            }
        } else {
            if (Prefs.singleX <= 0f) {
                Prefs.singleX = dm.widthPixels / 2f
                Prefs.singleY = dm.heightPixels / 2f
            }
            addMarker("◎", Prefs.singleX, Prefs.singleY, 0xCCC62828.toInt()) { nx, ny ->
                Prefs.singleX = nx; Prefs.singleY = ny
            }
        }
        setMarkersTouchable(!running)
    }

    private fun addMarker(label: String, x: Float, y: Float, color: Int, onMoved: (Float, Float) -> Unit) {
        val size = dp(48)
        val tv = TextView(this)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.textSize = 14f
        tv.alpha = 0.85f
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }
        val params = lp(size, size)
        params.x = (x - size / 2).toInt()
        params.y = (y - size / 2).toInt()
        var sx = 0f
        var sy = 0f
        var px = 0
        var py = 0
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; px = params.x; py = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = px + (e.rawX - sx).toInt()
                    params.y = py + (e.rawY - sy).toInt()
                    wm.updateViewLayout(tv, params)
                    onMoved((params.x + size / 2).toFloat(), (params.y + size / 2).toFloat())
                }
                MotionEvent.ACTION_UP -> Session.persist()
            }
            true
        }
        wm.addView(tv, params)
        markers.add(tv to params)
    }

    private fun setMarkersTouchable(touchable: Boolean) {
        for ((v, p) in markers) {
            p.flags = if (touchable) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { wm.updateViewLayout(v, p) }
        }
    }

    private fun addTarget() {
        val dm = resources.displayMetrics
        val n = Session.script.actions.size
        Session.script.actions.add(
            ClickAction(
                ActionType.TAP,
                dm.widthPixels / 2f + n * dp(12),
                dm.heightPixels / 2f + n * dp(12),
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
        // keep panel above the record layer
        handler.post {
            val p = panel
            val l = panelLp
            if (p != null && l != null && recordView != null) {
                runCatching { wm.removeView(p) }
                wm.addView(p, l)
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
        handler.postDelayed({ runStep() }, 300)
    }

    private fun stopRun() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
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
        handler.postDelayed({ runStep() }, d)
    }
}
