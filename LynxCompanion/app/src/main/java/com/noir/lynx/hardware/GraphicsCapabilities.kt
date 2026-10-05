package com.noir.lynx.hardware

enum class GpuBackend {
    KGSL,
    MTK_GED,
    MALI_DEVFREQ,
    EXYNOS_SYSFS,
    GENERIC_DEVFREQ,
    NONE
}

data class NodeStatus(
    val path: String,
    val label: String,
    val readable: Boolean,
    val writable: Boolean
)

data class DisplayModeInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val fps: Float
)

data class GraphicsCapabilities(
    val gpuVendor: String = "Unknown",
    val gpuModel: String = "Generic GPU",
    val glesVersion: String = "OpenGL ES",
    val driverVersion: String = "Unknown",
    val vulkanVersion: String? = null,
    val vulkanDriverId: String? = null,
    val backend: GpuBackend = GpuBackend.NONE,
    val gpuTempNode: String? = null,
    val memBusNodes: List<String> = emptyList(),
    val displayModes: List<DisplayModeInfo> = emptyList(),
    val hdrTypes: List<String> = emptyList(),
    val wideColor: Boolean = false,
    val maxLuminanceNits: Float? = null,
    val hasKcal: Boolean = false,
    val dcDimmingNode: String? = null,
    val hbmNode: String? = null,
    val nodes: List<NodeStatus> = emptyList(),
    // Anti-Spoofing & Detailed Telemetry
    val isSpoofed: Boolean = false,
    val spoofedGpuModel: String? = null,
    val spoofedSoc: String? = null,
    val groundTruthSoc: String = "Generic",
    val groundTruthGpu: String = "Generic",
    val gpuDriverPath: String = "",
    val displayDpi: Int = 0,
    val displayDensity: Float = 0f,
    val displayColorMode: String = "Standard",
    val surfaceFlingerHwc: String = "Hardware Composer"
)

sealed class WriteResult {
    object Applied : WriteResult()
    object Unsupported : WriteResult()
    data class Rejected(val readBack: String) : WriteResult()
}
