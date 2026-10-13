package com.noir.lynx.feature.cpu.ui
import com.noir.lynx.feature.cpu.ui.CompactClusterRow

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.noir.lynx.data.*
import com.noir.lynx.feature.cpu.ui.CpuSetsTaskShieldCard

import com.noir.lynx.ui.*

const val PERFORMA_LABEL = "Performa"

@Composable
fun CpuTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(0, PERFORMA_LABEL, Icons.Default.Bolt),
        Triple(1, "Sistem", Icons.Default.SettingsSuggest),
        Triple(2, "Monitor", Icons.Default.Analytics)
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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                val tabAccent = when (index) {
                    0 -> AccentCyan
                    1 -> AccentOrange
                    2 -> AccentGreen
                    else -> AccentCyan
                }
                Surface(
                    onClick = { onSelectTab(index) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) tabAccent.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, tabAccent.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) tabAccent else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  2. CPU DASHBOARD HERO CARD (LEVEL 1 ONE-GLANCE OVERVIEW)
// ============================================================

@Composable
fun CpuDashboardHeroCard(
    uiState: LynxUiState,
    modifier: Modifier = Modifier
) {
    val cores = uiState.cpuCores
    val totalLoad = uiState.totalCpuLoadPercent.coerceIn(0, 100)
    val peakKhz = cores.filter { it.isOnline }.maxOfOrNull { it.curFreqKhz } ?: 0L
    val peakGhz = peakKhz / 1000000f
    val tempC: Int = uiState.resolveCpuTempC()
    val currentProfile = uiState.cpuComprehensiveProfile.lowercase()

    val profileLabel = when (currentProfile) {
        "battery" -> "Baterai"
        "performance", "gaming" -> PERFORMA_LABEL
        "extreme" -> "Ekstrem"
        else -> "Seimbang"
    }
    val profileAccent = when (currentProfile) {
        "battery" -> AccentGreen
        "performance", "gaming" -> AccentOrange
        "extreme" -> AccentRed
        else -> AccentCyan
    }

    val sched = uiState.schedulerInfo
    val isGuardActive = sched.antiThrottlingGuardEnabled || sched.ppmDlptBypassEnabled || !sched.ppmThermalThrottlingEnabled
    val isThrottled = uiState.isCpuThermalThrottled
    val healthQuality = uiState.cpuHealthQuality
    val healthAccent = when {
        isThrottled -> AccentRed
        isGuardActive && healthQuality == CpuHealthQuality.WARM -> AccentCyan
        healthQuality == CpuHealthQuality.WARM -> AccentOrange
        else -> AccentGreen
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Status Mode & Chipset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(profileAccent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = profileLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = profileAccent
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text(
                        text = if (uiState.socPlatformName.isNotBlank()) uiState.socPlatformName else "ARMv8 SoC",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4-Column Primary Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Peak Speed
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PEAK CLOCK", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (peakGhz > 0f) String.format(Locale.US, "%.2f", peakGhz) else "--",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentCyan
                        )
                        Text("GHz", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }

                // Metric 2: CPU Temp
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SUHU CPU", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$tempC°",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (tempC >= 75) AccentRed else (if (tempC >= 60) AccentOrange else AccentGreen)
                        )
                        Text("Celsius", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }

                // Metric 3: Total Load
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("BEBAN CPU", fontSize = 8.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "$totalLoad%",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalLoad > 75) AccentOrange else TextPrimary
                        )
                        Text("Total", fontSize = 8.5.sp, color = TextSecondary)
                    }
                }

                // Metric 4: Throttle / Anti-Throttle Guard Status
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isGuardActive && !isThrottled) "GUARD" else "THROTTLE",
                            fontSize = 8.5.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = when {
                                isThrottled -> "Aktif"
                                isGuardActive -> "Bypass"
                                else -> "Aman"
                            },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isThrottled -> AccentRed
                                isGuardActive -> AccentCyan
                                else -> AccentGreen
                            }
                        )
                        Text(
                            text = when {
                                isThrottled -> "Dibatasi"
                                isGuardActive -> "Plafon 100%"
                                else -> "Normal"
                            },
                            fontSize = 8.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Qualitative Smart Health Status Bar (Synchronized with Anti-Throttling Guard)
            val statusTitle = when {
                isThrottled -> "Throttled"
                isGuardActive && tempC >= 65 -> "Unthrottled (Warm)"
                isGuardActive -> "Optimal (Unthrottled)"
                else -> healthQuality.label
            }
            val statusSubtitle = when {
                isThrottled -> "Frekuensi puncak dibatasi oleh governor termal"
                isGuardActive -> "Anti-Throttling aktif • Plafon clock puncak terjaga 100%"
                else -> healthQuality.subtitle
            }
            val statusBadge = when {
                isThrottled -> "$PERFORMA_LABEL Dibatasi"
                isGuardActive -> "Anti-Throttle Aktif"
                else -> "$PERFORMA_LABEL Siaga"
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(healthAccent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Status CPU:",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = statusTitle,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = healthAccent
                                )
                            }
                            Text(
                                text = statusSubtitle,
                                fontSize = 9.5.sp,
                                color = TextTertiary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = healthAccent.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, healthAccent.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = statusBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = healthAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  3. CPU SMART RECOMMENDATION BANNER (PHASE 7: SLIM STRIP)
// ============================================================

@Composable
fun CpuSmartRecommendationBanner(
    recommendation: String,
    onApplyRecommendation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AccentOrange.copy(alpha = 0.12f),
        border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = recommendation,
                    fontSize = 10.5.sp,
                    color = TextPrimary,
                    lineHeight = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onApplyRecommendation,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Terapkan",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
//  4. COMPACT CLUSTER ROW (PHASE 1 & 5: PROGRESSIVE DISCLOSURE)
// ============================================================

