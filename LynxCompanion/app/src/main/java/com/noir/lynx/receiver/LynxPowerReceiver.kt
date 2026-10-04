package com.noir.lynx.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.noir.lynx.data.LynxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * LynxPowerReceiver — Automatically re-asserts fast charging & thermal bypass
 * when charger cable is plugged into the device.
 */
class LynxPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        if (action == Intent.ACTION_POWER_CONNECTED) {
            Log.i("LynxPowerReceiver", "Charger cable connected! Verifying charging bypass state...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val state = LynxRepository.readState().charging
                    if (state.extremeChargingEnabled || state.bypassEnabled) {
                        Log.i("LynxPowerReceiver", "Re-asserting hardware charging mode (extreme=${state.extremeChargingEnabled}, bypass=${state.bypassEnabled})...")
                        LynxRepository.applySavedChargingConfig()
                    }
                } catch (e: Exception) {
                    Log.e("LynxPowerReceiver", "Error in power connected receiver: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
