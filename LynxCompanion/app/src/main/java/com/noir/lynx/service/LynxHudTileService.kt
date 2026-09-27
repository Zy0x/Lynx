package com.noir.lynx.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi

/**
 * Quick Settings Tile to toggle Real-time Floating Game HUD & Telemetry.
 */
@RequiresApi(Build.VERSION_CODES.N)
class LynxHudTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izin Tampilkan di Atas Aplikasi Lain diperlukan", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivityAndCollapse(intent)
            return
        }

        val isRunning = LynxFloatingHudService.isRunning
        val intent = Intent(this, LynxFloatingHudService::class.java)
        if (isRunning) {
            stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        updateTileState(!isRunning)
    }

    private fun updateTileState(overrideActive: Boolean? = null) {
        val tile = qsTile ?: return
        val active = overrideActive ?: LynxFloatingHudService.isRunning
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Lynx HUD"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "Overlay Aktif" else "Mati"
        }
        tile.updateTile()
    }
}
