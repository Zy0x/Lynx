package com.noir.lynx.service

import com.noir.lynx.core.locale.AppLocale

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.R
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
                    Toast.makeText(this@LynxHbmTileService, AppLocale.getString(this@LynxHbmTileService, R.string.toast_hbm_unsupported), Toast.LENGTH_SHORT).show()
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
                    tile.label = AppLocale.getString(this@LynxHbmTileService, R.string.tile_hbm_unsupported)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = AppLocale.getString(this@LynxHbmTileService, R.string.tile_hbm_subtitle_unsupported)
                    }
                } else {
                    tile.state = if (disp.hbmEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    tile.label = AppLocale.getString(this@LynxHbmTileService, R.string.tile_hbm_label)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = AppLocale.getString(this@LynxHbmTileService, if (disp.hbmEnabled) R.string.tile_hbm_subtitle_active else R.string.tile_hbm_subtitle_normal)
                    }
                }
                tile.updateTile()
            }
        }
    }
}
