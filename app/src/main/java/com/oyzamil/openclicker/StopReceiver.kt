package com.oyzamil.openclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Stop" button of the running notification. */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            runCatching { ClickerService.instance?.stopFromOutside() }
        }
    }

    companion object {
        const val ACTION_STOP = "com.oyzamil.openclicker.ACTION_STOP"
    }
}
