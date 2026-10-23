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

class QualcommAdrenoAdapter : GpuBackendAdapter {
    override val backendId: String = "qualcomm_kgsl"
    override val displayName: String = "Qualcomm Adreno KGSL HAL"
    override val architectureName: String = "Qualcomm Adreno"

    private val kgslDir = "/sys/class/kgsl/kgsl-3d0"
    private val devfreqDir = "$kgslDir/devfreq"

    private fun resolveActiveDevfreq(): String {
        val found = Shell.cmd(
            "for d in '$devfreqDir' /sys/devices/platform/soc/*.qcom,kgsl-3d0/devfreq/* /sys/class/devfreq/*kgsl-3d0*; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim()
        return if (!found.isNullOrBlank()) found else devfreqDir
    }

    override suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -d '$kgslDir' ]").exec().isSuccess
    }

    override suspend fun scanFeatures(): List<GpuHardwareFeature> = withContext(Dispatchers.IO) {
        val resolvedDevfreq = resolveActiveDevfreq()

        val resolvedGpubw = Shell.cmd(
            "for d in /sys/class/devfreq/*gpubw* /sys/devices/platform/soc/soc:qcom,gpubw/devfreq/*; do [ -d \"\$d\" ] && echo \"\$d\" && break; done"
        ).exec().out.firstOrNull()?.trim() ?: ""

        val candidatePaths = mutableListOf(
            "$kgslDir/force_bus_on",
            "$kgslDir/idle_timer",
            "$kgslDir/force_rail_on",
            "$kgslDir/force_no_nap",
            "$kgslDir/bus_split",
            "$kgslDir/throttling",
            "$kgslDir/thermal_pwrlevel",
            "$kgslDir/default_pwrlevel",
            "$kgslDir/pwrscale/trustzone/target_load",
            "$resolvedDevfreq/adrenoboost",
            "$resolvedDevfreq/adreno_boost",
            "$resolvedDevfreq/governor",
            "$kgslDir/wake_on_touch",
            "$kgslDir/snapshot/snapshot_crashdumper",
            "$kgslDir/max_pwrlevel",
            "$kgslDir/ifpc",
            "$kgslDir/preemption",
            "$resolvedDevfreq/polling_interval",
            "$kgslDir/pmqos_active_latency",
            "$kgslDir/hwcg",
            "$kgslDir/force_clk_on",
            "$kgslDir/ft_long_ib_detect"
        )
        if (resolvedGpubw.isNotEmpty()) {
            candidatePaths.add("$resolvedGpubw/governor")
        }

        val probeMap = NodeWriter.batchProbe(candidatePaths)
        val features = mutableListOf<GpuHardwareFeature>()

        // 1. Adreno Devfreq Governor
        val govPath = "$resolvedDevfreq/governor"
        probeMap[govPath]?.let { access ->
            if (access.exists) {
                val rawGov = Shell.cmd("cat '$govPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "msm-adreno-tz"
                val curGov = NodeWriter.extractActiveValue(rawGov)
                val availGovs = Shell.cmd("cat '$resolvedDevfreq/available_governors' 2>/dev/null").exec().out.firstOrNull()?.trim()
                    ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() } ?: listOf("msm-adreno-tz", "performance", "powersave", "simple_ondemand")
                features.add(
                    GpuHardwareFeature(
                        id = "devfreq_governor",
                        name = "Adreno Devfreq Governor",
                        description = "Algoritma penskalaan frekuensi perangkat keras GPU Qualcomm Adreno",
                        nodePath = govPath,
                        currentValue = curGov,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = availGovs,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 2. Adreno Boost (supports both adrenoboost and adreno_boost kernel nodes)
        val boostNode = if (probeMap["$resolvedDevfreq/adrenoboost"]?.exists == true) "$resolvedDevfreq/adrenoboost" else "$resolvedDevfreq/adreno_boost"
        probeMap[boostNode]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$boostNode' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adrenoboost_level",
                        name = "Adreno Boost Level",
                        description = "Tingkat agresivitas devfreq boost Adreno untuk transisi beban instan",
                        nodePath = boostNode,
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("0", "1", "2", "3"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 3. Idle Timer
        probeMap["$kgslDir/idle_timer"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/idle_timer' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "64"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_idle_timer",
                        name = "Adreno Idle Timer (Waktu Tahan Clock)",
                        description = "Mencegah penurunan clock GPU tiba-tiba saat jeda frame antar render",
                        nodePath = "$kgslDir/idle_timer",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("20", "40", "64", "80", "100"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 4. Trustzone Target Load
        probeMap["$kgslDir/pwrscale/trustzone/target_load"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/pwrscale/trustzone/target_load' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "80"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_tz_target_load",
                        name = "Trustzone TZ Target Load",
                        description = "Ambang batas beban sebelum GPU melompat ke frekuensi lebih tinggi",
                        nodePath = "$kgslDir/pwrscale/trustzone/target_load",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = listOf("50", "60", "70", "80"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 5. Memory Bus Always-On
        probeMap["$kgslDir/force_bus_on"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_bus_on' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "kgsl_force_bus_on",
                        name = "KGSL Memory Bus Always-On",
                        description = "Kunci jalur DDR memory bus Adreno tetap aktif mencegah micro-stutter saat game",
                        nodePath = "$kgslDir/force_bus_on",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 6. Force Rail Active
        probeMap["$kgslDir/force_rail_on"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_rail_on' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_force_rail",
                        name = "Adreno Force Rail Active",
                        description = "Paksa jalur daya power rail GPU aktif bertenaga selama gaming",
                        nodePath = "$kgslDir/force_rail_on",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 7. Force No Nap (Anti-Slumber)
        probeMap["$kgslDir/force_no_nap"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/force_no_nap' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_force_no_nap",
                        name = "Adreno Anti-Nap (Cegah Tidur Mikro)",
                        description = "Mencegah GPU masuk ke status nap/slumber di sela komposisi frame",
                        nodePath = "$kgslDir/force_no_nap",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 8. Unified Bus Bandwidth (Disable Bus Split: node 0 = active, 1 = default split)
        probeMap["$kgslDir/bus_split"]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kgslDir/bus_split' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isSplitDisabled = if (NodeWriter.extractActiveValue(rawVal) == "0") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_bus_split_disable",
                        name = "Unified Bus Bandwidth (Nonaktifkan Bus Split)",
                        description = "Satukan alokasi bandwidth bus GPU-DDR penuh tanpa pemisahan kanal hemat daya",
                        nodePath = "$kgslDir/bus_split",
                        currentValue = isSplitDisabled,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 9. GPU-to-DDR Bandwidth Devfreq Governor (GPUBW)
        if (resolvedGpubw.isNotEmpty()) {
            val gpubwGovPath = "$resolvedGpubw/governor"
            probeMap[gpubwGovPath]?.let { access ->
                if (access.exists) {
                    val rawGov = Shell.cmd("cat '$gpubwGovPath' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "bw_vbif"
                    val curGov = NodeWriter.extractActiveValue(rawGov)
                    val availGovs = Shell.cmd("cat '$resolvedGpubw/available_governors' 2>/dev/null").exec().out.firstOrNull()?.trim()
                        ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() } ?: listOf("bw_vbif", "performance", "powersave")
                    features.add(
                        GpuHardwareFeature(
                            id = "adreno_gpubw_governor",
                            name = "Governor Bus Memori DDR GPU (GPUBW)",
                            description = "Mengatur kebijakan bandwidth interkoneksi antara GPU Adreno dan RAM LPDDR",
                            nodePath = gpubwGovPath,
                            currentValue = curGov,
                            accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                            confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                            uiType = FeatureUiType.CHOICE,
                            options = availGovs,
                            category = "Bus Memori & Power"
                        )
                    )
                }
            }
        }

        // 10. Bypass Thermal Throttling (prefers throttling node, falls back to thermal_pwrlevel; 0 in kernel = bypass active)
        val thrmNode = if (probeMap["$kgslDir/throttling"]?.exists == true) "$kgslDir/throttling" else "$kgslDir/thermal_pwrlevel"
        probeMap[thrmNode]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$thrmNode' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isBypassed = if (NodeWriter.extractActiveValue(rawVal) == "0") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_thermal_bypass",
                        name = "Bypass GPU Thermal Throttling",
                        description = "Abaikan pembatasan pwrlevel & throttling termal driver KGSL Qualcomm",
                        nodePath = thrmNode,
                        currentValue = isBypassed,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Proteksi Termal"
                    )
                )
            }
        }

        // 11. Adreno Touch-Wake (bangunkan GPU saat layar pertama kali disentuh)
        probeMap["$kgslDir/wake_on_touch"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/wake_on_touch' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_wake_on_touch",
                        name = "Adreno Touch-Wake (Bangun GPU via Sentuhan)",
                        description = "Aktifkan GPU Adreno seketika saat jari menyentuh layar, sebelum frame pertama dirender — memangkas input-to-display latency",
                        nodePath = "$kgslDir/wake_on_touch",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 12. KGSL Snapshot Crash Dumper Suppression (mencegah freeze 1-2 detik saat shader glitch)
        probeMap["$kgslDir/snapshot/snapshot_crashdumper"]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kgslDir/snapshot/snapshot_crashdumper' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isSuppressed = if (NodeWriter.extractActiveValue(rawVal) == "0") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_crashdumper_suppress",
                        name = "Supresi KGSL Crash Dumper (Anti-Freeze)",
                        description = "Matikan crash snapshot dumper GPU agar tidak memicu freeze 1-2 detik saat game mengalami shader glitch kecil",
                        nodePath = "$kgslDir/snapshot/snapshot_crashdumper",
                        currentValue = isSuppressed,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Stabilitas & Anti-Freeze"
                    )
                )
            }
        }

        // 13. KGSL Max PwrLevel Clamping (mengunci batas teratas clock GPU)
        probeMap["$kgslDir/max_pwrlevel"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/max_pwrlevel' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_max_pwrlevel",
                        name = "Kunci Batas Atas Clock GPU (Max PwrLevel)",
                        description = "Level 0 = clock GPU maksimum penuh. Level tinggi = pembatasan atas frekuensi GPU (hemat daya, batas termal)",
                        nodePath = "$kgslDir/max_pwrlevel",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 85, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.CHOICE,
                        options = listOf("0", "1", "2", "3"),
                        category = "Proteksi Termal"
                    )
                )
            }
        }

        // 14. In-Frame Power Collapse (IFPC) Anti-Stutter
        probeMap["$kgslDir/ifpc"]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kgslDir/ifpc' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isAntiStutterActive = if (NodeWriter.extractActiveValue(rawVal) == "0") "1" else "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_ifpc",
                        name = "In-Frame Power Collapse (Anti-Stutter)",
                        description = "Matikan siklus tidur mikro (IFPC) di sela render frame agar GPU selalu aktif dan bebas drop frame saat adegan game berat",
                        nodePath = "$kgslDir/ifpc",
                        currentValue = isAntiStutterActive,
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 15. GPU Hardware Preemption
        probeMap["$kgslDir/preemption"]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kgslDir/preemption' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_preemption",
                        name = "Hardware Preemption GPU",
                        description = "Prioritaskan antrean rendering SurfaceFlinger dan sentuhan layar di atas komputasi berat untuk memangkas latensi input sentuh",
                        nodePath = "$kgslDir/preemption",
                        currentValue = NodeWriter.extractActiveValue(rawVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 16. Devfreq Polling Interval (Sampling Latency)
        probeMap["$resolvedDevfreq/polling_interval"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$resolvedDevfreq/polling_interval' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "50"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_polling_interval",
                        name = "Devfreq Polling Interval (Sampling Latency)",
                        description = "Interval waktu sampling beban devfreq. 10ms-20ms membuat GPU melompat seketika ke clock tinggi saat aksi visual mendadak",
                        nodePath = "$resolvedDevfreq/polling_interval",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("10", "20", "50", "100"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 17. PM-QoS Active Latency (Interconnect Zero Wakeup)
        probeMap["$kgslDir/pmqos_active_latency"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/pmqos_active_latency' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "33"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_pmqos_latency",
                        name = "PM-QoS Active Latency",
                        description = "Toleransi jeda respon interconnect GPU saat bangun dari idle. 0 µs untuk respon frame instan tanpa lag",
                        nodePath = "$kgslDir/pmqos_active_latency",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("0", "33", "100", "300"),
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 18. Hardware Clock Gating (HWCG Anti-Lag)
        probeMap["$kgslDir/hwcg"]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kgslDir/hwcg' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1"
                val isAntiLag = rawVal == "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_hwcg",
                        name = "Anti-Lag Clock Gating (Matikan HWCG)",
                        description = "Matikan clock gating dinamis internal ALU saat gaming agar gerbang komputasi grafis selalu terbuka tanpa jeda switching",
                        nodePath = "$kgslDir/hwcg",
                        currentValue = if (isAntiLag) "1" else "0",
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Responsivitas Clock"
                    )
                )
            }
        }

        // 19. Paksa Clock GPU Tetap Berdetak (Force Clk)
        probeMap["$kgslDir/force_clk_on"]?.let { access ->
            if (access.exists) {
                val rawVal = Shell.cmd("cat '$kgslDir/force_clk_on' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_force_clk",
                        name = "Paksa Clock GPU Tetap Aktif",
                        description = "Memaksa osilator clock GPU terus aktif berdenyut mencegah clock drop ke 0 MHz di sela pergantian frame",
                        nodePath = "$kgslDir/force_clk_on",
                        currentValue = NodeWriter.extractActiveValue(rawVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 95, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.SWITCH,
                        category = "Bus Memori & Power"
                    )
                )
            }
        }

        // 20. Toleransi Timeout Hang Shader (Long IB Detect)
        probeMap["$kgslDir/ft_long_ib_detect"]?.let { access ->
            if (access.exists) {
                val curVal = Shell.cmd("cat '$kgslDir/ft_long_ib_detect' 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "2000"
                features.add(
                    GpuHardwareFeature(
                        id = "adreno_ft_long_ib",
                        name = "Toleransi Timeout Hang Shader (Long IB)",
                        description = "Ambang batas timeout pemulihan shader hang. Mencegah force close emulator berat saat kompilasi shader panjang",
                        nodePath = "$kgslDir/ft_long_ib_detect",
                        currentValue = NodeWriter.extractActiveValue(curVal),
                        accessState = if (access.writable) FeatureAccessState.VERIFIED_WORKING else FeatureAccessState.READ_ONLY_LOCKED,
                        confidence = HardwareConfidence(100, if (access.writable) 100 else 0, 90, if (access.writable) ConfidenceRating.HIGH_CONFIDENCE else ConfidenceRating.READ_ONLY_LOCK),
                        uiType = FeatureUiType.STEPPER,
                        options = listOf("1000", "2000", "4000", "8000"),
                        category = "Stabilitas & Anti-Freeze"
                    )
                )
            }
        }

        features
    }

    override suspend fun applyPowerPolicy(policy: String): WriteResult = withContext(Dispatchers.IO) {
        if (policy == "responsive" || policy == "always_on") {
            NodeWriter.writeVerified("$kgslDir/idle_timer", "80")
            NodeWriter.writeVerified("$kgslDir/force_bus_on", "1")
        } else {
            NodeWriter.writeVerified("$kgslDir/idle_timer", "20")
            NodeWriter.writeVerified("$kgslDir/force_bus_on", "0")
        }
    }

    override suspend fun setBoost(level: Int): WriteResult = withContext(Dispatchers.IO) {
        val activeDevfreq = resolveActiveDevfreq()
        val target = level.coerceIn(0, 3).toString()
        val r1 = NodeWriter.writeVerified("$activeDevfreq/adrenoboost", target)
        if (r1 is WriteResult.Applied) return@withContext r1
        NodeWriter.writeVerified("$activeDevfreq/adreno_boost", target)
    }

    override suspend fun setFrequencyRange(minMhz: Int?, maxMhz: Int?): WriteResult = withContext(Dispatchers.IO) {
        val activeDevfreq = resolveActiveDevfreq()

        // Read available frequencies to build descending OPP table (Index 0 = Highest Clock in KGSL)
        val availRaw = Shell.cmd(
            "cat $kgslDir/gpu_available_frequencies 2>/dev/null || cat $activeDevfreq/available_frequencies 2>/dev/null"
        ).exec().out.joinToString(" ").trim()

        val freqsDesc = availRaw.split(Regex("\\s+"))
            .mapNotNull { it.toLongOrNull() }
            .map { if (it > 1_000_000L) (it / 1_000_000L).toInt() else if (it > 10_000L) (it / 1000L).toInt() else it.toInt() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

        // Detect devfreq unit
        val rawTokens = availRaw.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        val isDevfreqHz = rawTokens.any { it > 10_000_000L }
        val isDevfreqKhz = !isDevfreqHz && rawTokens.any { it > 10_000L }

        var res: WriteResult = WriteResult.Applied

        // 1. Calculate OPP Power Level indices
        // In Qualcomm KGSL driver architecture:
        // Index 0 = highest clock (e.g. 750 MHz)
        // Highest index = lowest clock (e.g. 160 MHz)
        // max_pwrlevel = MAXIMUM PERFORMANCE / CEILING clock (lower numerical index)
        // min_pwrlevel = MINIMUM PERFORMANCE / FLOOR clock (higher numerical index)
        val lowestIdx = (freqsDesc.size - 1).coerceAtLeast(0)
        val maxPwrIdx = if (maxMhz != null && maxMhz > 0 && freqsDesc.isNotEmpty()) {
            val idx = freqsDesc.indexOfFirst { it <= maxMhz }
            if (idx >= 0) idx else 0
        } else 0

        val minPwrIdx = if (minMhz != null && minMhz > 0 && freqsDesc.isNotEmpty()) {
            val idx = freqsDesc.indexOfLast { it >= minMhz }
            if (idx >= 0) idx else lowestIdx
        } else lowestIdx

        // 2. Write Devfreq and direct KGSL clock limits with safe ordering (anti -EINVAL)
        val lowestMhz = freqsDesc.lastOrNull() ?: 160
        val lowestValStr = when {
            isDevfreqHz -> (lowestMhz * 1_000_000L).toString()
            isDevfreqKhz -> (lowestMhz * 1_000L).toString()
            else -> lowestMhz.toString()
        }
        // Step A: Relax min_freq first to prevent kernel -EINVAL when lowering max_freq
        NodeWriter.writeVerified("$activeDevfreq/min_freq", lowestValStr)

        // Step B: Set max_freq ceiling
        if (maxMhz != null && maxMhz > 0) {
            val maxValStr = when {
                isDevfreqHz -> (maxMhz * 1_000_000L).toString()
                isDevfreqKhz -> (maxMhz * 1_000L).toString()
                else -> maxMhz.toString()
            }
            val r2 = NodeWriter.writeVerified("$activeDevfreq/max_freq", maxValStr)
            NodeWriter.writeVerified("$kgslDir/max_clock_mhz", maxMhz.toString())
            NodeWriter.writeVerified("$kgslDir/max_gpuclk", (maxMhz * 1_000_000L).toString())
            if (r2 is WriteResult.Rejected) res = r2
        }

        // Step C: Set min_freq floor
        if (minMhz != null && minMhz > 0) {
            val minValStr = when {
                isDevfreqHz -> (minMhz * 1_000_000L).toString()
                isDevfreqKhz -> (minMhz * 1_000L).toString()
                else -> minMhz.toString()
            }
            val r1 = NodeWriter.writeVerified("$activeDevfreq/min_freq", minValStr)
            NodeWriter.writeVerified("$kgslDir/min_clock_mhz", minMhz.toString())
            if (r1 is WriteResult.Rejected) res = r1
        }

        // 3. Apply KGSL Power Levels with 3-step safety sequence:
        // The Linux kernel enforces `min_pwrlevel >= max_pwrlevel` at all times.
        // Step A: Relax min_pwrlevel to lowestIdx so min_pwrlevel is at maximum numerical index.
        // Step B: Set max_pwrlevel to maxPwrIdx (safely <= lowestIdx).
        // Step C: Set min_pwrlevel to safeMinPwr (safely >= maxPwrIdx).
        // Step D: Set default_pwrlevel to maxPwrIdx to prevent idle wake upsurge.
        if (freqsDesc.isNotEmpty()) {
            val safeMinPwr = minPwrIdx.coerceAtLeast(maxPwrIdx)
            val clampScript = """
                chmod 644 '$kgslDir/min_pwrlevel' '$kgslDir/max_pwrlevel' '$kgslDir/default_pwrlevel' 2>/dev/null
                echo $lowestIdx > '$kgslDir/min_pwrlevel' 2>/dev/null
                echo $maxPwrIdx > '$kgslDir/max_pwrlevel' 2>/dev/null
                echo $safeMinPwr > '$kgslDir/min_pwrlevel' 2>/dev/null
                echo $maxPwrIdx > '$kgslDir/default_pwrlevel' 2>/dev/null
                echo 1 > '$kgslDir/pwrscale' 2>/dev/null
            """.trimIndent()
            Shell.cmd(clampScript).exec()
        }

        res
    }

    override suspend fun setGovernor(governor: String): WriteResult = withContext(Dispatchers.IO) {
        val activeDevfreq = resolveActiveDevfreq()
        NodeWriter.writeVerified("$activeDevfreq/governor", governor)
    }

    override suspend fun setThermalBypass(enabled: Boolean): WriteResult = withContext(Dispatchers.IO) {
        val valStr = if (enabled) "0" else "1"
        val r1 = NodeWriter.writeVerified("$kgslDir/throttling", valStr)
        if (enabled) {
            NodeWriter.writeVerified("$kgslDir/thermal_pwrlevel", "0")
        }
        if (r1 is WriteResult.Applied) r1 else NodeWriter.writeVerified("$kgslDir/thermal_pwrlevel", valStr)
    }

    override suspend fun getConfidence(): HardwareConfidence = withContext(Dispatchers.IO) {
        HardwareConfidence(100, 100, 95, ConfidenceRating.HIGH_CONFIDENCE)
    }
}

/**
 * MediaTek Dimensity & Helio Adapter (GED HAL, FPSGO, GPUFreq v1/v2 & Mali/PowerVR Architecture)
 */
