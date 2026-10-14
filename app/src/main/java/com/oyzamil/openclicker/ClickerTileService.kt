package com.oyzamil.openclicker

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to start / stop the clicker. */
class ClickerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        runCatching { Prefs.init(this) }
        val s = ClickerService.instance
        if (s == null) {
            // service is off: try to switch it back on by itself (needs the one-time ADB permission)
            if (AccessibilityEnabler.ensureEnabled(this, repair = true)) {
                runCatching {
                    android.widget.Toast.makeText(this, "Service enabling… tap the tile again", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                openApp()
            }
            return
        }
        // dismissShade = true so the notification shade closes and the target app is visible
        runCatching { s.toggleRun(dismissShade = true) }
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val s = ClickerService.instance
        t.state = if (s?.isBusy() == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Open Clicker"
        runCatching { t.updateTile() }
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pi)
            } else {
                startActivityAndCollapse(intent)
            }
        }
    }
}
