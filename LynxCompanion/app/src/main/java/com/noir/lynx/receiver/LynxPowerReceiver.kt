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
    companion object {
        @Volatile
        private var lastConnectedTime = 0L
        @Volatile
        private var lastDisconnectedTime = 0L
        private const val DEBOUNCE_MS = 3000L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        val now = System.currentTimeMillis()

        if (action == Intent.ACTION_POWER_CONNECTED) {
            if (now - lastConnectedTime < DEBOUNCE_MS) {
                Log.d("LynxPowerReceiver", "Ignoring duplicate ACTION_POWER_CONNECTED (debounced)")
                return
            }
            lastConnectedTime = now
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
        } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
            if (now - lastDisconnectedTime < DEBOUNCE_MS) {
                Log.d("LynxPowerReceiver", "Ignoring duplicate ACTION_POWER_DISCONNECTED (debounced)")
                return
            }
            lastDisconnectedTime = now
            Log.i("LynxPowerReceiver", "Charger cable unplugged! Reverting fast charging & restoring thermal baseline...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LynxRepository.handlePowerDisconnected()
                } catch (e: Exception) {
                    Log.e("LynxPowerReceiver", "Error in power disconnected receiver: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
