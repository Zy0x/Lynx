package com.noir.lynx.feature.hud.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.hud.data.HudPreferences
import com.noir.lynx.feature.hud.data.HudPreferencesStore
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setHudMode
import com.noir.lynx.ui.setHudStyle

// ============================================================
// 1. HERO MASTER BENTO CARD (Always Top of Lynx Engine)
// ============================================================
@Composable
fun EngineHeroMasterCard(
    isActive: Boolean,
    hudMode: Int,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Pulsing alpha for active dot
    val infiniteTransition = rememberInfiniteTransition(label = "engine_dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "engine_dot_alpha"
    )

    val cardShape = RoundedCornerShape(20.dp)
    val modeName = if (hudMode == 0) "Bilah Samping (Edge Drawer)" else "Jendela Klasik (Floating Window)"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = BgCard,
        border = BorderStroke(1.dp, if (isActive) AccentCyan.copy(alpha = 0.6f) else BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Modern Icon Container
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isActive) AccentCyan.copy(alpha = 0.15f) else BgElevated)
                            .border(BorderStroke(0.8.dp, if (isActive) AccentCyan.copy(alpha = 0.4f) else BorderSubtle), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Lynx Engine",
                            tint = if (isActive) AccentCyan else TextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "LYNX OVERLAY ENGINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) AccentCyan else TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Liveness status dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) AccentGreen.copy(alpha = dotAlpha)
                                        else Color(0xFF6B7280)
                                    )
                            )
                            Text(
                                text = if (isActive) "AKTIF • SIAP DI TEPI LAYAR" else "SIAGA • NONAKTIF",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) TextPrimary else TextSecondary
                            )
                        }
                    }
                }

                // 50dp Tactile Switch
                Switch(
                    checked = isActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentCyan,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = BgElevated,
                        uncheckedThumbColor = Color(0xFF9E9E9E)
                    ),
                    modifier = Modifier.height(48.dp)
                )
            }

            // Mode Details Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Mode Operasi Aktif",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = modeName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) AccentCyan else TextPrimary
                    )
                }
            }

            // Overlay Permission Warning Banner
            if (!hasOverlayPermission) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRed.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Peringatan Izin",
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izin Tampilan di Atas Aplikasi Belum Aktif",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                            Text(
                                text = "Ketuk untuk memberikan izin agar overlay dapat tampil.",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 2. TAB 0: GAYA & MODE TAMPILAN
// ============================================================
@Composable
fun EngineAppearanceCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section A: Mode Tampilan Utama (Sidebar vs Floating)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "BENTUK ANTARMUKA UTAMA",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    letterSpacing = 0.6.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Bilah Samping (Edge Drawer)
                    ModeSelectTile(
                        title = "Bilah Samping",
                        subtitle = "Dock tersembunyi di sisi layar",
                        icon = Icons.Default.ViewSidebar,
                        isSelected = uiState.hudMode == 0,
                        onClick = { viewModel.setHudMode(context, 0) },
                        modifier = Modifier.weight(1f)
                    )

                    // Jendela Klasik (Floating Window)
                    ModeSelectTile(
                        title = "Jendela Klasik",
                        subtitle = "Overlay melayang bebas",
                        icon = Icons.Default.PictureInPicture,
                        isSelected = uiState.hudMode == 1,
                        onClick = { viewModel.setHudMode(context, 1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = if (uiState.hudMode == 0)
                        "Mode Bilah Samping: Menyediakan strip sentuh di tepi layar yang dapat di-swipe untuk membuka dock telemetri & aplikasi favorit tanpa menutupi pandangan game."
                    else
                        "Mode Jendela Klasik: Menampilkan kotak telemetri ringkas melayang di atas game dengan ukuran dan posisi yang dapat digeser bebas.",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // Section B: 6 Gaya Visual (Khusus Mode Jendela Klasik)
        if (uiState.hudMode == 1) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TEMA & GAYA VISUAL OVERLAY",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.6.sp
                    )

                    val styleList = listOf(
                        Triple(1, "RTSS Pillar", "Vertikal ala RivaTuner"),
                        Triple(2, "Top Ribbon", "Pita horizontal atas layar"),
                        Triple(3, "Dual Esport", "Dua kolom kompak pro gaming"),
                        Triple(4, "Quad Tiles", "Empat blok grid simetris"),
                        Triple(5, "Steam Deck", "Gaya HUD konsol genggam"),
                        Triple(6, "Ghost Text", "Teks transparan ultra-minimalis")
                    )

                    for (i in styleList.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val first = styleList[i]
                            StyleSelectTile(
                                title = first.second,
                                subtitle = first.third,
                                isSelected = uiState.hudStyle == first.first,
                                onClick = { viewModel.setHudStyle(context, first.first) },
                                modifier = Modifier.weight(1f)
                            )
                            if (i + 1 < styleList.size) {
                                val second = styleList[i + 1]
                                StyleSelectTile(
                                    title = second.second,
                                    subtitle = second.third,
                                    isSelected = uiState.hudStyle == second.first,
                                    onClick = { viewModel.setHudStyle(context, second.first) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelectTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) AccentCyan.copy(alpha = 0.12f) else BgElevated,
        border = BorderStroke(
            1.dp,
            if (isSelected) AccentCyan else BorderSubtle
        ),
        modifier = modifier.heightIn(min = 82.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) AccentCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Dipilih",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) TextPrimary else TextSecondary
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = TextSecondary.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StyleSelectTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AccentCyan.copy(alpha = 0.12f) else BgElevated,
        border = BorderStroke(
            0.8.dp,
            if (isSelected) AccentCyan else BorderSubtle
        ),
        modifier = modifier.heightIn(min = 60.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) TextPrimary else TextSecondary
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = TextSecondary.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ============================================================
// 3. TAB 1: METRIK TELEMETRI
// ============================================================
@Composable
fun EngineMetricsCard(
    prefs: HudPreferences,
    store: HudPreferencesStore,
    modifier: Modifier = Modifier
) {
    HudTheme {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudMetricSelection(prefs = prefs, store = store)
        }
    }
}

// ============================================================
// 4. TAB 2: JENDELA APUNG & PENGATURAN APLIKASI
// ============================================================
@Composable
fun EngineFloatingCard(
    prefs: HudPreferences,
    store: HudPreferencesStore,
    modifier: Modifier = Modifier
) {
    HudTheme {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudFloatingSettings(prefs = prefs, store = store)
        }
    }
}

// ============================================================
// 5. TAB 3: PEREKAM LAYAR MODERN
// ============================================================
@Composable
fun EngineRecorderCard(
    context: Context,
    modifier: Modifier = Modifier
) {
    HudTheme {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudRecorderMasterCard(context = context)
        }
    }
}
