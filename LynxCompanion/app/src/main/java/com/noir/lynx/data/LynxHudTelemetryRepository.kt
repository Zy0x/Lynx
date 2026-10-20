package com.noir.lynx.data

import com.noir.lynx.feature.hud.data.HudLegacyAdapter
import com.noir.lynx.service.LynxFloatingHudService
import android.os.SystemClock

/** Compatibility projection of the single HUD collector. Never starts a second sensor poller. */
object LynxHudTelemetryRepository {
    fun reset() = Unit
    suspend fun readFloatingHudTelemetry(displayHz: Int = 0): LynxRepository.FloatingHudTelemetry =
        HudLegacyAdapter.project(LynxFloatingHudService.snapshotFlow.value.fresh(SystemClock.elapsedRealtime()))
}
