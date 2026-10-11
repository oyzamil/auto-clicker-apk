package com.example.openclicker

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

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
}

class MainActivity : Activity() {

    private lateinit var dot: View
    private lateinit var statusText: TextView
    private lateinit var panelBtn: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var actionsCard: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var countText: TextView
    private lateinit var segSingle: TextView
    private lateinit var segMulti: TextView
    private lateinit var delayInput: EditText
    private val reqExport = 1
    private val reqImport = 2

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Session.init(this)
        window.statusBarColor = P.bg
        window.navigationBarColor = P.bg
        setContentView(build())
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

    private fun field(initial: String): EditText {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(initial)
        e.setTextColor(P.text)
        e.setHintTextColor(P.sub)
        e.background = rr(P.field, 12)
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        return e
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    // ---------- screen ----------

    private fun build(): View {
        val sv = ScrollView(this)
        sv.setBackgroundColor(P.bg)
        val root = vbox()
        root.setPadding(dp(16), dp(20), dp(16), dp(32))

        root.addView(header(), lpm(16))
        root.addView(statusCard(), lpm())
        setupCard = setupCard()
        root.addView(setupCard, lpm())
        root.addView(modeCard(), lpm())
        root.addView(timingCard(), lpm())
        root.addView(advancedCard(), lpm())
        actionsCard = actionsCard()
        root.addView(actionsCard, lpm())

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
        col.addView(text("Precision auto clicker", 13f, 0xCCFFFFFF.toInt()))
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
            "Installed apps → Open Clicker → turn ON. Used only to perform taps and swipes.",
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
        val seg = hbox()
        seg.background = rr(P.field, 14)
        seg.setPadding(dp(4), dp(4), dp(4), dp(4))
        segSingle = text("Single target", 14f, P.text, true)
        segMulti = text("Multi target", 14f, P.text, true)
        for (s in listOf(segSingle, segMulti)) {
            s.gravity = Gravity.CENTER
            s.setPadding(dp(8), dp(10), dp(8), dp(10))
            seg.addView(s, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        segSingle.setOnClickListener { setMode(false) }
        segMulti.setOnClickListener { setMode(true) }
        styleSeg()
        c.addView(seg, lpm(0))
        return c
    }

    private fun styleSeg() {
        val m = Prefs.multi
        segSingle.background = if (!m) rr(P.accent, 10) else null
        segMulti.background = if (m) rr(P.accent, 10) else null
        segSingle.setTextColor(if (!m) Color.WHITE else P.sub)
        segMulti.setTextColor(if (m) Color.WHITE else P.sub)
    }

    private fun setMode(multi: Boolean) {
        Prefs.multi = multi
        styleSeg()
        actionsCard.visibility = if (multi) View.VISIBLE else View.GONE
        ClickerService.instance?.onSettingsChanged()
    }

    private fun timingCard(): View {
        val c = card()
        c.addView(text("Delay between clicks", 16f, P.text, true))
        val hint = text("Milliseconds. 1000 ms = 1 second.", 12f, P.sub)
        hint.setPadding(0, dp(2), 0, dp(12))
        c.addView(hint)

        val row = hbox()
        val minus = button("−", false) { stepDelay(-1) }
        val plus = button("+", false) { stepDelay(1) }
        delayInput = field(Prefs.delayMs.toString())
        delayInput.textSize = 26f
        delayInput.typeface = Typeface.DEFAULT_BOLD
        delayInput.gravity = Gravity.CENTER
        delayInput.onChange { Prefs.delayMs = it.toLongOrNull() ?: 0L }
        row.addView(minus, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(delayInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(10), 0, dp(10), 0)
        })
        row.addView(plus, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        c.addView(row, lpm(10))

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val chips = hbox()
        for (v in listOf(10, 50, 100, 250, 500, 1000, 2000)) {
            val chip = text("$v ms", 13f, P.text, true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(dp(14), dp(8), dp(14), dp(8))
            chip.background = rr(P.field, 20)
            chip.setOnClickListener { delayInput.setText(v.toString()) }
            chips.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8) })
        }
        scroll.addView(chips)
        c.addView(scroll)
        return c
    }

    private fun stepDelay(dir: Int) {
        val cur = delayInput.text.toString().toLongOrNull() ?: 0L
        val step = when {
            cur < 100 -> 10L
            cur < 1000 -> 50L
            else -> 100L
        }
        delayInput.setText((cur + dir * step).coerceAtLeast(0L).toString())
    }

    private fun advancedCard(): View {
        val c = card()
        c.addView(text("Advanced", 16f, P.text, true), lpm(4))
        numRow(c, "Tap duration (ms)", { Prefs.tapDurationMs }, { Prefs.tapDurationMs = it })
        numRow(c, "Random position ± (px)", { Prefs.randomOffsetPx.toLong() }, { Prefs.randomOffsetPx = it.toInt() })
        numRow(c, "Random interval ± (ms)", { Prefs.randomIntervalMs.toLong() }, { Prefs.randomIntervalMs = it.toInt() })
        numRow(c, "Loops (0 = infinite)", { Prefs.loops.toLong() }, { Prefs.loops = it.toInt() })
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
        c.visibility = if (Prefs.multi) View.VISIBLE else View.GONE

        val head = hbox()
        head.addView(text("Actions", 16f, P.text, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        countText = text("0", 13f, P.accent, true)
        head.addView(countText)
        c.addView(head)
        val hint = text("Add targets with + on the floating panel, or record with ●. Tap a row to edit.", 12f, P.sub)
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
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        r1.addView(button("Load", false) { loadDialog() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
        c.addView(r1, lpm(8))

        val r2 = hbox()
        r2.addView(button("Export", false) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "clicker-script.json")
            startActivityForResult(i, reqExport)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        r2.addView(button("Import", false) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, reqImport)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
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
            badge.background = oval(if (a.type == ActionType.TAP) P.blue else P.amber)
            row.addView(badge, LinearLayout.LayoutParams(dp(28), dp(28)))

            val col = vbox()
            col.setPadding(dp(12), 0, 0, 0)
            val title = if (a.type == ActionType.TAP)
                "Tap  (${a.x.toInt()}, ${a.y.toInt()})"
            else
                "Swipe  (${a.x.toInt()}, ${a.y.toInt()}) → (${a.x2.toInt()}, ${a.y2.toInt()})"
            col.addView(text(title, 14f, P.text, true))
            col.addView(text("duration ${a.durationMs} ms  ·  delay ${a.delayMs} ms", 12f, P.sub))
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

        fun input(label: String, v: Number): EditText {
            val t = text(label, 12f, P.sub)
            t.setPadding(0, dp(10), 0, dp(2))
            val e = EditText(this)
            e.inputType = InputType.TYPE_CLASS_NUMBER
            e.setText(v.toString())
            box.addView(t)
            box.addView(e)
            return e
        }

        val ex = input("X", a.x.toInt())
        val ey = input("Y", a.y.toInt())
        var ex2: EditText? = null
        var ey2: EditText? = null
        if (a.type == ActionType.SWIPE) {
            ex2 = input("X end", a.x2.toInt())
            ey2 = input("Y end", a.y2.toInt())
        }
        val ed = input("Duration (ms)", a.durationMs)
        val el = input("Delay after this action (ms)", a.delayMs)

        AlertDialog.Builder(this)
            .setTitle("Action ${i + 1} · ${a.type.name.lowercase()}")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                a.x = ex.text.toString().toFloatOrNull() ?: a.x
                a.y = ey.text.toString().toFloatOrNull() ?: a.y
                ex2?.let { a.x2 = it.text.toString().toFloatOrNull() ?: a.x2 }
                ey2?.let { a.y2 = it.text.toString().toFloatOrNull() ?: a.y2 }
                a.durationMs = (ed.text.toString().toLongOrNull() ?: a.durationMs).coerceAtLeast(1L)
                a.delayMs = (el.text.toString().toLongOrNull() ?: a.delayMs).coerceAtLeast(0L)
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
