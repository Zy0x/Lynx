package com.noir.lynx.service

import com.noir.lynx.core.locale.AppLocale

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.R
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
            val toastIntent = android.content.Intent("com.noir.lynx.PROFILE_CHANGED").apply {
                setPackage(packageName)
                putExtra("profile", nextProfile)
                putExtra("reason", "Quick Settings Tile")
            }
            sendBroadcast(toastIntent)
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
                val profileName = AppLocale.getString(this@LynxProfileTileService, when (profile) {
                    "performance" -> R.string.profile_name_performance
                    "extreme" -> R.string.profile_name_extreme
                    "powersave" -> R.string.profile_name_powersave
                    else -> R.string.profile_name_balance
                })
                tile.label = AppLocale.getString(this@LynxProfileTileService, R.string.tile_profile_label_format, profileName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = AppLocale.getString(this@LynxProfileTileService, when (profile) {
                        "performance" -> R.string.tile_profile_performance
                        "extreme" -> R.string.tile_profile_extreme
                        "powersave" -> R.string.tile_profile_powersave
                        else -> R.string.tile_profile_balance
                    })
                }
                tile.updateTile()
            }
        }
    }
}
