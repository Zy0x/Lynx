package com.noir.lynx.hardware.gpu

import com.noir.lynx.data.ConfidenceRating
import com.noir.lynx.data.FeatureAccessState
import com.noir.lynx.data.FeatureUiType
import com.noir.lynx.data.GpuHardwareFeature
import com.noir.lynx.data.HardwareConfidence
import com.noir.lynx.hardware.NodeWriter
import com.noir.lynx.hardware.WriteResult
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Hardware Abstraction Layer (HAL) for Mobile GPUs.
 * Decouples system UI from SoC specifics (Qualcomm, MediaTek, Mali, Generic Devfreq).
 */
interface GpuBackendAdapter {
    val backendId: String
    val displayName: String
    val architectureName: String

    suspend fun isSupported(): Boolean
    suspend fun scanFeatures(): List<GpuHardwareFeature>
    suspend fun applyPowerPolicy(policy: String): WriteResult
    suspend fun setBoost(level: Int): WriteResult
    suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult
    suspend fun setGovernor(governor: String): WriteResult
    suspend fun setThermalBypass(enabled: Boolean): WriteResult
    suspend fun getConfidence(): HardwareConfidence
}

/**
 * Qualcomm Snapdragon Adreno Adapter (KGSL Driver Architecture)
 */
object GpuBackendManager {
    private val adapters = listOf(
        QualcommAdrenoAdapter(),
        MediaTekGedAdapter(),
        MaliKbaseAdapter(),
        GenericDevfreqAdapter()
    )

    private var cachedAdapter: GpuBackendAdapter? = null

    suspend fun getActiveAdapter(): GpuBackendAdapter = withContext(Dispatchers.IO) {
        cachedAdapter?.let { return@withContext it }
        for (adapter in adapters) {
            if (adapter.isSupported()) {
                cachedAdapter = adapter
                return@withContext adapter
            }
        }
        val fallback = GenericDevfreqAdapter()
        cachedAdapter = fallback
        fallback
    }

    fun invalidateCache() {
        cachedAdapter = null
    }
}
