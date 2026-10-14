package com.noir.lynx.feature.cpu.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

@Composable
fun CpuSetsTaskShieldCard(
    cpuSets: CpuSetsInfo,
    clusters: List<CpuClusterInfo> = emptyList(),
    onApplyPreset: (String) -> Unit,
    onToggleCore: (group: String, coreId: Int) -> Unit,
    onApplyOnBootChange: (Boolean) -> Unit,
    onResetToOem: (() -> Unit)? = null,
    isModified: Boolean = false,
    onOpenMatrixSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = "CPU Sets & Task Shield",
        icon = Icons.Default.Shield,
        accentColor = AccentCyan,
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isModified && onResetToOem != null) {
                    ResetHeaderButton(onClick = onResetToOem)
                }
                if (onOpenMatrixSheet != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenMatrixSheet() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Matriks Core",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = AccentCyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) {
        if (!cpuSets.isSupported) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kontrol cpuset kernel (/dev/cpuset) tidak tersedia atau tidak diaktifkan pada sistem ini.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
            return@LynxCard
        }

        val totalCores = cpuSets.totalCoresCount.coerceIn(4, 16)
        val isBigCore = { coreId: Int ->
            clusters.any { (it.id > 0 || it.role.contains("Big", ignoreCase = true) || it.role.contains("Prime", ignoreCase = true) || it.role.contains("Performance", ignoreCase = true)) && it.containsCore(coreId) }
                || (clusters.isEmpty() && coreId >= 6)
        }

        val activePresetKey = cpuSets.activePreset.lowercase()
        val isCustomMode = activePresetKey !in listOf("gaming", "battery", "standard")

        // 1. Subhead: Description + Optional Custom Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Isolasi thread kernel & prioritas inti",
                fontSize = 11.5.sp,
                color = TextSecondary
            )
            if (isCustomMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AccentPurple, CircleShape)
                    )
                    Text(
                        text = "Kustom",
                        color = AccentPurple,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. Streamlined 3-Way Segmented Preset Selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROFIL ISOLASI CORE",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "Preset Cepat 1-Klik",
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                LynxPresetOption("gaming", "Game Shield", Icons.Default.SportsEsports, AccentCyan),
                LynxPresetOption("standard", "Bawaan AOSP", Icons.Default.Tune, AccentBlue),
                LynxPresetOption("battery", "Hemat Daya", Icons.Default.BatteryChargingFull, AccentGreen)
            )
            presets.forEach { opt ->
                val isSel = activePresetKey == opt.key
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onApplyPreset(opt.key) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) opt.color.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.6f),
                    border = BorderStroke(if (isSel) 1.4.dp else 0.8.dp, if (isSel) opt.color else BorderSubtle)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = opt.icon,
                                contentDescription = null,
                                tint = if (isSel) opt.color else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = opt.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) opt.color else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Unified SoC Hardware Allocation Strip (Read-Only Telemetry Monitor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgElevated.copy(alpha = 0.35f))
                .border(BorderStroke(0.8.dp, BorderSubtle), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DIAGRAM ALOKASI INTI",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextSecondary
                )
                Text(
                    text = "$totalCores Cores",
                    fontSize = 10.sp,
                    color = TextTertiary
                )
            }

            // 1-Row Continuous Silicon Strip (Not isolated buttons)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = BgCard.copy(alpha = 0.85f),
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (coreId in 0 until totalCores) {
                        val isBig = isBigCore(coreId)
                        val inTopApp = cpuSets.isCoreInGroup("top-app", coreId)
                        val inBg = cpuSets.isCoreInGroup("background", coreId)
                        val isGameIsolated = isBig && inTopApp && !inBg

                        val cellBg = when {
                            isGameIsolated -> AccentCyan.copy(alpha = 0.22f)
                            !inTopApp && !inBg -> Color(0xFF141822)
                            isBig -> AccentOrange.copy(alpha = 0.16f)
                            else -> AccentBlue.copy(alpha = 0.14f)
                        }
                        val textColor = when {
                            isGameIsolated -> AccentCyan
                            !inTopApp && !inBg -> TextTertiary
                            isBig -> AccentOrange
                            else -> AccentBlue
                        }
                        val roleLabel = when {
                            isGameIsolated -> "Game"
                            isBig -> "Big"
                            else -> "Lit"
                        }

                        if (coreId > 0) {
                            Box(
                                modifier = Modifier
                                    .width(0.8.dp)
                                    .fillMaxHeight()
                                    .background(BorderSubtle.copy(alpha = 0.7f))
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(cellBg)
                                .padding(vertical = 6.dp, horizontal = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "C$coreId",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGameIsolated) Color.White else textColor
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = roleLabel,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(3.dp))
                            // Status LED Dot
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(if (!inTopApp && !inBg) TextTertiary.copy(alpha = 0.4f) else textColor)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dynamic One-Liner Status Note
            val statusNote = when (activePresetKey) {
                "gaming" -> "Big Core diprioritaskan 100% untuk Game & Top-App. Background diisolasi di Little Core."
                "battery" -> "Beban aplikasi ditahan pada Little Core efisien untuk memaksimalkan daya tahan baterai."
                "standard" -> "Seluruh 8 inti dialokasikan secara dinamis oleh penjadwal kernel."
                else -> "Konfigurasi kustom aktif. Penugasan thread berjalan sesuai matriks manual."
            }
            Text(
                text = statusNote,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = TextSecondary
            )
        }

    }
}
