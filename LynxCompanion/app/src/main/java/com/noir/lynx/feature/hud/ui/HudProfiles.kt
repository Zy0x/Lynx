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

    val activeLabelRes = when (current) {
        "powersave" -> R.string.hud_save
        "balance" -> R.string.hud_balanced
        "performance" -> R.string.hud_performance
        "auto" -> R.string.hud_auto
        "extreme" -> R.string.hud_extreme
        "dormant" -> R.string.hud_dormant
        else -> null
    }

    val profilesInfo = "Profil Pengoptimalan Kernel:\n\n" +
        "• Powersave (Hemat): Menurunkan frekuensi CPU/GPU dan menghemat daya baterai.\n\n" +
        "• Balanced (Seimbang): Keseimbangan dinamis responsivitas dan konsumsi baterai.\n\n" +
        "• Performance (Performa): Kunci frekuensi tinggi untuk kelancaran game yang konsisten.\n\n" +
        "• Auto (AI): Berpindah profil otomatis mendeteksi game yang sedang aktif di layar.\n\n" +
        "• Extreme: Mengunci frekuensi clock maksimum tanpa batas termal (disarankan menggunakan pendingin eksternal)."

    HudSection(
        title = hudString(R.string.hud_profile),
        badge = activeLabelRes?.let { hudString(it) },
        accentColor = if (current == "extreme") HudOrange else HudCyan,
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
            val cell = hudLabelWidth(primary.map { it.second }) + 16f
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (HudLayoutPolicy.fits(maxWidth.value, List(3) { cell }, gap = 6f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        primary.forEach { (key, label) -> HudChoice(label, current == key, { onProfile(key) }, Modifier.weight(1f), enabled = !busy) }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    primary.forEach { (key, label) -> HudChoice(label, current == key, { onProfile(key) }, Modifier.fillMaxWidth(), enabled = !busy) }
                }
            }
            var more by rememberSaveable { mutableStateOf(false) }
            HudDisclosure(hudString(R.string.hud_profile_more), more) { more = !more }
            if (more) {
                HudChoice(hudString(R.string.hud_auto), current == "auto", { onProfile("auto") }, Modifier.fillMaxWidth(), enabled = !busy)
                HudChoice(hudString(R.string.hud_extreme), current == "extreme", { extreme = true }, Modifier.fillMaxWidth(), enabled = !busy, accentColor = HudOrange)
            }

            if (busy) {
                Text(
                    text = hudString(R.string.hud_applying),
                    fontSize = 12.sp,
                    color = HudBlue
                )
            }

            if (extreme) {
                Surface(
                    color = HudElevated,
                    shape = RoundedCornerShape(12.dp),
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
                            color = HudOrange
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
