package com.noir.lynx.feature.network.model

import androidx.compose.runtime.Immutable

enum class SpeedtestPhase {
    IDLE,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    ERROR
}

@Immutable
data class SpeedtestServerSpec(
    val id: String,
    val name: String,
    val provider: String,
    val description: String,
    val isCustom: Boolean = false
)

val SPEEDTEST_SERVER_PRESETS: List<SpeedtestServerSpec> = listOf(
    SpeedtestServerSpec(
        id = "cloudflare",
        name = "Cloudflare Edge",
        provider = "speed.cloudflare.com",
        description = "Uji kecepatan unduh, unggah & kestabilan ping secara menyeluruh"
    ),
    SpeedtestServerSpec(
        id = "cloudflare_lite",
        name = "Hemat Kuota",
        provider = "speed.cloudflare.com",
        description = "Uji cepat hemat data (~10–20 MB), cocok untuk jaringan seluler"
    ),
    SpeedtestServerSpec(
        id = "custom",
        name = "Server Kustom",
        provider = "LibreSpeed / HTTPS",
        description = "Gunakan alamat server uji kecepatan HTTPS pilihan Anda",
        isCustom = true
    )
)

@Immutable
data class SpeedtestSnapshot(
    val serverLabel: String = "",
    val idleLatencyMs: Float = 0f,
    val idleJitterMs: Float = 0f,
    val downloadMbps: Float = 0f,
    val downloadLoadedLatencyMs: Float = 0f,
    val uploadMbps: Float = 0f,
    val uploadLoadedLatencyMs: Float = 0f,
    val bufferbloatGrade: String = "",
    val tcpAlgo: String = "",
    val qdisc: String = "",
    val timestampMs: Long = 0L
)

@Immutable
data class SpeedtestUiState(
    val isSheetOpen: Boolean = false,
    val selectedServerId: String = "cloudflare",
    val customBaseUrl: String = "",
    val phase: SpeedtestPhase = SpeedtestPhase.IDLE,
    val progress: Float = 0f,
    val statusText: String = "Siap menguji kecepatan & kestabilan ping",
    val serverEdgeLabel: String = "",
    val idleLatencyMs: Float = 0f,
    val idleJitterMs: Float = 0f,
    val downloadMbps: Float = 0f,
    val downloadPeakMbps: Float = 0f,
    val downloadLoadedLatencyMs: Float = 0f,
    val uploadMbps: Float = 0f,
    val uploadPeakMbps: Float = 0f,
    val uploadLoadedLatencyMs: Float = 0f,
    val totalTransferredMb: Float = 0f,
    val speedSamples: List<Float> = emptyList(),
    val bufferbloatGrade: String = "",
    val bufferbloatSummary: String = "",
    val baselineResult: SpeedtestSnapshot? = null,
    val errorMessage: String? = null,
    val lastTestedAtMs: Long = 0L,
    val activeIfaceLabel: String = "",
    val tcpAlgoSnapshot: String = "",
    val qdiscSnapshot: String = ""
) {
    val isRunning: Boolean
        get() = phase == SpeedtestPhase.PING || phase == SpeedtestPhase.DOWNLOAD || phase == SpeedtestPhase.UPLOAD

    val hasResult: Boolean
        get() = downloadMbps > 0f || uploadMbps > 0f || idleLatencyMs > 0f
}
