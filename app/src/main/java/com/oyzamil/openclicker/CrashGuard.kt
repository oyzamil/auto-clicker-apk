package com.oyzamil.openclicker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Why this exists: if the app process dies from ANY uncaught exception, Android marks the
 * accessibility service as "Not working" and the user has to switch it on again in Settings.
 * So (1) every error is written to errors.log and (2) the main thread is kept alive after
 * unexpected exceptions while the service is running.
 */
object CrashGuard {
    private var appCtx: Context? = null
    private var loggerInstalled = false
    private var loopGuardInstalled = false

    private fun file(): File? = appCtx?.let { File(it.filesDir, "errors.log") }

    private fun enabled() = runCatching { Prefs.crashGuard }.getOrDefault(true)

    fun log(where: String, t: Throwable) {
        runCatching {
            Log.e("OpenClicker", where, t)
            val f = file() ?: return
            if (f.exists() && f.length() > 60_000L) f.writeText("")
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            f.appendText("[${Date()}] $where\n${sw.toString().take(3000)}\n\n")
        }
    }

    fun readLog(): String = runCatching {
        file()?.takeIf { it.exists() }?.readText() ?: ""
    }.getOrDefault("")

    fun clearLog() {
        runCatching { file()?.delete() }
    }

    /** Log every uncaught exception. Background-thread errors no longer kill the process. */
    fun installLogger(c: Context) {
        appCtx = c.applicationContext
        if (loggerInstalled) return
        loggerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log("uncaught on ${t.name}", e)
            val isMain = t === Looper.getMainLooper().thread
            if (isMain || !enabled()) previous?.uncaughtException(t, e)
        }
    }

    /** Keep the main thread alive after an unexpected exception (service stays "working"). */
    fun installLoopGuard() {
        if (loopGuardInstalled) return
        loopGuardInstalled = true
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                    return@post
                } catch (t: Throwable) {
                    log("main loop", t)
                    if (!enabled()) throw t
                }
            }
        }
    }
}
