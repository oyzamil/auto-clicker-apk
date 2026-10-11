package com.example.openclicker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var multiSection: LinearLayout
    private val reqExport = 1
    private val reqImport = 2

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Session.init(this)
        setContentView(buildUi())
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

    private fun updateStatus() {
        status.text = if (ClickerService.instance != null) "Service: ON" else "Service: OFF — enable in Accessibility settings"
    }

    private fun buildUi(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(24), dp(20), dp(24))

        val title = TextView(this)
        title.text = "Open Clicker"
        title.textSize = 24f
        root.addView(title)

        status = TextView(this)
        status.setPadding(0, dp(8), 0, dp(8))
        root.addView(status)

        root.addView(button("Open Accessibility settings") {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("Show floating panel") {
            val s = ClickerService.instance
            if (s == null) {
                toast("Enable the Open Clicker accessibility service first")
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                s.showPanel()
                s.onSettingsChanged()
                moveTaskToBack(true)
            }
        })
        root.addView(button("Hide floating panel") { ClickerService.instance?.hidePanel() })

        // mode
        val modeLabel = TextView(this)
        modeLabel.text = "Mode"
        modeLabel.setPadding(0, dp(16), 0, 0)
        root.addView(modeLabel)
        val group = RadioGroup(this)
        group.orientation = RadioGroup.HORIZONTAL
        val rSingle = RadioButton(this)
        rSingle.text = "Single target"
        rSingle.id = View.generateViewId()
        val rMulti = RadioButton(this)
        rMulti.text = "Multi target"
        rMulti.id = View.generateViewId()
        group.addView(rSingle)
        group.addView(rMulti)
        group.check(if (Prefs.multi) rMulti.id else rSingle.id)
        group.setOnCheckedChangeListener { _, id ->
            Prefs.multi = (id == rMulti.id)
            multiSection.visibility = if (Prefs.multi) View.VISIBLE else View.GONE
            ClickerService.instance?.onSettingsChanged()
        }
        root.addView(group)

        // numeric settings
        numRow(root, "Delay between clicks (ms)", { Prefs.delayMs }, { Prefs.delayMs = it })
        numRow(root, "Tap duration (ms)", { Prefs.tapDurationMs }, { Prefs.tapDurationMs = it })
        numRow(root, "Random position ± (px)", { Prefs.randomOffsetPx.toLong() }, { Prefs.randomOffsetPx = it.toInt() })
        numRow(root, "Random interval ± (ms)", { Prefs.randomIntervalMs.toLong() }, { Prefs.randomIntervalMs = it.toInt() })
        numRow(root, "Loops (0 = infinite)", { Prefs.loops.toLong() }, { Prefs.loops = it.toInt() })

        // multi section
        multiSection = LinearLayout(this)
        multiSection.orientation = LinearLayout.VERTICAL
        multiSection.visibility = if (Prefs.multi) View.VISIBLE else View.GONE

        val actTitle = TextView(this)
        actTitle.text = "Actions (tap to edit)"
        actTitle.textSize = 18f
        actTitle.setPadding(0, dp(24), 0, dp(4))
        multiSection.addView(actTitle)

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        multiSection.addView(list)

        multiSection.addView(button("Clear all actions") {
            Session.script.actions.clear()
            Session.persist()
            refreshList()
            ClickerService.instance?.refreshMarkers()
        })
        multiSection.addView(button("Save script") {
            promptName { n ->
                val s = Script(n, Session.script.actions.map { it.copy() }.toMutableList())
                ScriptStore.save(this, s)
                toast("Saved: $n")
            }
        })
        multiSection.addView(button("Load / delete script") { loadDialog() })
        multiSection.addView(button("Export script (.json)") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "clicker-script.json")
            startActivityForResult(i, reqExport)
        })
        multiSection.addView(button("Import script (.json)") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, reqImport)
        })
        root.addView(multiSection)

        val sv = ScrollView(this)
        sv.addView(root)
        return sv
    }

    private fun button(t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun numRow(parent: LinearLayout, label: String, get: () -> Long, set: (Long) -> Unit) {
        val t = TextView(this)
        t.text = label
        t.setPadding(0, dp(12), 0, 0)
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(get().toString())
        e.doAfterTextChanged { set(it?.toString()?.toLongOrNull() ?: 0L) }
        parent.addView(t)
        parent.addView(e)
    }

    private fun refreshList() {
        list.removeAllViews()
        val acts = Session.script.actions
        if (acts.isEmpty()) {
            val t = TextView(this)
            t.text = "No actions yet. Show panel, then + (add target) or ● (record)."
            list.addView(t)
        }
        acts.forEachIndexed { i, a ->
            val row = TextView(this)
            row.text = if (a.type == ActionType.TAP)
                "${i + 1}. TAP (${a.x.toInt()},${a.y.toInt()})  dur ${a.durationMs}ms  delay ${a.delayMs}ms"
            else
                "${i + 1}. SWIPE (${a.x.toInt()},${a.y.toInt()})→(${a.x2.toInt()},${a.y2.toInt()})  dur ${a.durationMs}ms  delay ${a.delayMs}ms"
            row.setPadding(0, dp(10), 0, dp(10))
            row.setOnClickListener { editDialog(i) }
            list.addView(row)
        }
    }

    private fun editDialog(i: Int) {
        val acts = Session.script.actions
        if (i !in acts.indices) return
        val a = acts[i]
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        fun field(label: String, v: Number): EditText {
            val t = TextView(this)
            t.text = label
            val e = EditText(this)
            e.inputType = InputType.TYPE_CLASS_NUMBER
            e.setText(v.toString())
            box.addView(t)
            box.addView(e)
            return e
        }

        val ex = field("X", a.x.toInt())
        val ey = field("Y", a.y.toInt())
        var ex2: EditText? = null
        var ey2: EditText? = null
        if (a.type == ActionType.SWIPE) {
            ex2 = field("X end", a.x2.toInt())
            ey2 = field("Y end", a.y2.toInt())
        }
        val ed = field("Duration (ms)", a.durationMs)
        val el = field("Delay after this action (ms)", a.delayMs)

        AlertDialog.Builder(this)
            .setTitle("Action ${i + 1} (${a.type})")
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
                        ScriptStore.load(this, n)?.let {
                            Session.script = it
                            Session.persist()
                            refreshList()
                            ClickerService.instance?.refreshMarkers()
                        } ?: toast("Load failed")
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
