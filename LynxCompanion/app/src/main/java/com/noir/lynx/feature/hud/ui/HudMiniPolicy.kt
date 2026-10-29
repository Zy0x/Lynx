package com.noir.lynx.feature.hud.ui

import com.noir.lynx.feature.hud.data.HudMetric

/** Pin FPS always has a visible first page, including an explicitly empty optional selection. */
internal object HudMiniPolicy {
    fun metrics(selected: Set<String>): List<HudMetric> = listOf(HudMetric.FPS) +
        HudMetric.values().filter { it != HudMetric.FPS && it.key in selected }
}
