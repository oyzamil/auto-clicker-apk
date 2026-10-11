package com.oyzamil.openclicker

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

private object P {
    val bg = 0xFF0F1117.toInt()
    val card = 0xFF1A1D26.toInt()
    val field = 0xFF262A36.toInt()
    val accent = 0xFFFF7A00.toInt()
    val accent2 = 0xFFFF4D1A.toInt()
    val text = 0xFFF2F4F8.toInt()
    val sub = 0xFF9AA3B2.toInt()
    val ok = 0xFF2ECC71.toInt()
    val bad = 0xFFFF5252.toInt()
    val blue = 0xFF1E88E5.toInt()
    val amber = 0xFFFB8C00.toInt()
    val green = 0xFF43A047.toInt()
}

class MainActivity : Activity() {

    private class Seg(val root: LinearLayout, val refresh: () -> Unit)

    private lateinit var focusHolder: LinearLayout
    private lateinit var dot: View
    private lateinit var statusText: TextView
    private lateinit var panelBtn: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var singleCard: LinearLayout
    private lateinit var actionsCard: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var countText: TextView
    private lateinit var convText: TextView
    private lateinit var singleHint: TextView
    private lateinit var modeSeg: Seg
    private lateinit var typeSeg: Seg
    private lateinit var dirSeg: Seg
    private lateinit var delayInput: TimeInput
    private val reqExport = 1
    private val reqImport = 2
    private val factors = longArrayOf(1L, 1000L, 60000L)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Session.init(this)
        window.statusBarColor = P.bg
        window.navigationBarColor = P.bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(build())
        focusHolder.post { focusHolder.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshList()
    }

    override fun onPause() {
        super.onPause()
        ClickerService.instance?.onSettingsChanged()
    }

    // ---------- small UI toolkit ----------

    private fun rr(color: Int, radius: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun hbox() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun card(): LinearLayout = vbox().apply {
        background = rr(P.card, 18)
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun lpm(bottom: Int = 12) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(bottom) }

    private fun weight(left: Int = 0, right: Int = 0) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(left)
            rightMargin = dp(right)
        }

    private fun text(t: String, sp: Float = 14f, color: Int = P.text, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sp
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit): TextView {
        val b = text(label, 14f, if (filled) Color.WHITE else P.accent, true)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), dp(12), dp(14), dp(12))
        val bg: GradientDrawable = if (filled) {
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(P.accent, P.accent2))
                .apply { cornerRadius = dp(12).toFloat() }
        } else {
            rr(Color.TRANSPARENT, 12, P.accent, 1)
        }
        b.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun EditText.onChange(cb: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = cb(s?.toString() ?: "")
        })
    }

    private fun hideKeyboardAndClear(v: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
        v.clearFocus()
        if (::focusHolder.isInitialized) focusHolder.requestFocus()
    }

    private fun field(initial: String): EditText {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(initial)
        e.setTextColor(P.text)
        e.setHintTextColor(P.sub)
        e.background = rr(P.field, 12)
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        e.imeOptions = EditorInfo.IME_ACTION_DONE
        e.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboardAndClear(v)
                true
            } else {
                false
            }
        }
        return e
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun segmented(labels: List<String>, selected: () -> Int, onSelect: (Int) -> Unit): Seg {
        val root = hbox()
        root.background = rr(P.field, 14)
        root.setPadding(dp(4), dp(4), dp(4), dp(4))
        val views = labels.mapIndexed { i, l ->
            val v = text(l, 14f, P.sub, true)
            v.gravity = Gravity.CENTER
            v.setPadding(dp(6), dp(10), dp(6), dp(10))
            v.setOnClickListener { onSelect(i) }
            v
        }
        views.forEach { root.addView(it, weight()) }
        val refresh = {
            views.forEachIndexed { i, v ->
                val sel = i == selected()
                v.background = if (sel) rr(P.accent, 10) else null
                v.setTextColor(if (sel) Color.WHITE else P.sub)
            }
        }
        refresh()
        return Seg(root, refresh)
    }

    // ---------- time helpers ----------

    private fun fmt(ms: Long, unit: Int): String {
        val v = ms.toDouble() / factors[unit]
        return if (v == Math.floor(v)) v.toLong().toString()
        else String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
    }

    private fun pickUnit(ms: Long): Int = when {
        ms > 0 && ms % 60000L == 0L -> 2
        ms >= 1000 && ms % 1000L == 0L -> 1
        else -> 0
    }

    private fun humanMs(ms: Long): String = when {
        ms > 0 && ms % 60000L == 0L -> "${ms / 60000} min"
        ms >= 1000 && ms % 1000L == 0L -> "${ms / 1000} s"
        else -> "$ms ms"
    }

    private fun unitSpinner(selected: Int, onSel: (Int) -> Unit): Spinner {
        val items = arrayOf("ms", "sec", "min")
        val activity = this
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.text = items[position] + " ▾"
                v.setTextColor(P.text)
                v.textSize = 15f
                v.typeface = Typeface.DEFAULT_BOLD
                v.gravity = Gravity.CENTER
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(P.text)
                v.setPadding(dp(18), dp(12), dp(18), dp(12))
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val sp = Spinner(this)
        sp.adapter = adapter
        sp.setSelection(selected)
        sp.background = rr(P.field, 12)
        sp.setPopupBackgroundDrawable(rr(P.card, 12))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSel(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        return sp
    }

    /** Number input + unit selector (ms / sec / min). Value is always reported in milliseconds. */
    private inner class TimeInput(ms0: Long, unit0: Int, big: Boolean, private val onChanged: (Long, Int) -> Unit) {
        val root = hbox()
        private val edit = field("")
        private var unit = unit0.coerceIn(0, 2)
        private var guard = false
        private val spinner: Spinner

        init {
            edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (big) {
                edit.textSize = 26f
                edit.typeface = Typeface.DEFAULT_BOLD
                edit.gravity = Gravity.CENTER
            }
            guard = true
            edit.setText(fmt(ms0, unit))
            guard = false
            edit.onChange { if (!guard) onChanged(getMs(), unit) }
            spinner = unitSpinner(unit) { u ->
                if (u != unit) {
                    unit = u
                    onChanged(getMs(), unit)
                }
            }
            root.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(
                spinner,
                LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.MATCH_PARENT).apply { leftMargin = dp(8) }
            )
        }

        fun unitIdx() = unit

        fun getMs(): Long {
            val v = edit.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            return Math.round(v * factors[unit]).coerceAtLeast(0L)
        }

        fun setMs(ms: Long, autoUnit: Boolean) {
            if (autoUnit) unit = pickUnit(ms)
            guard = true
            spinner.setSelection(unit)
            edit.setText(fmt(ms, unit))
            guard = false
            onChanged(ms, unit)
        }
    }

    // ---------- screen ----------

    private fun build(): View {
        val sv = ScrollView(this)
        sv.setBackgroundColor(P.bg)
        val root = vbox()
        root.setPadding(dp(16), dp(20), dp(16), dp(32))
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        focusHolder = root

        root.addView(header(), lpm(16))
        root.addView(statusCard(), lpm())
        setupCard = setupCard()
        root.addView(setupCard, lpm())
        root.addView(modeCard(), lpm())
        singleCard = singleCard()
        root.addView(singleCard, lpm())
        root.addView(timingCard(), lpm())
        root.addView(advancedCard(), lpm())
        actionsCard = actionsCard()
        root.addView(actionsCard, lpm())

        applyModeVisibility()
        sv.addView(root)
        return sv
    }

    private fun header(): View {
        val row = hbox()
        row.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(P.accent, P.accent2)
        ).apply { cornerRadius = dp(20).toFloat() }
        row.setPadding(dp(16), dp(16), dp(16), dp(16))

        val iv = ImageView(this)
        iv.setImageResource(R.drawable.ic_launcher_art)
        iv.clipToOutline = true
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
            }
        }
        row.addView(iv, LinearLayout.LayoutParams(dp(64), dp(64)))

        val col = vbox()
        col.setPadding(dp(14), 0, 0, 0)
        col.addView(text("Open Clicker", 22f, Color.WHITE, true))
        col.addView(text("Click · Slide · Scroll", 13f, 0xCCFFFFFF.toInt()))
        row.addView(col)
        return row
    }

    private fun statusCard(): View {
        val c = card()
        val row = hbox()
        dot = View(this)
        row.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)))
        statusText = text("", 15f, P.text, true)
        statusText.setPadding(dp(10), 0, 0, 0)
        row.addView(statusText)
        c.addView(row, lpm(12))

        panelBtn = button("Show floating panel", true) {
            val s = ClickerService.instance
            if (s == null) {
                toast("Enable the accessibility service first")
            } else {
                if (s.isPanelVisible()) {
                    s.hidePanel()
                } else {
                    s.showPanel()
                    s.onSettingsChanged()
                    moveTaskToBack(true)
                }
                updateStatus()
            }
        }
        c.addView(panelBtn, lpm(0))
        return c
    }

    private fun updateStatus() {
        val on = ClickerService.instance != null
        dot.background = oval(if (on) P.ok else P.bad)
        statusText.text = if (on) "Service active" else "Service not enabled"
        val visible = ClickerService.instance?.isPanelVisible() == true
        panelBtn.text = if (visible) "Hide floating panel" else "Show floating panel"
        setupCard.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun setupCard(): LinearLayout {
        val c = card()
        c.addView(text("Setup", 16f, P.text, true), lpm(8))
        var n = 1
        if (Build.VERSION.SDK_INT >= 33) {
            step(
                c, n++, "Allow restricted settings",
                "App info → ⋮ (top right) → Allow restricted settings. Needed once if the app was installed from an APK file.",
                "Open App info"
            ) {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        step(
            c, n, "Enable accessibility service",
            "Installed apps → Open Clicker → turn ON. Used only to perform taps, slides and scrolls.",
            "Open Accessibility"
        ) {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return c
    }

    private fun step(parent: LinearLayout, n: Int, title: String, desc: String, label: String, action: () -> Unit) {
        val row = hbox()
        row.gravity = Gravity.TOP
        val badge = text("$n", 13f, Color.WHITE, true)
        badge.gravity = Gravity.CENTER
        badge.background = oval(P.accent)
        row.addView(badge, LinearLayout.LayoutParams(dp(26), dp(26)))
        val col = vbox()
        col.setPadding(dp(12), 0, 0, 0)
        col.addView(text(title, 14f, P.text, true))
        val d = text(desc, 12f, P.sub)
        d.setPadding(0, dp(2), 0, dp(8))
        col.addView(d)
        col.addView(button(label, false, action))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row, lpm(12))
    }

    private fun modeCard(): View {
        val c = card()
        c.addView(text("Mode", 16f, P.text, true), lpm(10))
        modeSeg = segmented(listOf("Single target", "Multi target"), { if (Prefs.multi) 1 else 0 }) { i ->
            Prefs.multi = (i == 1)
            modeSeg.refresh()
            applyModeVisibility()
            ClickerService.instance?.onSettingsChanged()
        }
        c.addView(modeSeg.root, lpm(0))
        return c
    }

    private fun applyModeVisibility() {
        singleCard.visibility = if (Prefs.multi) View.GONE else View.VISIBLE
        actionsCard.visibility = if (Prefs.multi) View.VISIBLE else View.GONE
    }

    private fun singleCard(): LinearLayout {
        val c = card()
        c.addView(text("Action", 16f, P.text, true), lpm(10))
        typeSeg = segmented(listOf("Click", "Slide", "Scroll"), { Prefs.singleType }) { i ->
            Prefs.singleType = i
            styleSingle()
            ClickerService.instance?.onSettingsChanged()
        }
        c.addView(typeSeg.root, lpm(8))

        dirSeg = segmented(
            listOf("↑ Up", "↓ Down", "← Left", "→ Right"), { Prefs.scrollDir }
        ) { i ->
            Prefs.scrollDir = i
            styleSingle()
            ClickerService.instance?.onSettingsChanged()
        }
        c.addView(dirSeg.root, lpm(8))

        singleHint = text("", 12f, P.sub)
        c.addView(singleHint)
        styleSingle()
        return c
    }

    private fun styleSingle() {
        typeSeg.refresh()
        dirSeg.refresh()
        val t = Prefs.singleType
        dirSeg.root.visibility = if (t == 2) View.VISIBLE else View.GONE
        singleHint.text = when (t) {
            0 -> "Drag the ◎ marker to the point you want to click."
            1 -> "Drag S (start) and E (end). The finger slides from S to E."
            else -> "Drag the marker to where the scroll starts. Direction is where the content moves to (↓ shows lower content)."
        }
    }

    private fun timingCard(): View {
        val c = card()
        c.addView(text("Delay between actions", 16f, P.text, true))
        val hint = text("Choose ms, sec or min. It is converted to milliseconds automatically.", 12f, P.sub)
        hint.setPadding(0, dp(2), 0, dp(12))
        c.addView(hint)

        convText = text("", 13f, P.accent, true)
        convText.gravity = Gravity.CENTER

        delayInput = TimeInput(Prefs.delayMs, Prefs.delayUnit, true) { ms, unit ->
            Prefs.delayMs = ms
            Prefs.delayUnit = unit
            updateConv(ms)
        }
        c.addView(delayInput.root, lpm(10))

        val row = hbox()
        row.addView(button("−", false) { stepDelay(-1) }, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(convText, weight(8, 8))
        row.addView(button("+", false) { stepDelay(1) }, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        c.addView(row, lpm(12))
        updateConv(Prefs.delayMs)

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val chips = hbox()
        val presets = listOf(10L, 50L, 100L, 250L, 500L, 1000L, 2000L, 60000L, 180000L, 300000L)
        for (ms in presets) {
            val chip = text(humanMs(ms), 13f, P.text, true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(dp(14), dp(8), dp(14), dp(8))
            chip.background = rr(P.field, 20)
            chip.setOnClickListener {
                delayInput.setMs(ms, true)
                hideKeyboardAndClear(chip)
            }
            chips.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(8) }
            )
        }
        scroll.addView(chips)
        c.addView(scroll)
        return c
    }

    private fun updateConv(ms: Long) {
        convText.text = "= ${String.format(Locale.US, "%,d", ms)} ms"
    }

    private fun stepDelay(dir: Int) {
        val cur = delayInput.getMs()
        val step = when (delayInput.unitIdx()) {
            0 -> if (cur < 100) 10L else if (cur < 1000) 50L else 100L
            1 -> if (cur < 1000) 100L else 1000L
            else -> if (cur < 60000) 10000L else 60000L
        }
        delayInput.setMs((cur + dir * step).coerceAtLeast(0L), false)
    }

    private fun advancedCard(): View {
        val c = card()
        c.addView(text("Advanced", 16f, P.text, true), lpm(4))
        numRow(c, "Repeat count (0 = infinite)", { Prefs.loops.toLong() }, { Prefs.loops = it.toInt() })
        val hint = text("Single mode: how many times the click / slide / scroll is performed. Multi mode: how many times the whole sequence runs.", 11f, P.sub)
        hint.setPadding(0, dp(4), 0, dp(4))
        c.addView(hint)
        numRow(c, "Tap duration (ms)", { Prefs.tapDurationMs }, { Prefs.tapDurationMs = it })
        numRow(c, "Slide / scroll duration (ms)", { Prefs.swipeDurationMs }, { Prefs.swipeDurationMs = it })
        numRow(c, "Scroll distance (% of screen)", { Prefs.scrollDistPct.toLong() }, { Prefs.scrollDistPct = it.toInt() })
        numRow(c, "Random position ± (px)", { Prefs.randomOffsetPx.toLong() }, { Prefs.randomOffsetPx = it.toInt() })
        numRow(c, "Random interval ± (ms)", { Prefs.randomIntervalMs.toLong() }, { Prefs.randomIntervalMs = it.toInt() })
        return c
    }

    private fun numRow(parent: LinearLayout, label: String, get: () -> Long, set: (Long) -> Unit) {
        val row = hbox()
        row.setPadding(0, dp(8), 0, 0)
        val t = text(label, 14f, P.sub)
        val e = field(get().toString())
        e.gravity = Gravity.END
        e.onChange { set(it.toLongOrNull() ?: 0L) }
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(e, LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(row)
    }

    private fun actionsCard(): LinearLayout {
        val c = card()

        val head = hbox()
        head.addView(text("Actions", 16f, P.text, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        countText = text("0", 13f, P.accent, true)
        head.addView(countText)
        c.addView(head)
        val hint = text("On the floating panel: + click, ⇄ slide, ⇅ scroll, ● record. Tap a row to edit.", 12f, P.sub)
        hint.setPadding(0, dp(2), 0, dp(8))
        c.addView(hint)

        listBox = vbox()
        c.addView(listBox, lpm(8))

        val r1 = hbox()
        r1.addView(button("Save", false) {
            promptName { n ->
                ScriptStore.save(this, Script(n, Session.script.actions.map { it.copy() }.toMutableList()))
                toast("Saved: $n")
            }
        }, weight(0, 4))
        r1.addView(button("Load", false) { loadDialog() }, weight(4, 0))
        c.addView(r1, lpm(8))

        val r2 = hbox()
        r2.addView(button("Export", false) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "clicker-script.json")
            startActivityForResult(i, reqExport)
        }, weight(0, 4))
        r2.addView(button("Import", false) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, reqImport)
        }, weight(4, 0))
        c.addView(r2, lpm(8))

        c.addView(button("Clear all actions", false) {
            AlertDialog.Builder(this)
                .setTitle("Clear all actions?")
                .setPositiveButton("Clear") { _, _ ->
                    Session.script.actions.clear()
                    Session.persist()
                    refreshList()
                    ClickerService.instance?.refreshMarkers()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        return c
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val acts = Session.script.actions
        countText.text = "${acts.size} total"
        if (acts.isEmpty()) {
            listBox.addView(text("No actions yet.", 13f, P.sub))
            return
        }
        acts.forEachIndexed { i, a ->
            val row = hbox()
            row.background = rr(P.field, 12)
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            val badge = text("${i + 1}", 13f, Color.WHITE, true)
            badge.gravity = Gravity.CENTER
            badge.background = oval(
                when (a.type) {
                    ActionType.TAP -> P.blue
                    ActionType.SWIPE -> P.amber
                    ActionType.SCROLL -> P.green
                }
            )
            row.addView(badge, LinearLayout.LayoutParams(dp(28), dp(28)))

            val col = vbox()
            col.setPadding(dp(12), 0, 0, 0)
            val title = when (a.type) {
                ActionType.TAP -> "Click  (${a.x.toInt()}, ${a.y.toInt()})"
                ActionType.SWIPE -> "Slide  (${a.x.toInt()}, ${a.y.toInt()}) → (${a.x2.toInt()}, ${a.y2.toInt()})"
                ActionType.SCROLL -> "Scroll ${ScrollDir.arrow(a.dir)} ${a.distPct}%  (${a.x.toInt()}, ${a.y.toInt()})"
            }
            col.addView(text(title, 14f, P.text, true))
            val rep = if (a.repeat > 1) "  ·  ×${a.repeat}" else ""
            col.addView(text("duration ${a.durationMs} ms  ·  delay ${humanMs(a.delayMs)}$rep", 12f, P.sub))
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("›", 20f, P.sub))
            row.setOnClickListener { editDialog(i) }
            listBox.addView(row, lpm(8))
        }
    }

    // ---------- dialogs ----------

    private fun editDialog(i: Int) {
        val acts = Session.script.actions
        if (i !in acts.indices) return
        val a = acts[i]
        val box = vbox()
        box.setPadding(dp(20), dp(8), dp(20), 0)

        fun label(t: String) {
            val v = text(t, 12f, P.sub)
            v.setPadding(0, dp(10), 0, dp(2))
            box.addView(v)
        }

        fun input(label: String, v: Number): EditText {
            label(label)
            val e = EditText(this)
            e.inputType = InputType.TYPE_CLASS_NUMBER
            e.setText(v.toString())
            box.addView(e)
            return e
        }

        val ex = input("X", a.x.toInt())
        val ey = input("Y", a.y.toInt())
        var ex2: EditText? = null
        var ey2: EditText? = null
        var dirSpinner: Spinner? = null
        var edist: EditText? = null
        if (a.type == ActionType.SWIPE) {
            ex2 = input("X end", a.x2.toInt())
            ey2 = input("Y end", a.y2.toInt())
        }
        if (a.type == ActionType.SCROLL) {
            label("Scroll direction")
            val names = listOf(ScrollDir.UP, ScrollDir.DOWN, ScrollDir.LEFT, ScrollDir.RIGHT)
                .map { ScrollDir.arrow(it) + " " + ScrollDir.name(it) }
            val sp = Spinner(this)
            val ad = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sp.adapter = ad
            sp.setSelection(a.dir)
            box.addView(sp)
            dirSpinner = sp
            edist = input("Distance (% of screen)", a.distPct)
        }
        val ed = input("Duration (ms)", a.durationMs)
        val er = input("Repeat (times before next action)", a.repeat)
        label("Delay after this action")
        val delayTi = TimeInput(a.delayMs, pickUnit(a.delayMs), false) { _, _ -> }
        box.addView(delayTi.root)

        val scroll = ScrollView(this)
        scroll.addView(box)

        AlertDialog.Builder(this)
            .setTitle("Action ${i + 1} · ${a.type.name.lowercase()}")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                a.x = ex.text.toString().toFloatOrNull() ?: a.x
                a.y = ey.text.toString().toFloatOrNull() ?: a.y
                ex2?.let { a.x2 = it.text.toString().toFloatOrNull() ?: a.x2 }
                ey2?.let { a.y2 = it.text.toString().toFloatOrNull() ?: a.y2 }
                dirSpinner?.let { a.dir = it.selectedItemPosition.coerceIn(0, 3) }
                edist?.let { a.distPct = (it.text.toString().toIntOrNull() ?: a.distPct).coerceIn(1, 100) }
                a.durationMs = (ed.text.toString().toLongOrNull() ?: a.durationMs).coerceAtLeast(1L)
                a.repeat = (er.text.toString().toIntOrNull() ?: a.repeat).coerceAtLeast(1)
                a.delayMs = delayTi.getMs()
                Session.persist()
                refreshList()
                ClickerService.instance?.refreshMarkers()
            }
            .setNeutralButton("Delete") { _, _ ->
                acts.removeAt(i)
                Session.persist()
                refreshList()
                ClickerService.instance?.refreshMarkers()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptName(onName: (String) -> Unit) {
        val e = EditText(this)
        e.hint = "Script name"
        AlertDialog.Builder(this)
            .setTitle("Save script")
            .setView(e)
            .setPositiveButton("Save") { _, _ ->
                val n = e.text.toString().trim()
                if (n.isNotEmpty()) onName(n)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadDialog() {
        val names = ScriptStore.names(this)
        if (names.isEmpty()) {
            toast("No saved scripts")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Saved scripts")
            .setItems(names.toTypedArray()) { _, which ->
                val n = names[which]
                AlertDialog.Builder(this)
                    .setTitle(n)
                    .setPositiveButton("Load") { _, _ ->
                        val s = ScriptStore.load(this, n)
                        if (s == null) {
                            toast("Load failed")
                        } else {
                            Session.script = s
                            Session.persist()
                            refreshList()
                            ClickerService.instance?.refreshMarkers()
                        }
                    }
                    .setNeutralButton("Delete") { _, _ -> ScriptStore.delete(this, n) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            if (requestCode == reqExport) {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(Session.script.toJson().toString(2).toByteArray())
                }
                toast("Exported")
            } else if (requestCode == reqImport) {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                Session.script = Script.fromJson(JSONObject(text))
                Session.persist()
                refreshList()
                ClickerService.instance?.refreshMarkers()
                toast("Imported ${Session.script.actions.size} actions")
            }
        } catch (e: Exception) {
            toast("Failed: ${e.message}")
        }
    }
}
