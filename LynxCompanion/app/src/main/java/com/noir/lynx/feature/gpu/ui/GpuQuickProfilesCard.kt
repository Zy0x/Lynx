package com.noir.lynx.feature.gpu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.theme.*

/**
 * GpuQuickProfilesCard — 2x2 Bento Matrix for Instant 1-Tap GPU Profiles.
 * Follows the gold-standard calm design language of TuningProfilesCategory.
 * Anti AI slop, essential language only, zero redundant clutter.
 */
@Composable
fun GpuQuickProfilesCard(
    activeProfile: String,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = listOf(
        GpuProfilePresetItem(
            id = "battery",
            title = "Hemat Daya",
            subtitle = "Batas clock 60% • Irit baterai",
            icon = Icons.Default.BatteryChargingFull,
            accent = AccentGreen
        ),
        GpuProfilePresetItem(
            id = "balanced",
            title = "Seimbang",
            subtitle = "Responsif & sejuk untuk harian",
            icon = Icons.Default.Tune,
            accent = AccentCyan
        ),
        GpuProfilePresetItem(
            id = "esports",
            title = "Esports",
            subtitle = "Latensi rendah • Frame stabil",
            icon = Icons.Default.SportsEsports,
            accent = AccentOrange
        ),
        GpuProfilePresetItem(
            id = "extreme",
            title = "Ekstrem",
            subtitle = "Frekuensi maksimal tanpa batas",
            icon = Icons.Default.LocalFireDepartment,
            accent = AccentRed
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // 2x2 Bento Matrix: Row 1 (Battery & Balanced)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GpuBentoProfileCard(
                item = profiles[0],
                isSelected = activeProfile == "battery",
                onClick = { onSelectProfile("battery") },
                modifier = Modifier.weight(1f)
            )
            GpuBentoProfileCard(
                item = profiles[1],
                isSelected = activeProfile == "balanced",
                onClick = { onSelectProfile("balanced") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // 2x2 Bento Matrix: Row 2 (Esports & Extreme)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GpuBentoProfileCard(
                item = profiles[2],
                isSelected = activeProfile == "esports",
                onClick = { onSelectProfile("esports") },
                modifier = Modifier.weight(1f)
            )
            GpuBentoProfileCard(
                item = profiles[3],
                isSelected = activeProfile == "extreme",
                onClick = { onSelectProfile("extreme") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class GpuProfilePresetItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color
)

@Composable
private fun GpuBentoProfileCard(
    item: GpuProfilePresetItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cardBg by animateColorAsState(
        targetValue = if (isSelected) item.accent.copy(alpha = 0.12f) else BgElevated,
        animationSpec = tween(durationMillis = 180),
        label = "gpu_bento_bg_${item.id}"
    )
    val cardBorder by animateColorAsState(
        targetValue = if (isSelected) item.accent.copy(alpha = 0.7f) else BorderSubtle,
        animationSpec = tween(durationMillis = 180),
        label = "gpu_bento_border_${item.id}"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(if (isSelected) 1.dp else 0.8.dp, cardBorder),
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (!isSelected) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) item.accent.copy(alpha = 0.20f) else Color(0x10FFFFFF),
                border = BorderStroke(0.5.dp, if (isSelected) item.accent.copy(alpha = 0.3f) else Color(0x15FFFFFF)),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (isSelected) item.accent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) item.accent else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = item.accent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "AKTIF",
                                color = item.accent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = item.subtitle,
                    fontSize = 10.sp,
                    color = if (isSelected) item.accent.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
