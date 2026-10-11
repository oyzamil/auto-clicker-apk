package com.example.openclicker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ActionType { TAP, SWIPE }

data class ClickAction(
    var type: ActionType = ActionType.TAP,
    var x: Float = 0f,
    var y: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var durationMs: Long = 10L,
    /** Delay AFTER this action, in milliseconds. */
    var delayMs: Long = 100L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name)
        .put("x", x.toDouble()).put("y", y.toDouble())
        .put("x2", x2.toDouble()).put("y2", y2.toDouble())
        .put("duration", durationMs)
        .put("delay", delayMs)

    companion object {
        fun fromJson(o: JSONObject) = ClickAction(
            type = runCatching { ActionType.valueOf(o.optString("type", "TAP")) }.getOrDefault(ActionType.TAP),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            x2 = o.optDouble("x2", 0.0).toFloat(),
            y2 = o.optDouble("y2", 0.0).toFloat(),
            durationMs = o.optLong("duration", 10L),
            delayMs = o.optLong("delay", 100L)
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

    var tapDurationMs: Long
        get() = sp.getLong("tapDur", 10L)
        set(v) { sp.edit().putLong("tapDur", v.coerceAtLeast(1L)).apply() }

    var randomOffsetPx: Int
        get() = sp.getInt("rndPos", 0)
        set(v) { sp.edit().putInt("rndPos", v.coerceAtLeast(0)).apply() }

    var randomIntervalMs: Int
        get() = sp.getInt("rndInt", 0)
        set(v) { sp.edit().putInt("rndInt", v.coerceAtLeast(0)).apply() }

    /** 0 = infinite. */
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
