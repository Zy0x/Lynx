package com.noir.lynx.feature.battery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.BatteryInfoStats
import com.noir.lynx.designsystem.theme.*

@Composable
internal fun BatteryHistoryTabSelector(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    // 2-Segment Switcher: Minimalis & Touch-Friendly
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(10.dp),
            color = BgElevated.copy(alpha = 0.5f),
            border = BorderStroke(0.8.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Segment 0: Grafik Waktu
                Surface(
                    onClick = { onSelectTab(0) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedTab == 0) AccentCyan.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (selectedTab == 0) BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = if (selectedTab == 0) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.battery_graph_time_tab),
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) TextPrimary else TextSecondary
                        )
                    }
                }

                // Segment 1: Penggunaan Aplikasi
                Surface(
                    onClick = { onSelectTab(1) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedTab == 1) AccentCyan.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (selectedTab == 1) BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = if (selectedTab == 1) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.battery_app_usage_tab),
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }
}
@Composable
internal fun BatteryHistoryTimePanel(
    historySeries: BatteryHistorySeries,
    currentLevel: Int,
    scrubbedIndex: Int?,
    onScrubbedIndexChange: (Int?) -> Unit,
    isStepsExpanded: Boolean,
    onToggleStepsExpanded: (Boolean) -> Unit
) {
    val points = historySeries.points
    val steps = historySeries.steps

    if (points.size >= 2) {
                val activePoint = scrubbedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, if (scrubbedIndex != null) AccentCyan.copy(alpha = 0.8f) else BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (activePoint?.isScreenOn == true) AccentGreen else AccentCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (scrubbedIndex != null) stringResource(R.string.battery_scrubbed_step_detail) else stringResource(R.string.battery_last_recorded_step),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.5.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = activePoint?.stepDurationText?.ifBlank { "${activePoint.level}%" } ?: "$currentLevel%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (activePoint?.isScreenOn == true) AccentGreen.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, if (activePoint?.isScreenOn == true) AccentGreen else AccentCyan)
                        ) {
                            Text(
                                text = if (activePoint?.isScreenOn == true) stringResource(R.string.battery_screen_on_tag) else stringResource(R.string.battery_screen_off_tag),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activePoint?.isScreenOn == true) AccentGreen else AccentCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                DischargeHistoryGraph(
                    series = historySeries,
                    scrubbedIndex = scrubbedIndex,
                    onScrubbedIndexChange = onScrubbedIndexChange
                )

                Spacer(Modifier.height(10.dp))
            }

            if (steps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_no_discharge_recorded),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Surface(
                    onClick = { onToggleStepsExpanded(!isStepsExpanded) },
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated.copy(alpha = 0.5f),
                    border = BorderStroke(0.8.dp, if (isStepsExpanded) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isStepsExpanded) AccentCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.battery_log_duration_per_drop),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isStepsExpanded) TextPrimary else TextSecondary,
                                fontSize = 11.5.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "(${steps.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }

                        Icon(
                            imageVector = if (isStepsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isStepsExpanded) "Tutup" else "Buka",
                            tint = if (isStepsExpanded) AccentCyan else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isStepsExpanded,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        steps.take(15).forEach { step ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgCard,
                                border = BorderStroke(0.7.dp, BorderGlass),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${step.fromLevel}% ➔ ${step.toLevel}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = if (step.isScreenOn) AccentGreen.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, if (step.isScreenOn) AccentGreen.copy(alpha = 0.6f) else AccentCyan.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = if (step.isScreenOn) stringResource(R.string.battery_screen_on_tag) else if (step.isDeviceIdle) stringResource(R.string.battery_deep_doze_tag) else stringResource(R.string.battery_screen_off_tag),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (step.isScreenOn) AccentGreen else AccentCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = step.durationText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (step.isScreenOn) AccentGreen else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
}
}

@Composable
internal fun BatteryHistoryAppPanel(
    stats: BatteryInfoStats,
    isAppsExpanded: Boolean,
    onToggleAppsExpanded: (Boolean) -> Unit
) {
    // Segment 1: Penggunaan Aplikasi & Sistem (Minimalis & Elegan)
            val apps = stats.topDrainApps
            if (apps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_no_app_drain_recorded),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                val maxDrainPct = apps.firstOrNull()?.drainPercent ?: 100f
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    apps.take(5).forEach { item ->
                        AppDrainRow(item, maxDrainPct)
                    }

                    if (apps.size > 5) {
                        AnimatedVisibility(
                            visible = isAppsExpanded,
                            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                apps.drop(5).forEach { item ->
                                    AppDrainRow(item, maxDrainPct)
                                }
                            }
                        }

                        Surface(
                            onClick = { onToggleAppsExpanded(!isAppsExpanded) },
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated.copy(alpha = 0.45f),
                            border = BorderStroke(0.8.dp, if (isAppsExpanded) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isAppsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isAppsExpanded) stringResource(R.string.battery_show_less) else stringResource(R.string.battery_show_more_apps, apps.size - 5),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentCyan,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
}
