package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

internal enum class HudBoostSubpage(val titleRes: Int) {
    GameShift(R.string.hud_gameshift_title),
    Touch(R.string.hud_lab_touch_title),
    Network(R.string.hud_lab_network_title),
    Bypass(R.string.hud_lab_bypass_title)
}

private data class BoostCardItem(
    val subpage: HudBoostSubpage,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
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
            HudBoostSubpage.GameShift -> HudGameShiftControls()
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
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            text = "Akselerasi Kernel & Hardware Gaming",
                            fontSize = 11.sp,
                            color = HudMuted
                        )
                    }
                    val activeNorm = normalizeProfile(snapshot.profile)
                    val activeColor = hudProfileColor(activeNorm)
                    Surface(
                        color = activeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            activeColor.copy(alpha = 0.45f)
                        )
                    ) {
                        Text(
                            text = hudString(hudProfileLabel(activeNorm)),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = activeColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 2. Akselerasi Performa Gaming (Section Header)
            Text(
                text = "AKSELERASI PERFORMA GAMING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HudMuted,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            )

            val boostCards = listOf(
                BoostCardItem(
                    subpage = HudBoostSubpage.GameShift,
                    icon = Icons.Default.Bolt,
                    title = hudString(R.string.hud_gameshift_title),
                    subtitle = "Reaktif Burst Clock",
                    accentColor = Color(0xFF00E5FF)
                ),
                BoostCardItem(
                    subpage = HudBoostSubpage.Touch,
                    icon = Icons.Default.TouchApp,
                    title = hudString(R.string.hud_lab_touch_title),
                    subtitle = if (touchStatus.enabled) "TouchBoost Aktif" else "Sampling 240/480Hz",
                    accentColor = Color(0xFF38BDF8)
                ),
                BoostCardItem(
                    subpage = HudBoostSubpage.Network,
                    icon = Icons.Default.NetworkCheck,
                    title = hudString(R.string.hud_lab_network_title),
                    subtitle = "Low-Latency Wi-Fi",
                    accentColor = Color(0xFFFB923C)
                ),
                BoostCardItem(
                    subpage = HudBoostSubpage.Bypass,
                    icon = Icons.Default.BatteryChargingFull,
                    title = hudString(R.string.hud_lab_bypass_title),
                    subtitle = if (bypassStatus.enabled || bypassStatus.active) "Direct Power Aktif" else "Anti-Overheat",
                    accentColor = Color(0xFF00E676)
                )
            )

            // Render 2x2 Grid of Clean Cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                boostCards.chunked(2).forEach { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCards.forEach { card ->
                            BoostEngineCard(
                                card = card,
                                onClick = { onSelectSubpage(card.subpage) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Booster Memori & Cache Kernel
            Text(
                text = "BOOSTER MEMORI & CACHE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HudMuted,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
            )
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
        modifier = modifier.heightIn(min = 68.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = card.accentColor.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, card.accentColor.copy(alpha = 0.45f)),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        tint = card.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = card.title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    fontSize = 10.sp,
                    color = card.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Buka",
                tint = HudMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
