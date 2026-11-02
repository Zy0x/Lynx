package com.noir.lynx.core.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Universal Two-Way Real-Time Profile Synchronization Bus.
 * Bridges MainActivity (main process) and LynxFloatingHudService (:hud process)
 * with zero-latency Android Broadcast IPC and Kotlin Flow events.
 */
object LynxProfileSyncBus {
    const val ACTION_PROFILE_SYNCHRONIZED = "com.noir.lynx.ACTION_PROFILE_SYNCHRONIZED"
    const val EXTRA_PROFILE = "extra_profile"
    const val EXTRA_SENDER = "extra_sender"
    private const val TAG = "LynxProfileSyncBus"

    // In-process reactive flow (for components running in the same process)
    private val _profileEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val profileEvents = _profileEvents.asSharedFlow()

    /**
     * Broadcasts a profile change across all app processes (main, :hud, and services).
     */
    fun broadcastProfileChange(context: Context, profile: String, sender: String = "unknown") {
        val norm = profile.trim().lowercase()
        Log.i(TAG, "Broadcasting profile change across processes: profile=$norm, sender=$sender")
        _profileEvents.tryEmit(norm to sender)

        // Send explicit broadcast targeting own package to ensure delivery across :hud and main process
        val intent = Intent(ACTION_PROFILE_SYNCHRONIZED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_PROFILE, norm)
            putExtra(EXTRA_SENDER, sender)
            addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY or Intent.FLAG_RECEIVER_FOREGROUND)
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send profile sync broadcast: ${e.message}")
        }
    }
}
