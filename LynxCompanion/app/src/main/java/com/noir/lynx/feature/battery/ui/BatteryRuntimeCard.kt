package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.BatteryInfoStats
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.BgCard
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderGlass
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary

/**
 * BatteryRuntimeCard — Comprehensive cycle runtime, SOT proportion, and drain rates with i18n support.
 */
@Composable
fun BatteryRuntimeCard(uiState: com.noir.lynx.data.LynxUiState) {
    val stats = uiState.batteryInfoStats
    val currentLevel = stats.currentLevel.takeIf { it > 0 } ?: (uiState.batteryDetails?.level ?: 0)
    BatteryRuntimeCard(stats = stats, currentLevel = currentLevel)
}

@Composable
fun BatteryRuntimeCard(
    stats: BatteryInfoStats,
    currentLevel: Int
) {
    val startLevel = stats.unplugStartLevel.takeIf { it > 0 } ?: currentLevel
    LynxCard(
        title = stringResource(R.string.battery_runtime_card_title),
        icon = Icons.Default.Timer,
        accentColor = AccentGreen
    ) {
        // Hero Banner: Start% -> Current% & Combined Total Time
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (stats.isCharging) AccentCyan else AccentGreen)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (stats.isCharging) {
                                stringResource(R.string.battery_charging_from_source, stats.powerSourceText)
                            } else {
                                stringResource(R.string.battery_unplug_cycle_range)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (startLevel > currentLevel) {
                            "${startLevel}% ➔ ${currentLevel}% (-${stats.dischargedPercent}%)"
                        } else {
                            stringResource(R.string.battery_current_level_display, currentLevel)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.battery_unplug_details, stats.startClockTimeText, stats.dischargedMah),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.battery_total_time),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stats.timeSinceUnpluggedText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentGreen
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3-Segment Combined Proportion Bar (SOT + Deep Sleep + Screen-Off Awake = 100%)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Proporsi Gabungan Waktu Berjalan",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "100% Akurat Kernel",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        color = AccentCyan
                    )
                }

                Spacer(Modifier.height(8.dp))

                val sotWeight = stats.screenOnRatioPercent.coerceAtLeast(0f)
                val sleepWeight = stats.deepSleepRatioPercent.coerceAtLeast(0f)
                val awakeWeight = stats.screenOffAwakeRatioPercent.coerceAtLeast(0f)
                val totalWeight = (sotWeight + sleepWeight + awakeWeight).takeIf { it > 0f } ?: 1f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BorderSubtle)
                ) {
                    if (sotWeight > 0f) {
                        Box(
                            modifier = Modifier
                                .weight((sotWeight / totalWeight).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(AccentGreen)
                        )
                    }
                    if (sleepWeight > 0f) {
                        Box(
                            modifier = Modifier
                                .weight((sleepWeight / totalWeight).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(AccentCyan)
                        )
                    }
                    if (awakeWeight > 0f) {
                        Box(
                            modifier = Modifier
                                .weight((awakeWeight / totalWeight).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(AccentOrange)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Three breakdown columns: screen on, deep sleep, and awake idle.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Screen on (SOT).
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated.copy(alpha = 0.55f),
                        border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentGreen))
                                Spacer(Modifier.width(5.dp))
                                Text("Layar Aktif", fontSize = 9.5.sp, color = TextSecondary, maxLines = 1)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stats.screenOnTimeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = AccentGreen
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", stats.screenOnRatioPercent)}% • -${stats.screenOnDischargedPercent}% (${stats.screenOnDischargedMah}mAh)",
                                fontSize = 9.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. Tidur Lelap (Deep Sleep)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated.copy(alpha = 0.55f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentCyan))
                                Spacer(Modifier.width(5.dp))
                                Text("Deep Sleep", fontSize = 9.5.sp, color = TextSecondary, maxLines = 1)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stats.deepSleepTimeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = AccentCyan
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", stats.deepSleepRatioPercent)}% (${stats.deepSleepPercentage}% idle)",
                                fontSize = 9.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 3. Siaga Bangun (Screen-Off Awake / Wakelock)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated.copy(alpha = 0.55f),
                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentOrange))
                                Spacer(Modifier.width(5.dp))
                                Text("Siaga Bangun", fontSize = 9.5.sp, color = TextSecondary, maxLines = 1)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stats.screenOffAwakeTimeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = AccentOrange
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", stats.screenOffAwakeRatioPercent)}% • -${stats.screenOffDischargedPercent}% (${stats.screenOffDischargedMah}mAh)",
                                fontSize = 9.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Real Drain Rate & Remaining Time Summary Table
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated.copy(alpha = 0.5f),
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Waktu Layar Mati (Deep Sleep + Bangun)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(stats.screenOffTimeText, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Laju Konsumsi Saat Layar Aktif", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(stats.activeDrainRatePerHour, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_idle_drain_rate), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(stats.idleDrainRatePerHour, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_estimated_screen_remaining), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(stats.estimatedScreenRemainingText, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_estimated_standby_remaining), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(stats.estimatedStandbyRemainingText, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                }
            }
        }
    }
}
