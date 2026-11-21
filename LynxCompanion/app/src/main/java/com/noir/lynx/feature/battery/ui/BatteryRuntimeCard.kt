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
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BgSurfaceLowest
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
    val isSingleCleanCycle = !stats.isCharging &&
        startLevel > currentLevel &&
        stats.dischargedPercent in 1..(startLevel - currentLevel + 2)

    LynxCard(
        title = stringResource(R.string.battery_runtime_card_title),
        subtitle = "Proporsi SOT, deep sleep & estimasi sisa pemakaian",
        icon = Icons.Default.Timer,
        accentColor = AccentGreen
    ) {
        // Hero Banner: Context-Aware Cycle Progress & Active Runtime
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, (if (stats.isCharging) AccentCyan else AccentGreen).copy(alpha = 0.40f)),
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
                            text = when {
                                stats.isBypassMode -> "Bypass Daya Aktif (${stats.powerSourceText})"
                                stats.isCharging -> stringResource(R.string.battery_charging_from_source, stats.powerSourceText)
                                isSingleCleanCycle -> stringResource(R.string.battery_unplug_cycle_range)
                                else -> "Akumulasi Siklus Sejak Reset Penuh"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            isSingleCleanCycle -> "${startLevel}% ➔ ${currentLevel}% (-${stats.dischargedPercent}%)"
                            stats.dischargedPercent > 0 -> "Baterai ${currentLevel}% • Terkuras ${stats.dischargedPercent}%"
                            else -> stringResource(R.string.battery_current_level_display, currentLevel)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            stats.isCharging && stats.timeToFullEstimatedText != "-" ->
                                "Estimasi penuh: ${stats.timeToFullEstimatedText} • Terkuras siklus: ${stats.dischargedMah} mAh"
                            stats.startClockTimeText != "-" ->
                                "Awal siklus: ${stats.startClockTimeText} • Terkuras: ${stats.dischargedMah} mAh"
                            else ->
                                stringResource(R.string.battery_unplug_details, stats.startClockTimeText, stats.dischargedMah)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.50f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Waktu Baterai",
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
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.6.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Proporsi Waktu Siklus Berjalan",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (stats.deepSleepPercentage > 0) "Efisiensi Idle ${stats.deepSleepPercentage}%" else "Siklus Aktif",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
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
                        .background(BgSurfaceLowest)
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
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentGreen))
                                Spacer(Modifier.width(5.dp))
                                Text("Layar Aktif", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stats.screenOnTimeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AccentGreen
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", stats.screenOnRatioPercent)}% • -${stats.screenOnDischargedPercent}% (${stats.screenOnDischargedMah}mAh)",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. Tidur Lelap (Deep Sleep)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentCyan))
                                Spacer(Modifier.width(5.dp))
                                Text("Deep Sleep", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stats.deepSleepTimeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AccentCyan
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", stats.deepSleepRatioPercent)}% (${stats.deepSleepPercentage}% idle)",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 3. Siaga Bangun (Screen-Off Awake / Wakelock)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AccentOrange))
                                Spacer(Modifier.width(5.dp))
                                Text("Siaga Bangun", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stats.screenOffAwakeTimeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AccentOrange
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", stats.screenOffAwakeRatioPercent)}% • -${stats.screenOffDischargedPercent}% (${stats.screenOffDischargedMah}mAh)",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 2x2 Bento Grid: Real Drain Rates & Remaining Runtime Estimates
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text("Laju Drain Layar Aktif", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stats.activeDrainRatePerHour,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(stringResource(R.string.battery_idle_drain_rate), fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stats.idleDrainRatePerHour,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(stringResource(R.string.battery_estimated_screen_remaining), fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stats.estimatedScreenRemainingText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentOrange,
                        maxLines = 1
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(stringResource(R.string.battery_estimated_standby_remaining), fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stats.estimatedStandbyRemainingText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentBlue,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Screen-Off Total Summary Strip
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.6.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Waktu Layar Mati (Deep Sleep + Siaga)", fontSize = 10.5.sp, color = TextSecondary)
                Text(stats.screenOffTimeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}
