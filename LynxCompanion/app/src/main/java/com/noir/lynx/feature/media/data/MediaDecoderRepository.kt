package com.noir.lynx.feature.media.data

import android.media.MediaCodecList
import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RegisteredDecoder(val name: String, val mime: String, val kind: String)

data class MediaDecoderStatus(
    val available: Boolean = false,
    val configuredMode: String = "system",
    val applyOnBoot: Boolean = false,
    val sessionMode: String = "unmanaged",
    val pendingRestart: Boolean = false,
    val sessionPropertyVerified: Boolean = false,
    val supportedModes: Set<String> = setOf("system"),
    val activeDecoders: List<String> = emptyList(),
    val registeredDecoders: List<RegisteredDecoder> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)

object MediaDecoderRepository {
    private const val ENGINE = "/data/adb/modules/Lynx/system/bin/lynxd"
    val modes = listOf("system", "omx_compat", "prefer_hardware", "prefer_software")

    suspend fun read(): MediaDecoderStatus = withContext(Dispatchers.IO) {
        // Android's registry is authoritative for advertised codecs, not a decode benchmark.
        val registered = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filterNot { it.isEncoder }.flatMap { info ->
                info.supportedTypes.filter { it.startsWith("video/") }.map { mime ->
                    val kind = if (Build.VERSION.SDK_INT >= 29) {
                        when { info.isHardwareAccelerated -> "hardware"; info.isSoftwareOnly -> "software"; else -> "vendor" }
                    } else if (info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.")) "software" else "vendor"
                    RegisteredDecoder(info.name, mime, kind)
                }
            }.distinctBy { it.name to it.mime }
        }.getOrDefault(emptyList())
        val result = Shell.cmd("[ -x '$ENGINE' ] && '$ENGINE' media status").exec()
        val json = runCatching { JSONObject(result.out.joinToString("\n")) }.getOrNull()
        if (!result.isSuccess || json?.optBoolean("ok") != true) {
            return@withContext MediaDecoderStatus(registeredDecoders = registered)
        }
        val supported = json.optJSONArray("supported_modes")
        val active = json.optJSONArray("active_decoders")
        val props = json.optJSONObject("properties")
        MediaDecoderStatus(
            available = true,
            configuredMode = json.optString("configured_mode", "system"),
            applyOnBoot = json.optBoolean("apply_on_boot"),
            sessionMode = json.optString("session_mode", "unmanaged"),
            pendingRestart = json.optBoolean("pending_restart"),
            sessionPropertyVerified = json.optBoolean("session_property_verified"),
            supportedModes = (0 until (supported?.length() ?: 0)).mapNotNull { supported?.optString(it) }.toSet(),
            activeDecoders = (0 until (active?.length() ?: 0)).mapNotNull { active?.optString(it) },
            registeredDecoders = registered,
            properties = props?.let { values -> values.keys().asSequence().associateWith { values.optString(it) } } ?: emptyMap()
        )
    }

    suspend fun apply(mode: String, boot: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (mode !in modes) return@withContext Result.failure(IllegalArgumentException("invalid_mode"))
        val result = Shell.cmd("[ -x '$ENGINE' ] && '$ENGINE' media set '$mode' '$boot' --restart-player").exec()
        val json = runCatching { JSONObject(result.out.joinToString("\n")) }.getOrNull()
        if (result.isSuccess && json?.optBoolean("ok") == true) Result.success(Unit)
        else Result.failure(IllegalStateException(json?.optString("error", "native_unavailable") ?: "native_unavailable"))
    }
}
