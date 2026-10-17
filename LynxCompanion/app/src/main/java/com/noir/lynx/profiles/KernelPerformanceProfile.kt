package com.noir.lynx.profiles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString

/** A safe, portable performance policy. It stores semantic presets and allowlisted config values only. */
data class KernelPerformanceProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val baseProfile: String,
    val domainProfiles: Map<ProfileDomain, String> = emptyMap(),
    val includedDomains: Set<ProfileDomain> = emptySet(),
    val settings: Map<String, String> = emptyMap(),
    val includedSensitiveSettings: Set<String> = emptySet(),
    val builtIn: Boolean = false,
) {
    fun validate(): String? {
        if (!ID_PATTERN.matches(id)) return "ID profil tidak valid"
        if (name.isBlank() || name.length > 40) return "Nama profil harus 1–40 karakter"
        if (baseProfile !in BASE_PROFILE_IDS) return "Profil dasar tidak dikenal"
        if (domainProfiles.any { (domain, preset) ->
                when (domain) {
                    ProfileDomain.CPU -> preset !in setOf("powersave", "battery", "balance", "performance", "extreme")
                    ProfileDomain.GPU -> preset !in setOf("battery", "balanced", "esports", "extreme")
                    else -> preset !in BUILT_IN_IDS
                }
            }) return "Preset domain tidak dikenal"
        if (settings.keys.any { it !in SAFE_SETTING_KEYS }) return "Profil berisi pengaturan yang tidak didukung"
        if (includedSensitiveSettings.any { it !in SENSITIVE_SETTING_KEYS }) return "Pilihan pengaturan sensitif tidak valid"
        if (settings.keys.any { it in SENSITIVE_SETTING_KEYS && it !in includedSensitiveSettings }) {
            return "Pengaturan baterai/termal sensitif harus dipilih secara eksplisit"
        }
        if (settings.values.any { it.length > 80 || !VALUE_PATTERN.matches(it) }) return "Nilai profil berisi karakter yang tidak didukung"
        if (BOOLEAN_SETTING_KEYS.any { key -> settings[key]?.let { it != "true" && it != "false" } == true }) {
            return "Nilai toggle profil harus true atau false"
        }
        if (settings["charging.max_battery_percent"]?.toIntOrNull()?.let { it !in 50..100 } == true) {
            return "Batas pengisian harus 50–100%"
        }
        if (settings["thermal.custom_temp_limit_c"]?.toIntOrNull()?.let { it !in 35..60 } == true) {
            return "Batas termal harus 35–60°C"
        }
        if (settings["thermal.trip_point_override_c"]?.toIntOrNull()?.let { it !in 35..60 } == true) {
            return "Trip point profil harus 35–60°C"
        }
        if (settings["charging.limit_current_ma"]?.toIntOrNull()?.let { it !in 500..6000 } == true) {
            return "Batas arus profil harus 500–6000 mA"
        }
        if (settings["thermal.mode"]?.let { it !in setOf("default", "stable", "performance", "hardware_safety_dominant") } == true) {
            return "Mode termal profil tidak dikenal"
        }
        if (settings["thermal.full_bypass"] == "true" || settings["charging.bypass_enabled"] == "true" ||
            settings["charging.thermal_lockout_bypass_enabled"] == "true") {
            return "Bypass pengisian atau proteksi termal tidak boleh dimasukkan ke profil"
        }
        return null
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("description", description)
        put("base_profile", baseProfile)
        put("built_in", builtIn)
        put("domain_profiles", buildJsonObject { domainProfiles.forEach { (domain, preset) -> put(domain.storageKey, preset) } })
        put("included_domains", JsonArray(includedDomains.map { JsonPrimitive(it.storageKey) }.sortedBy { it.content }))
        put("settings", buildJsonObject { settings.forEach { (key, value) -> put(key, value) } })
        put("included_sensitive_settings", JsonArray(includedSensitiveSettings.sorted().map(::JsonPrimitive)))
    }

    companion object {
        const val CATALOG_VERSION = 1
        val BUILT_IN_IDS = setOf("auto", "balance", "performance", "extreme", "powersave")
        val BASE_PROFILE_IDS = setOf("balance", "performance", "extreme", "powersave")
        val ID_PATTERN = Regex("[a-z][a-z0-9_-]{0,39}")
        val VALUE_PATTERN = Regex("[A-Za-z0-9_.,:%+\\-]*")
        val BOOLEAN_SETTING_KEYS = setOf(
            "memory.mglru_enabled", "display_touch.touchboost", "display_touch.skia_vulkan_enabled",
            "network.wifi_ping_stabilizer", "network.fq_codel_enabled", "network.wifi_power_save_in_game",
            "network.tcp_buffers_16m", "network.tcp_fast_open", "network.anti_jitter_scan", "network.mtk_hw_aggregation",
            "audio.low_latency_mmap", "audio.fast_track_enabled", "audio.bt_crackle_guard",
            "charging.smart_tapering_enabled", "charging.emergency_temp_guard_enabled",
            "charging.thermal_lockout_bypass_enabled", "charging.bypass_enabled",
            "charging.extreme_charging_enabled", "thermal.full_bypass", "thermal.hardware_safety_dominant"
        )
        val SENSITIVE_SETTING_KEYS = setOf(
            "charging.max_battery_percent", "charging.high_current_target_percent",
            "charging.limit_current_ma", "charging.smart_tapering_enabled",
            "charging.emergency_temp_guard_enabled", "charging.thermal_lockout_bypass_enabled",
            "charging.bypass_enabled", "charging.extreme_charging_enabled",
            "thermal.mode", "thermal.full_bypass", "thermal.custom_temp_limit_c",
            "thermal.trip_point_override_c", "thermal.hardware_safety_dominant"
        )
        val SAFE_SETTING_KEYS = setOf(
            "memory.zram_size_mb", "memory.swappiness", "memory.mglru_enabled",
            "memory.comp_algorithm", "uclamp.game_min_ratio", "uclamp.game_max_ratio",
            "uclamp.balance_min_ratio", "uclamp.schedutil_up_rate_us", "uclamp.schedutil_down_rate_us",
            "display_touch.touchboost", "display_touch.refresh_rate_lock",
            "display_touch.surfaceflinger_offset_ns", "display_touch.skia_vulkan_enabled",
            "network.wifi_ping_stabilizer", "network.tcp_congestion", "network.fq_codel_enabled",
            "network.wifi_power_save_in_game", "network.tcp_buffers_16m", "network.tcp_fast_open",
            "network.anti_jitter_scan", "network.mtk_hw_aggregation",
            "audio.low_latency_mmap", "audio.fast_track_enabled",
            "audio.bt_crackle_guard", "charging.max_battery_percent", "charging.high_current_target_percent",
            "charging.limit_current_ma", "charging.smart_tapering_enabled",
            "charging.emergency_temp_guard_enabled", "charging.thermal_lockout_bypass_enabled",
            "charging.bypass_enabled", "charging.extreme_charging_enabled", "thermal.mode",
            "thermal.full_bypass", "thermal.custom_temp_limit_c", "thermal.trip_point_override_c",
            "thermal.hardware_safety_dominant"
        )

        fun builtIns(): List<KernelPerformanceProfile> = listOf(
            builtin("auto", "Auto (AI)", "balance"),
            builtin("balance", "Seimbang", "balance"),
            builtin("performance", "Performa", "performance"),
            builtin("extreme", "Extreme", "extreme"),
            builtin("powersave", "Hemat Daya", "powersave"),
        )

        private fun builtin(id: String, label: String, base: String) = KernelPerformanceProfile(
            id = id, name = label, description = "Profil bawaan Lynx", baseProfile = base,
            domainProfiles = mapOf(
                ProfileDomain.CPU to base,
                ProfileDomain.GPU to when (base) {
                    "powersave" -> "battery"
                    "performance" -> "esports"
                    "balance" -> "balanced"
                    else -> base
                }
            ), builtIn = true
        )

        fun fromJson(json: JsonObject): KernelPerformanceProfile? = try {
            val domains = json["domain_profiles"]?.jsonObject ?: JsonObject(emptyMap())
            val included = json["included_domains"]?.jsonArray ?: JsonArray(emptyList())
            val settingsJson = json["settings"]?.jsonObject ?: JsonObject(emptyMap())
            val sensitive = json["included_sensitive_settings"]?.jsonArray ?: JsonArray(emptyList())
            val profile = KernelPerformanceProfile(
                id = json.getValue("id").jsonPrimitive.content,
                name = json.getValue("name").jsonPrimitive.content,
                description = json["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                baseProfile = json["base_profile"]?.jsonPrimitive?.contentOrNull ?: "balance",
                domainProfiles = ProfileDomain.values().mapNotNull { domain ->
                    domains[domain.storageKey]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { domain to it }
                }.toMap(),
                includedDomains = included.mapNotNull { value ->
                    ProfileDomain.values().firstOrNull { it.storageKey == value.jsonPrimitive.contentOrNull }
                }.toSet(),
                settings = settingsJson.mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }.toMap(),
                includedSensitiveSettings = sensitive.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet(),
                builtIn = json["built_in"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            )
            profile.takeIf { it.validate() == null }
        } catch (_: Exception) { null }
    }
}

data class KernelProfileCatalog(
    val profiles: List<KernelPerformanceProfile> = KernelPerformanceProfile.builtIns(),
    val activeId: String = "balance",
) {
    fun find(id: String): KernelPerformanceProfile? = profiles.firstOrNull { it.id == id }

    fun toJson(): String = Json { prettyPrint = true }.encodeToString(
        JsonObject(buildMap {
            put("schema_version", JsonPrimitive(KernelPerformanceProfile.CATALOG_VERSION))
            put("active_id", JsonPrimitive(activeId))
            put("profiles", JsonArray(profiles.map { it.toJson() }))
        })
    )

    companion object {
        fun parse(raw: String): KernelProfileCatalog {
            return try {
                val json = Json.parseToJsonElement(raw).jsonObject
                val version = json["schema_version"]?.jsonPrimitive?.intOrNull ?: 0
                if (version !in 1..KernelPerformanceProfile.CATALOG_VERSION) return KernelProfileCatalog()
                val builtIns = KernelPerformanceProfile.builtIns().associateBy { it.id }.toMutableMap()
                val custom = linkedMapOf<String, KernelPerformanceProfile>()
                val values = json["profiles"]?.jsonArray ?: JsonArray(emptyList())
                for (value in values) {
                    val candidate = KernelPerformanceProfile.fromJson(value.jsonObject) ?: continue
                    if (candidate.id in KernelPerformanceProfile.BUILT_IN_IDS) {
                        // Built-ins can be customized only as an explicit persisted replacement layer.
                        if (candidate.builtIn) builtIns[candidate.id] = candidate.copy(builtIn = true)
                    } else if (!candidate.builtIn) custom[candidate.id] = candidate
                }
                val profiles = KernelPerformanceProfile.builtIns().map { builtIns[it.id] ?: it } + custom.values
                val active = json["active_id"]?.jsonPrimitive?.contentOrNull?.takeIf { id -> profiles.any { it.id == id } } ?: "balance"
                KernelProfileCatalog(profiles, active)
            } catch (_: Exception) { KernelProfileCatalog() }
        }
    }
}
