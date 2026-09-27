package com.noir.lynx.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.noir.lynx.data.LynxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile to cycle through Lynx Kernel profiles:
 * Balance -> Performance -> Extreme -> Powersave -> Balance
 */
@RequiresApi(Build.VERSION_CODES.N)
class LynxProfileTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        scope.launch(Dispatchers.IO) {
            val state = LynxRepository.readState()
            val nextProfile = when (state.activeProfile.lowercase()) {
                "balance", "dormant" -> "performance"
                "performance" -> "extreme"
                "extreme" -> "powersave"
                else -> "balance"
            }
            LynxRepository.setProfile(nextProfile)
            launch(Dispatchers.Main) {
                updateTileState()
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        scope.launch(Dispatchers.IO) {
            val state = LynxRepository.readState()
            val profile = state.activeProfile.lowercase()
            launch(Dispatchers.Main) {
                tile.state = if (profile == "dormant") Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                tile.label = "Lynx: ${profile.replaceFirstChar { it.uppercase() }}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = when (profile) {
                        "performance" -> "High Perf Mode"
                        "extreme" -> "Max Clock / No Limit"
                        "powersave" -> "Battery Saver"
                        else -> "Balanced Daily"
                    }
                }
                tile.updateTile()
            }
        }
    }
}
