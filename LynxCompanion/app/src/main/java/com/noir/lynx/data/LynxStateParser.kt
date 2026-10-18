package com.noir.lynx.data

import org.json.JSONObject
import android.util.Log
import com.noir.lynx.profiles.ProfileOverridesCodec

internal object LynxStateParser {
        fun parseStateJson(raw: String, soc: String, currentActiveProf: String): LynxState {
            return try {
                val root = JSONObject(raw)
                val customProfile = root.optString("kernel_profile_id", "")
                val resolvedActiveProfile = if (com.noir.lynx.profiles.KernelPerformanceProfile.ID_PATTERN.matches(customProfile)) {
                    customProfile
                } else if (currentActiveProf in listOf("auto", "balance", "performance", "extreme", "powersave", "dormant")) {
                    currentActiveProf
                } else {
                    val p = root.optString("active_profile", "balance")
                    if (p in listOf("auto", "balance", "performance", "extreme", "powersave", "dormant")) p else "balance"
                }

                LynxState(
                    moduleVersion = root.optString("module_version", "3.0.0"),
                    releaseType = root.optString("release_type", "beta"),
                    activeProfile = resolvedActiveProfile,
                    profileOverrides = ProfileOverridesCodec.decode(root.optString("profile_overrides", "")),
                    targetSoc = soc,
                    isSpoofed = root.optBoolean("is_spoofed", false),
                    overclock = parseOverclock(root.optJSONObject("overclock")),
                    memory = parseMemory(root.optJSONObject("memory")),
                    charging = parseCharging(root.optJSONObject("charging")),
                    uclamp = parseUclamp(root.optJSONObject("uclamp")),
                    displayTouch = parseDisplayTouch(root.optJSONObject("display_touch")),
                    network = parseNetwork(root.optJSONObject("network")),
                    audio = parseAudio(root.optJSONObject("audio")),
                    oemNeutralizer = parseOemNeutralizer(root.optJSONObject("oem_neutralizer")),
                    thermal = parseThermal(root.optJSONObject("thermal")),
                )
            } catch (e: Exception) {
                Log.e("LynxStateParser", "parseStateJson failed: ${e.message}")
                LynxState()
            }
        }

        private fun parseOverclock(j: JSONObject?) = OverclockConfig(
            enabled = j?.optBoolean("enabled", false) ?: false,
            cpuFloorRatio = j?.optInt("cpu_floor_ratio", 85) ?: 85,
            zramSizeMb = j?.optInt("zram_size_mb", 2048) ?: 2048,
            swappiness = j?.optInt("swappiness", 80) ?: 80,
        )

        private fun parseMemory(j: JSONObject?) = MemoryConfig(
            zramSizeMb = j?.optInt("zram_size_mb", 2048) ?: 2048,
            swappiness = j?.optInt("swappiness", 80) ?: 80,
            mglruEnabled = j?.optBoolean("mglru_enabled", true) ?: true,
            compAlgorithm = j?.optString("comp_algorithm", "lz4") ?: "lz4",
        )

        private fun parseCharging(j: JSONObject?) = ChargingConfig(
            chargingMode = j?.optString("charging_mode", "standard") ?: "standard",
            bypassEnabled = j?.optBoolean("bypass_enabled", false) ?: false,
            extremeChargingEnabled = j?.optBoolean("extreme_charging_enabled", false) ?: false,
            tempCutoffC = j?.optInt("temp_cutoff_c", 45) ?: 45,
            limitCurrentMa = j?.optInt("limit_current_ma", 6000) ?: 6000,
            isUnconstrainedMaxHw = j?.optBoolean("is_unconstrained_max_hw", false) ?: false,
            customLimitCurrentMa = j?.optInt("custom_limit_current_ma", 6000) ?: 6000,
            autoCutEnabled = j?.optBoolean("auto_cut_enabled", false) ?: false,
            maxBatteryPercent = j?.optInt("max_battery_percent", 80) ?: 80,
            highCurrentTargetPercent = j?.optInt("high_current_target_percent", 90) ?: 90,
            emergencyTempGuardEnabled = j?.optBoolean("emergency_temp_guard_enabled", true) ?: true,
            thermalLockoutBypassEnabled = j?.optBoolean("thermal_lockout_bypass_enabled", false) ?: false,
            smartTaperingEnabled = j?.optBoolean("smart_tapering_enabled", true) ?: true,
            nightSleepGuardEnabled = j?.optBoolean("night_sleep_guard_enabled", false) ?: false,
            dualCellMultiplier = j?.optInt("dual_cell_multiplier", 1) ?: 1,
        )

        private fun parseUclamp(j: JSONObject?) = UclampConfig(
            gameMinRatio = j?.optInt("game_min_ratio", 70) ?: 70,
            gameMaxRatio = j?.optInt("game_max_ratio", 100) ?: 100,
            balanceMinRatio = j?.optInt("balance_min_ratio", 20) ?: 20,
            schedutilUpRateUs = j?.optInt("schedutil_up_rate_us", 500) ?: 500,
            schedutilDownRateUs = j?.optInt("schedutil_down_rate_us", 20000) ?: 20000,
        )

        private fun parseDisplayTouch(j: JSONObject?) = DisplayTouchConfig(
            touchboost = j?.optBoolean("touchboost", true) ?: true,
            refreshRateLock = j?.optInt("refresh_rate_lock", 120) ?: 120,
            surfaceFlingerOffsetNs = j?.optInt("surfaceflinger_offset_ns", 2000000) ?: 2000000,
            skiaVulkanEnabled = j?.optBoolean("skia_vulkan_enabled", false) ?: false,
        )

        private fun parseNetwork(j: JSONObject?) = NetworkConfig(
            wifiPingStabilizer = j?.optBoolean("wifi_ping_stabilizer", true) ?: true,
            tcpCongestion = j?.optString("tcp_congestion", "cubic") ?: "cubic",
            fqCodelEnabled = j?.optBoolean("fq_codel_enabled", true) ?: true,
            wifiPowerSaveInGame = j?.optBoolean("wifi_power_save_in_game", false) ?: false,
            tcpBuffers16m = j?.optBoolean("tcp_buffers_16m", true) ?: true,
            tcpFastOpen = j?.optBoolean("tcp_fast_open", true) ?: true,
            antiJitterScan = j?.optBoolean("anti_jitter_scan", true) ?: true,
            mtkHardwareAggregation = j?.optBoolean("mtk_hw_aggregation", true) ?: true,
            privateDnsMode = j?.optString("private_dns_mode", "off") ?: "off",
            privateDnsSpecifier = j?.optString("private_dns_specifier", "") ?: "",
            wifiGameAutoTrigger = j?.optBoolean("wifi_game_auto_trigger", true) ?: true,
        )

        private fun parseAudio(j: JSONObject?) = AudioConfig(
            lowLatencyMmap = j?.optBoolean("low_latency_mmap", true) ?: true,
            fastTrackEnabled = j?.optBoolean("fast_track_enabled", true) ?: true,
            btCrackleGuard = j?.optBoolean("bt_crackle_guard", true) ?: true,
            deepBufferBypass = j?.optBoolean("deep_buffer_bypass", true) ?: true,
            directDacOffload = j?.optBoolean("direct_dac_offload", true) ?: true,
        )

        private fun parseOemNeutralizer(j: JSONObject?) = OemNeutralizerConfig(
            joyoseNeutralize = j?.optBoolean("joyose_neutralize", true) ?: true,
            gosNeutralize = j?.optBoolean("gos_neutralize", true) ?: true,
            freezeMethod = j?.optString("freeze_method", "sigstop") ?: "sigstop",
        )

        private fun parseThermal(j: JSONObject?) = ThermalConfig(
            fullBypass = j?.optBoolean("full_bypass", false) ?: false,
            customTempLimitC = j?.optInt("custom_temp_limit_c", 50) ?: 50,
            tripPointOverrideC = j?.optInt("trip_point_override_c", 150) ?: 150,
        )
}
