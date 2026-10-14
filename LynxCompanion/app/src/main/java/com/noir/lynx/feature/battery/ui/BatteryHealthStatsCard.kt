package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.BatteryHealthStats
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import java.util.Locale

@Composable
fun BatteryHealthStatsCard(
    batteryHealth: BatteryHealthStats,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LynxCard(
        title = stringResource(R.string.battery_health_card_title),
        icon = Icons.Default.BatteryChargingFull,
        accentColor = AccentCyan,
        modifier = modifier,
        action = {
            Surface(
                shape = CircleShape,
                color = AccentCyan.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRefresh() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = AccentCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_refresh),
                        color = AccentCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Health % Bento Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Health", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${batteryHealth.healthPct}%", color = AccentCyan, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = if (batteryHealth.healthPct >= 80) stringResource(R.string.battery_health_prime) else stringResource(R.string.battery_health_moderate),
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                }

                // Cycle Count Bento Tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.battery_charge_cycles), color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (batteryHealth.cycleCount >= 0) stringResource(R.string.battery_cycles_suffix, batteryHealth.cycleCount) else stringResource(R.string.battery_not_available),
                            color = AccentOrange,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(stringResource(R.string.battery_hardware_node_real), color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }

            // Deep Sleep & Drain Metrics Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.battery_deep_sleep_ratio), color = TextSecondary, fontSize = 11.5.sp)
                        Text(
                            text = "${batteryHealth.deepSleepPct.toInt()}%",
                            color = if (batteryHealth.deepSleepPct > 70f) AccentGreen else AccentOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { batteryHealth.deepSleepPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (batteryHealth.deepSleepPct > 70f) AccentGreen else AccentOrange,
                        trackColor = BgElevated
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.battery_capacity_stats, batteryHealth.actualCapacityMah, batteryHealth.designedCapacityMah),
                            color = TextTertiary,
                            fontSize = 10.5.sp
                        )
                        Text(
                            text = stringResource(R.string.battery_drain_rate_active, String.format(Locale.US, "%.1f", batteryHealth.activeDrainRateMh)),
                            color = TextTertiary,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}
