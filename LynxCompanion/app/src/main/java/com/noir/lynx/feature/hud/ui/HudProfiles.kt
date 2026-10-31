package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudProfiles(current: String?, busy: Boolean, onProfile: (String) -> Unit) {
    var extreme by rememberSaveable { mutableStateOf(false) }
    val normalized = normalizeProfile(current)
    val activeLabelRes = hudProfileLabel(normalized)
    val activeColor = hudProfileColor(normalized)
    val extremeColor = hudProfileColor("extreme")

    val profilesInfo = hudString(R.string.hud_profile_help)

    HudSection(
        title = hudString(R.string.hud_profile),
        badge = hudString(activeLabelRes),
        accentColor = activeColor,
        info = profilesInfo
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val primary = listOf("powersave" to hudString(R.string.hud_save), "balance" to hudString(R.string.hud_balanced),
                "performance" to hudString(R.string.hud_performance))
            HudAdaptiveGrid(primary.map { it.second }, maximum = 2) { index ->
                val (key, label) = primary[index]
                HudChoice(label, normalized == key, { onProfile(key) }, Modifier.weight(1f), enabled = !busy, accentColor = hudProfileColor(key))
            }
            var more by rememberSaveable { mutableStateOf(false) }
            HudDisclosure(hudString(R.string.hud_profile_more), more) { more = !more }
            if (more) {
                HudChoice(hudString(R.string.hud_auto), normalized == "auto", { onProfile("auto") }, Modifier.fillMaxWidth(), enabled = !busy)
                HudChoice(hudString(R.string.hud_extreme), normalized == "extreme", { extreme = true }, Modifier.fillMaxWidth(), enabled = !busy, accentColor = extremeColor)
            }

            if (busy) {
                Text(
                    text = hudString(R.string.hud_applying),
                    fontSize = 12.sp,
                    color = activeColor
                )
            }

            if (extreme) {
                Surface(
                    color = HudElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, extremeColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = hudString(R.string.hud_extreme_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = extremeColor
                        )
                        Text(
                            text = hudString(R.string.hud_extreme_warning),
                            fontSize = 12.sp,
                            color = HudMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HudAction(
                                label = hudString(R.string.hud_cancel),
                                enabled = !busy,
                                onClick = { extreme = false },
                                modifier = Modifier.weight(1f)
                            )
                            HudAction(
                                label = hudString(R.string.hud_confirm),
                                enabled = !busy,
                                onClick = {
                                    extreme = false
                                    onProfile("extreme")
                                },
                                modifier = Modifier.weight(1f),
                                isDanger = true
                            )
                        }
                    }
                }
            }
        }
    }
}
