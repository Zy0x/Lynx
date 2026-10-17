package com.noir.lynx.feature.network.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.core.locale.localizedUiText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.theme.*

/**
 * Sticky Category TabRow separating Wi-Fi / Network and Sound / Audio subsystems.
 * Provides touch-first 48dp+ tap targets and luxury OLED styling.
 */
@Composable
fun NetworkAudioTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(0, "Wi-Fi & Network", Icons.Default.Wifi),
        Triple(1, "Sound & Audio", Icons.AutoMirrored.Filled.VolumeUp)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                val tabAccent = if (index == 0) AccentBlue else AccentCyan

                Surface(
                    onClick = { onSelectTab(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) tabAccent.copy(alpha = 0.15f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, tabAccent.copy(alpha = 0.5f)) else null,
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
                            contentDescription = localizedUiText(title),
                            tint = if (isSelected) tabAccent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
