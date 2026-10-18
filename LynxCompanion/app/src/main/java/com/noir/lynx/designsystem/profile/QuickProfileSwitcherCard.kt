package com.noir.lynx.designsystem.profile

import com.noir.lynx.core.locale.localizedUiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.profiles.KernelPerformanceProfile
import com.noir.lynx.profiles.KernelProfileCatalog

/**
 * QuickProfileSwitcherCard — Clean, Modern, Compact Mode Switcher for the Dashboard.
 *
 * Dedicated to 1-tap rapid mode switching without the clutter of creation, editing,
 * duplicating, or deletion buttons. Directs users to the Tuning Hub for full catalog customization.
 */
@Composable
fun QuickProfileSwitcherCard(
    catalog: KernelProfileCatalog,
    currentProfile: String,
    hasOverrides: Boolean,
    onProfileSelected: (String, Boolean) -> Unit,
    onExtremeConfirmRequired: () -> Unit,
    onNavigateToProfilesCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProfileObj = catalog.profiles.firstOrNull { it.id == currentProfile }
    val isCustomActive = activeProfileObj != null && !activeProfileObj.builtIn

    LynxCard(
        title = "Mode Performa Kernel",
        subtitle = if (hasOverrides) "⚡ Override aktif" else null,
        icon = Icons.Default.Speed,
        accentColor = AccentOrange,
        action = {
            Surface(
                onClick = onNavigateToProfilesCatalog,
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.45f)),
                modifier = Modifier.defaultMinSize(minHeight = 28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kustom",
                        color = AccentOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = localizedUiText("Buka Profil Kustom"),
                        tint = AccentOrange,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        // 4 Core Quick Profiles in a modern compact row
        val standardProfiles = listOf(
            QuickModeItem("powersave", "Hemat", "Efisiensi Daya", Color(0xFF00E676), Icons.Default.BatteryChargingFull),
            QuickModeItem("balance", "Seimbang", "Harian Seimbang", AccentBlue, Icons.Default.Tune),
            QuickModeItem("performance", "Performa", "Gaming Responsif", AccentOrange, Icons.Default.Speed),
            QuickModeItem("extreme", "Ekstrem", "Clock Maksimal", AccentRed, Icons.Default.LocalFireDepartment),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            standardProfiles.forEach { item ->
                val isSelected = currentProfile.equals(item.id, ignoreCase = true)
                val targetBg = if (isSelected) item.accentColor.copy(alpha = 0.18f) else BgElevated
                val targetBorder = if (isSelected) item.accentColor else BorderSubtle
                val animatedBg by animateColorAsState(targetBg, animationSpec = tween(150), label = "quick_card_bg")
                val animatedBorder by animateColorAsState(targetBorder, animationSpec = tween(150), label = "quick_card_border")

                Surface(
                    onClick = {
                        if (!isSelected) {
                            if (item.id == "extreme") {
                                onExtremeConfirmRequired()
                            } else {
                                onProfileSelected(item.id, true)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = animatedBg,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 0.8.dp,
                        color = animatedBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = localizedUiText(item.label),
                            tint = if (isSelected) item.accentColor else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = item.label,
                            color = if (isSelected) item.accentColor else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0"),
                            maxLines = 1
                        )

                        Text(
                            text = item.subtitle,
                            color = if (isSelected) item.accentColor.copy(alpha = 0.85f) else TextTertiary,
                            fontSize = 8.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Active Custom Profile Badge (if custom preset currently selected)
        if (isCustomActive && activeProfileObj != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Profil Kustom Aktif: ${activeProfileObj.name}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Basis: ${activeProfileObj.baseProfile.uppercase()} • ${activeProfileObj.description.ifBlank { "Kustom Pengguna" }}",
                                color = TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class QuickModeItem(
    val id: String,
    val label: String,
    val subtitle: String,
    val accentColor: Color,
    val icon: ImageVector
)
