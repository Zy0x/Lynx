package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudLab(
    snapshot: HudSnapshot,
    action: HudActionState,
    onProfile: (String) -> Unit,
    onBoostRam: () -> Unit = {}
) {
    val ram = snapshot[HudMetric.RAM_USED]
    val isCleaningRam = action.actionType == "ram" && action.busy
    val cpuTemp = snapshot[HudMetric.CPU_TEMP].takeIf { it.status == "valid" } ?: snapshot[HudMetric.SOC_TEMP]
    val batTemp = snapshot[HudMetric.BATTERY_TEMP]

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Engine & Active Mode Banner
        Surface(
            color = HudCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, HudCyan.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HudCyan.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = HudCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lynx Deity Engine",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Hardware Kernel Tuning & Dynamic Governor",
                        fontSize = 11.sp,
                        color = HudMuted
                    )
                }
                Surface(
                    color = if (snapshot.profile == "extreme") HudOrange.copy(alpha = 0.15f) else HudCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (snapshot.profile == "extreme") HudOrange.copy(alpha = 0.4f) else HudCyan.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = hudString(hudProfileLabel(snapshot.profile)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (snapshot.profile == "extreme") HudOrange else HudCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 2. Performance Profiles Selector
        HudProfiles(
            current = snapshot.profile,
            busy = action.busy,
            onProfile = onProfile
        )

        // 3. RAM & Kernel Cache Cleaner
        HudSection(
            title = hudString(R.string.hud_metric_ram),
            badge = if (isCleaningRam) "⚡ CLEANING" else if (ram.status == "valid") "${ram.text()}%" else null,
            accentColor = HudCyan,
            info = "Booster Memori Cepat:\n\n• Membersihkan cached pagecache, dentries, dan inodes kernel Linux.\n• Mengembalikan RAM bebas ke aplikasi game untuk mencegah micro-stutter."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = HudMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Penggunaan RAM",
                            fontSize = 12.sp,
                            color = HudMuted
                        )
                    }
                    Text(
                        text = if (ram.status == "valid") "${ram.text()} %" else "—",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isCleaningRam) HudCyan else Color.White
                    )
                }

                HudAction(
                    label = if (isCleaningRam) "⚡ Sedang Membersihkan..." else "⚡ Boost RAM & Hapus Cache",
                    enabled = !action.busy,
                    onClick = onBoostRam,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. Thermal & Headroom
        HudSection(
            title = "Thermal & Power Headroom",
            badge = if (cpuTemp.status == "valid") "${cpuTemp.text()}°C" else null,
            accentColor = HudBlue,
            info = "Pemantau Suhu & Daya:\n\n• Memastikan hardware tetap dalam rentang temperatur optimal saat mode performa aktif."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = HudCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Thermostat, null, tint = HudBlue, modifier = Modifier.size(14.dp))
                            Text("Suhu CPU/SoC", fontSize = 11.sp, color = HudMuted)
                        }
                        Text(
                            text = if (cpuTemp.status == "valid") "${cpuTemp.text()} °C" else "—",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = HudCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Thermostat, null, tint = HudOrange, modifier = Modifier.size(14.dp))
                            Text("Suhu Baterai", fontSize = 11.sp, color = HudMuted)
                        }
                        Text(
                            text = if (batTemp.status == "valid") "${batTemp.text()} °C" else "—",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
