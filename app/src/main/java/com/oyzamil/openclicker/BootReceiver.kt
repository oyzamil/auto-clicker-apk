package com.oyzamil.openclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot or an app update, switch the accessibility service back on if Android turned it off. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching {
                Prefs.init(context)
                CrashGuard.installLogger(context)
                AccessibilityEnabler.ensureEnabled(context, repair = false)
            }
        }
    }
}
