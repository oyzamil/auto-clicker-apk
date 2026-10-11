package com.oyzamil.openclicker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ActionType { TAP, SWIPE, SCROLL }

/** Scroll direction = where the content scrolls TO (DOWN = reveal lower content). */
object ScrollDir {
    const val UP = 0
    const val DOWN = 1
    const val LEFT = 2
    const val RIGHT = 3
    fun arrow(d: Int) = when (d) { UP -> "↑"; DOWN -> "↓"; LEFT -> "←"; else -> "→" }
    fun name(d: Int) = when (d) { UP -> "Up"; DOWN -> "Down"; LEFT -> "Left"; else -> "Right" }
}

data class ClickAction(
    var type: ActionType = ActionType.TAP,
    var x: Float = 0f,
    var y: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var durationMs: Long = 10L,
    /** Delay AFTER this action, in milliseconds. */
    var delayMs: Long = 100L,
    /** How many times this action is performed before moving to the next one. */
    var repeat: Int = 1,
    /** SCROLL only: ScrollDir value. */
    var dir: Int = ScrollDir.DOWN,
    /** SCROLL only: distance as percent of screen height/width. */
    var distPct: Int = 50
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name)
        .put("x", x.toDouble()).put("y", y.toDouble())
        .put("x2", x2.toDouble()).put("y2", y2.toDouble())
        .put("duration", durationMs)
        .put("delay", delayMs)
        .put("repeat", repeat)
        .put("dir", dir)
        .put("dist", distPct)

    companion object {
        fun fromJson(o: JSONObject) = ClickAction(
            type = runCatching { ActionType.valueOf(o.optString("type", "TAP")) }.getOrDefault(ActionType.TAP),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            x2 = o.optDouble("x2", 0.0).toFloat(),
            y2 = o.optDouble("y2", 0.0).toFloat(),
            durationMs = o.optLong("duration", 10L),
            delayMs = o.optLong("delay", 100L),
            repeat = o.optInt("repeat", 1).coerceAtLeast(1),
            dir = o.optInt("dir", ScrollDir.DOWN).coerceIn(0, 3),
            distPct = o.optInt("dist", 50).coerceIn(1, 100)
        )
    }
}

data class Script(
    var name: String = "current",
    val actions: MutableList<ClickAction> = mutableListOf()
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        actions.forEach { arr.put(it.toJson()) }
        return JSONObject().put("name", name).put("actions", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): Script {
            val list = mutableListOf<ClickAction>()
            val arr = o.optJSONArray("actions") ?: JSONArray()
            for (i in 0 until arr.length()) list.add(ClickAction.fromJson(arr.getJSONObject(i)))
            return Script(o.optString("name", "imported"), list)
        }
    }
}

/** App-wide settings. */
object Prefs {
    private var p: SharedPreferences? = null
    fun init(c: Context) {
        if (p == null) p = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
    private val sp: SharedPreferences get() = p!!

    var multi: Boolean
        get() = sp.getBoolean("multi", false)
        set(v) { sp.edit().putBoolean("multi", v).apply() }

    /** Delay between clicks in ms (single mode, and default for new multi actions). */
    var delayMs: Long
        get() = sp.getLong("delay", 100L)
        set(v) { sp.edit().putLong("delay", v.coerceAtLeast(0L)).apply() }

    /** Unit shown in the delay input: 0 = ms, 1 = sec, 2 = min. Delay itself is always stored in ms. */
    var delayUnit: Int
        get() = sp.getInt("delayUnit", 0)
        set(v) { sp.edit().putInt("delayUnit", v.coerceIn(0, 2)).apply() }

    /** Duration of slide / scroll gestures in ms. */
    var swipeDurationMs: Long
        get() = sp.getLong("swipeDur", 300L)
        set(v) { sp.edit().putLong("swipeDur", v.coerceAtLeast(1L)).apply() }

    /** Scroll distance, percent of screen. */
    var scrollDistPct: Int
        get() = sp.getInt("scrollDist", 50)
        set(v) { sp.edit().putInt("scrollDist", v.coerceIn(1, 100)).apply() }

    var scrollDir: Int
        get() = sp.getInt("scrollDir", ScrollDir.DOWN)
        set(v) { sp.edit().putInt("scrollDir", v.coerceIn(0, 3)).apply() }

    /** Single-mode action: 0 = click, 1 = slide, 2 = scroll. */
    var singleType: Int
        get() = sp.getInt("singleType", 0)
        set(v) { sp.edit().putInt("singleType", v.coerceIn(0, 2)).apply() }

    var singleX2: Float
        get() = sp.getFloat("sx2", 0f)
        set(v) { sp.edit().putFloat("sx2", v).apply() }

    var singleY2: Float
        get() = sp.getFloat("sy2", 0f)
        set(v) { sp.edit().putFloat("sy2", v).apply() }

    var tapDurationMs: Long
        get() = sp.getLong("tapDur", 10L)
        set(v) { sp.edit().putLong("tapDur", v.coerceAtLeast(1L)).apply() }

    var randomOffsetPx: Int
        get() = sp.getInt("rndPos", 0)
        set(v) { sp.edit().putInt("rndPos", v.coerceAtLeast(0)).apply() }

    var randomIntervalMs: Int
        get() = sp.getInt("rndInt", 0)
        set(v) { sp.edit().putInt("rndInt", v.coerceAtLeast(0)).apply() }

    /** How many times to perform the action / sequence. 0 = infinite. */
    var loops: Int
        get() = sp.getInt("loops", 0)
        set(v) { sp.edit().putInt("loops", v.coerceAtLeast(0)).apply() }

    var singleX: Float
        get() = sp.getFloat("sx", 0f)
        set(v) { sp.edit().putFloat("sx", v).apply() }

    var singleY: Float
        get() = sp.getFloat("sy", 0f)
        set(v) { sp.edit().putFloat("sy", v).apply() }
}

/** The working multi-target script, shared between activity and service. */
object Session {
    var script = Script()
    private var file: File? = null

    fun init(c: Context) {
        if (file != null) return
        file = File(c.applicationContext.filesDir, "current.json")
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { script = Script.fromJson(JSONObject(f.readText())) }
        }
    }

    fun persist() {
        runCatching { file?.writeText(script.toJson().toString()) }
    }
}

object ScriptStore {
    private fun dir(c: Context) = File(c.filesDir, "scripts").apply { mkdirs() }
    private fun safe(n: String) = n.replace(Regex("[^A-Za-z0-9 _-]"), "_")

    fun names(c: Context): List<String> =
        dir(c).listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension }?.sorted()
            ?: emptyList()

    fun save(c: Context, s: Script) {
        File(dir(c), safe(s.name) + ".json").writeText(s.toJson().toString())
    }

    fun load(c: Context, name: String): Script? =
        runCatching { Script.fromJson(JSONObject(File(dir(c), "$name.json").readText())) }.getOrNull()

    fun delete(c: Context, name: String) {
        File(dir(c), "$name.json").delete()
    }
}
