package com.noir.lynx.data

/**
 * Persistent configuration model for kernel panel hardware tuning:
 * - True DCS 0x55 Content Adaptive Backlight Control (CABC) via LCM_CABC
 * - MediaTek Display ESD Watchdog polling interval (/proc/mtk_esd_period_ms)
 */
data class PanelTuningConfig(
    val cabcMode: Int = DEFAULT_CABC_MODE,
    val esdPeriodMs: Int = DEFAULT_ESD_PERIOD_MS,
    val applyOnBoot: Boolean = true,
) {
    fun sanitized(): PanelTuningConfig = copy(
        cabcMode = cabcMode.coerceIn(MIN_CABC_MODE, MAX_CABC_MODE),
        esdPeriodMs = clampEsdPeriodMs(esdPeriodMs),
    )

    companion object {
        const val MIN_CABC_MODE = 0
        const val MAX_CABC_MODE = 3
        const val DEFAULT_CABC_MODE = 0

        const val ESD_PAUSED_MS = 0
        const val MIN_ACTIVE_ESD_PERIOD_MS = 2_000
        const val MAX_ESD_PERIOD_MS = 60_000
        const val STOCK_MTK_ESD_PERIOD_MS = 5_000
        const val DEFAULT_ESD_PERIOD_MS = 15_000

        val STOCK_BASELINE = PanelTuningConfig()

        fun clampEsdPeriodMs(raw: Int): Int {
            return if (raw <= 0) {
                ESD_PAUSED_MS
            } else {
                raw.coerceIn(MIN_ACTIVE_ESD_PERIOD_MS, MAX_ESD_PERIOD_MS)
            }
        }
    }
}

/**
 * Live hardware capability & active state snapshot read from sysfs/procfs.
 */
data class PanelTuningHardwareSupport(
    val probed: Boolean = false,
    val cabcSupported: Boolean = false,
    val esdSupported: Boolean = false,
    val activeCabcMode: Int = PanelTuningConfig.DEFAULT_CABC_MODE,
    val activeEsdPeriodMs: Int = PanelTuningConfig.DEFAULT_ESD_PERIOD_MS,
    val lastWriteOk: Boolean = true,
) {
    val hasAnySupport: Boolean
        get() = cabcSupported || esdSupported
}
