package com.noir.lynx.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.noir.lynx.data.LynxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile to toggle High Brightness Mode (HBM).
 * Safely reports UNAVAILABLE if panel driver lacks HBM node.
 */
@RequiresApi(Build.VERSION_CODES.N)
class LynxHbmTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        scope.launch(Dispatchers.IO) {
            val disp = LynxRepository.readDisplayCalibration()
            if (!disp.isHbmSupported) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@LynxHbmTileService, "HBM tidak didukung oleh panel display ini", Toast.LENGTH_SHORT).show()
                    updateTileState()
                }
                return@launch
            }

            val newState = !disp.hbmEnabled
            LynxRepository.setHbmEnabled(newState)
            launch(Dispatchers.Main) {
                updateTileState()
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        scope.launch(Dispatchers.IO) {
            val disp = LynxRepository.readDisplayCalibration()
            launch(Dispatchers.Main) {
                if (!disp.isHbmSupported) {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = "HBM: Unsupported"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = "Tidak Didukung"
                    }
                } else {
                    tile.state = if (disp.hbmEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    tile.label = "Display HBM"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = if (disp.hbmEnabled) "Outdoor Boost Aktif" else "Normal"
                    }
                }
                tile.updateTile()
            }
        }
    }
}
