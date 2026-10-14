package com.noir.lynx.designsystem.tweak

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.AccentPurple
import com.noir.lynx.designsystem.theme.AccentRed
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BgSurfaceLowest
import com.noir.lynx.designsystem.theme.BorderGlass
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary

/**
 * LynxSchedulerPresetSheet — Clean Preset Selector Bottom Sheet with i18n support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LynxSchedulerPresetSheet(
    activePreset: String,
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header Tag
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = stringResource(R.string.scheduler_kernel),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text(
                        text = if (activePreset.equals("custom", true)) stringResource(R.string.scheduler_custom_mode_active) else stringResource(R.string.scheduler_4_tested_profiles),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (activePreset.equals("custom", true)) AccentPurple else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.scheduler_profile_response),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.scheduler_profile_response_desc),
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(14.dp))

            if (activePreset.equals("custom", true)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentPurple.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.scheduler_custom_alert_title),
                                color = AccentPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.scheduler_custom_alert_desc),
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.5.sp
                            )
                        }
                    }
                }
            }

            // 4 Preset Options
            listOf(
                Triple(
                    "extreme",
                    "Extreme (Unrestricted)",
                    Triple(
                        "Performa puncak tanpa kompromi: Latensi CFS ditekan ke 3 ms, migrasi instan 50 µs, uclamp 512, RT throttling dimatikan (-1), dan C-State sleep disabled.",
                        "Ramp-up: 0 µs • Latensi: 3 ms • RT Throttling: Off • Uclamp: 512",
                        AccentRed
                    )
                ),
                Triple(
                    "gaming",
                    "Responsif (Gaming Stabil)",
                    Triple(
                        "Clock CPU melompat instan tanpa jeda (0 µs ramp-up), latensi task 4 ms, preemption 0.75 ms, migrasi 200 µs, uclamp 128. Sangat stabil untuk gaming tanpa panas berlebih.",
                        "Ramp-up: 0 µs • Latensi: 4 ms • Uclamp: 128",
                        AccentOrange
                    )
                ),
                Triple(
                    "balanced",
                    "Seimbang (Rekomendasi Harian)",
                    Triple(
                        "Transisi frekuensi halus dan dinamis (1000 µs), latensi 10 ms. Sangat stabil, responsif untuk multitasking harian dengan efisiensi daya optimal.",
                        "Ramp-up: 1000 µs • Latensi: 10 ms • Uclamp: 0",
                        AccentBlue
                    )
                ),
                Triple(
                    "battery",
                    "Efisiensi Daya (Hemat Baterai)",
                    Triple(
                        "Mencegah lonjakan frekuensi singkat yang boros daya (4000 µs ramp-up), latensi santai 20 ms, migrasi 1000 µs, uclamp cap 640. Menghemat konsumsi baterai maksimal.",
                        "Ramp-up: 4000 µs • Latensi: 20 ms • Uclamp Cap: 640",
                        AccentGreen
                    )
                )
            ).forEach { (presetKey, title, details) ->
                val (desc, spec, color) = details
                val isSelected = activePreset.equals(presetKey, ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) color.copy(alpha = 0.12f) else BgElevated,
                    border = BorderStroke(1.dp, if (isSelected) color else BorderGlass),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable {
                            onSelectPreset(presetKey)
                            onDismiss()
                        }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) color else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = color.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, color)
                                ) {
                                    Box(Modifier.padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text(stringResource(R.string.status_badge_active), color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = desc,
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.5.sp
                        )

                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(0.8.dp, BorderSubtle)
                        ) {
                            Text(
                                text = spec,
                                color = if (isSelected) color else TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
