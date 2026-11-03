package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

internal enum class HudBoostSubpage(val titleRes: Int) {
    Touch(R.string.hud_lab_touch_title),
    Network(R.string.hud_lab_network_title),
    Bypass(R.string.hud_lab_bypass_title)
}

private data class BoostCardItem(
    val subpage: HudBoostSubpage,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val badge: String,
    val accentColor: Color
)

@Composable
internal fun HudLab(
    snapshot: HudSnapshot,
    action: HudActionState,
    selectedSubpage: HudBoostSubpage? = null,
    onSelectSubpage: (HudBoostSubpage) -> Unit = {},
    onBoostRam: () -> Unit = {},
    bypassStatus: HudBypassStatus = HudBypassStatus(),
    onToggleBypass: (Boolean) -> Unit = {},
    onRefreshBypass: () -> Unit = {},
    onProbeNetwork: suspend () -> HudNetworkSample = { HudNetworkSample() },
    touchStatus: HudTouchStatus = HudTouchStatus(),
    onToggleTouchBoost: (Boolean) -> Unit = {}
) {
    if (selectedSubpage != null) {
        when (selectedSubpage) {
            HudBoostSubpage.Touch -> HudTouchPage(
                touchStatus = touchStatus,
                busy = action.busy,
                onToggleTouchBoost = onToggleTouchBoost
            )
            HudBoostSubpage.Network -> HudNetworkPage(
                onProbeNetwork = onProbeNetwork
            )
            HudBoostSubpage.Bypass -> HudBypassPage(
                snapshot = snapshot,
                bypassStatus = bypassStatus,
                busy = action.busy,
                onToggleBypass = onToggleBypass,
                onRefreshBypass = onRefreshBypass
            )
        }
    } else {
        val ram = snapshot[HudMetric.RAM_USED]
        val isCleaningRam = action.actionType == "ram" && action.busy

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Daftar Akselerasi Performa Gaming (Full-Width Sleek Cards, Bebas Truncation)
            val boostCards = listOf(
                BoostCardItem(
                    subpage = HudBoostSubpage.Touch,
                    icon = Icons.Default.TouchApp,
                    title = hudString(R.string.hud_lab_touch_title),
                    subtitle = "Akselerasi sampling sentuhan layar",
                    badge = if (touchStatus.enabled) "Aktif" else "Siap",
                    accentColor = Color(0xFF38BDF8)
                ),
                BoostCardItem(
                    subpage = HudBoostSubpage.Network,
                    icon = Icons.Default.NetworkCheck,
                    title = hudString(R.string.hud_lab_network_title),
                    subtitle = "Wi-Fi low latency & prioritas paket",
                    badge = "Low Latency",
                    accentColor = Color(0xFFFB923C)
                ),
                BoostCardItem(
                    subpage = HudBoostSubpage.Bypass,
                    icon = Icons.Default.BatteryChargingFull,
                    title = hudString(R.string.hud_lab_bypass_title),
                    subtitle = "Pasokan daya langsung tanpa panas baterai",
                    badge = if (bypassStatus.enabled || bypassStatus.active) "Aktif" else "Siap",
                    accentColor = Color(0xFF00E676)
                )
            )

            boostCards.forEach { card ->
                BoostEngineCard(
                    card = card,
                    onClick = { onSelectSubpage(card.subpage) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(2.dp))

            // 2. Booster Memori & Cache Kernel
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
                                text = "Penggunaan RAM Fisik",
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
        }
    }
}

@Composable
private fun BoostEngineCard(
    card: BoostCardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = HudCard,
        border = BorderStroke(1.dp, HudBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = card.accentColor.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, card.accentColor.copy(alpha = 0.45f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        tint = card.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = card.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = card.subtitle,
                    fontSize = 10.5.sp,
                    color = HudMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = card.accentColor.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, card.accentColor.copy(alpha = 0.40f))
            ) {
                Text(
                    text = card.badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = card.accentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Buka",
                tint = HudMuted.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
