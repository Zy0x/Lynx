package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.theme.*

/**
 * MemoryStorageTabRow — 3-segment Obsidian Pill TabRow matching BatterySubscreenTabRow, ThermalTabRow & GpuDisplayTabRow.
 * Segments Memory & Storage cleanly by domain:
 * 0: RAM & ZRAM (Hero + ZRAM + Quick VM & Swappiness)
 * 1: VM & Entropi (VM Summary + Progressive Writeback/KSM + LMK + Entropy + PSI)
 * 2: Storage & I/O (MountX Flash Shield + MicroSD + Block Device Schedulers & Read-Ahead)
 */
@Composable
fun MemoryStorageTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tabs = listOf(
        Triple(0, "RAM & ZRAM", Icons.Default.Memory),
        Triple(1, "VM & Entropi", Icons.Default.Tune),
        Triple(2, "Storage & I/O", Icons.Default.Storage)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF10141E),
        border = BorderStroke(0.6.dp, Color(0x1AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                val animatedBg by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF1E2638) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "mem_tab_bg_$index"
                )
                val animatedText by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextSecondary.copy(alpha = 0.65f),
                    animationSpec = tween(durationMillis = 180),
                    label = "mem_tab_text_$index"
                )
                val animatedIcon by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.50f),
                    animationSpec = tween(durationMillis = 180),
                    label = "mem_tab_icon_$index"
                )

                Surface(
                    onClick = {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(index)
                        }
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = animatedBg,
                    border = if (isSelected) {
                        BorderStroke(
                            0.6.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x35FFFFFF),
                                    Color(0x10FFFFFF)
                                )
                            )
                        )
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = animatedIcon,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = animatedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
