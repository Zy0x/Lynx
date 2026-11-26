package com.noir.lynx.feature.hud.ui

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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.hud.data.*

/**
 * Shared settings for Floating Windows (Jendela Apung) & Dock App Manager.
 * Built with ergonomic Bento cards, live aspect ratio preview, and 48dp+ touch targets.
 */
@Composable
internal fun HudFloatingSettings(
    prefs: HudPreferences,
    store: HudPreferencesStore,
    modifier: Modifier = Modifier
) {
    var selectedSubpage by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ============================================================
        // 1. HERO MASTER BENTO CARD: Jendela Mengambang (Floating Apps)
        // ============================================================
        FloatingMasterSwitchCard(
            isEnabled = prefs.floatingEnabled,
            onToggle = store::setFloatingEnabled
        )

        if (!prefs.floatingEnabled) {
            // Disabled state informational card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Fitur Jendela Apung Sedang Nonaktif",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Aktifkan sakelar di atas untuk membuka aplikasi pendukung (WhatsApp, YouTube, Kalkulator, Browser) dalam jendela mini melayang di atas game tanpa perlu keluar.",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
            return@Column
        }

        // ============================================================
        // 2. SUB-PAGE NAVIGATION ROW (Engine, Ukuran, Aplikasi, Panduan)
        // ============================================================
        FloatingSubtabRow(
            selectedIndex = selectedSubpage,
            selectedAppCount = prefs.dockPackages?.size ?: 0,
            onSelect = { selectedSubpage = it }
        )

        // ============================================================
        // 3. SUB-PAGE BODY CONTENT
        // ============================================================
        AnimatedContent(
            targetState = selectedSubpage,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
            },
            label = "floating_subpage_anim"
        ) { section ->
            when (section) {
                0 -> FloatingEngineSection(prefs = prefs, store = store)
                1 -> FloatingSizeSection(prefs = prefs, store = store)
                2 -> FloatingAppsSection(prefs = prefs, store = store)
                3 -> FloatingGuideSection()
            }
        }
    }
}

// ============================================================
// 1. MASTER SWITCH BENTO CARD
// ============================================================
@Composable
private fun FloatingMasterSwitchCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating_dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_dot_alpha"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isEnabled) AccentCyan.copy(alpha = 0.6f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isEnabled) AccentCyan.copy(alpha = 0.15f) else BgElevated)
                        .border(
                            BorderStroke(0.8.dp, if (isEnabled) AccentCyan.copy(alpha = 0.4f) else BorderSubtle),
                            RoundedCornerShape(13.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Floating Apps",
                        tint = if (isEnabled) AccentCyan else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "JENDELA APUNG (FLOATING APPS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) AccentCyan else TextSecondary,
                        letterSpacing = 0.6.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isEnabled) AccentCyan.copy(alpha = dotAlpha)
                                    else TextSecondary.copy(alpha = 0.4f)
                                )
                        )
                        Text(
                            text = if (isEnabled) "AKTIF • Siap Diluncurkan dari Dock" else "NONAKTIF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) TextPrimary else TextSecondary
                        )
                    }
                    Text(
                        text = "Buka aplikasi dock sebagai jendela mini tanpa menutup game",
                        fontSize = 10.5.sp,
                        color = TextSecondary.copy(alpha = 0.8f)
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = BgElevated,
                    uncheckedBorderColor = BorderSubtle
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}

// ============================================================
// 2. SUB-PAGE TABROW 48DP
// ============================================================
@Composable
private fun FloatingSubtabRow(
    selectedIndex: Int,
    selectedAppCount: Int,
    onSelect: (Int) -> Unit
) {
    val tabs = listOf(
        Triple(0, "Engine", Icons.Default.Memory),
        Triple(1, "Ukuran", Icons.Default.AspectRatio),
        Triple(2, if (selectedAppCount > 0) "Aplikasi ($selectedAppCount)" else "Aplikasi", Icons.Default.Apps),
        Triple(3, "Panduan", Icons.Default.Info)
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedIndex == index
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent,
                    border = BorderStroke(
                        0.8.dp,
                        if (isSelected) AccentCyan.copy(alpha = 0.5f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AccentCyan else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 3. SUBPAGE 0: ENGINE SELECTION
// ============================================================
@Composable
private fun FloatingEngineSection(
    prefs: HudPreferences,
    store: HudPreferencesStore
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "BACKEND ENGINE WINDOW",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = if (prefs.floatingEngine == "standalone") "AKTIF: LYNX VIRTUAL" else "AKTIF: FREEFORM ROM",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }

                // Option 1: Lynx Virtual Standalone (Root Engine)
                EngineSelectBentoCard(
                    title = "Lynx Virtual (Root Engine)",
                    badge = "⭐ REKOMENDASI LYNX",
                    desc = "Aplikasi berjalan lewat display virtual root mandiri. Sangat stabil untuk semua ROM (AOSP, MIUI, HyperOS, ColorOS) tanpa perlu modul eksternal.",
                    icon = Icons.Default.Terminal,
                    isSelected = prefs.floatingEngine == "standalone",
                    onClick = { store.setFloatingEngine("standalone") }
                )

                // Option 2: Native Freeform ROM
                EngineSelectBentoCard(
                    title = "Freeform ROM Native",
                    badge = "SISTEM AOSP/MIUI",
                    desc = "Memanfaatkan multi-window freeform bawaan sistem Android. Memerlukan dukungan fitur freeform dari vendor ROM atau bantuan LMO.",
                    icon = Icons.Default.PhoneAndroid,
                    isSelected = prefs.floatingEngine == "system_freeform",
                    onClick = { store.setFloatingEngine("system_freeform") }
                )
            }
        }
    }
}

@Composable
private fun EngineSelectBentoCard(
    title: String,
    badge: String,
    desc: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) AccentCyan.copy(alpha = 0.10f) else BgElevated,
        border = BorderStroke(
            1.dp,
            if (isSelected) AccentCyan else BorderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AccentCyan.copy(alpha = 0.2f) else BgCard)
                            .border(BorderStroke(0.6.dp, if (isSelected) AccentCyan.copy(alpha = 0.5f) else BorderSubtle), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) AccentCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else BgCard,
                            border = BorderStroke(0.5.dp, if (isSelected) AccentCyan.copy(alpha = 0.4f) else BorderSubtle)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) AccentCyan else TextSecondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Dipilih",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = desc,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

// ============================================================
// 4. SUBPAGE 1: SIZE CONFIGURATOR WITH LIVE PREVIEW
// ============================================================
@Composable
private fun FloatingSizeSection(
    prefs: HudPreferences,
    store: HudPreferencesStore
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // A. Live Visual Mockup Preview
        Surface(
            shape = RoundedCornerShape(16.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PRATINJAU UKURAN JENDELA",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "${prefs.floatingWidthPct}% × ${prefs.floatingHeightPct}%",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                // Interactive Phone Screen Silhouette Mockup
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090A0C))
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Grid background decoration
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(3) {
                            HorizontalDivider(color = Color(0xFF1A1D24).copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }

                    // Scaled Floating Window Mockup
                    val widthFraction = (prefs.floatingWidthPct / 100f).coerceIn(0.35f, 0.95f)
                    val heightFraction = (prefs.floatingHeightPct / 100f).coerceIn(0.35f, 0.90f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFraction)
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF141720))
                            .border(BorderStroke(1.2.dp, AccentCyan), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Mini title bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                }
                                Text("App", fontSize = 8.sp, color = TextSecondary)
                            }

                            // Center content
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Launch,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${prefs.floatingWidthPct}% × ${prefs.floatingHeightPct}%",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Bottom corner resize indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = null,
                                    tint = AccentCyan.copy(alpha = 0.6f),
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                    }
                }

                // 4 Preset Tiles
                val presetList = listOf(
                    Triple("compact", "Kompak", "55% × 50%"),
                    Triple("standard", "Standar", "70% × 65%"),
                    Triple("large", "Besar", "85% × 80%"),
                    Triple("custom", "Kustom", "Bebas")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetList.forEach { (id, label, sub) ->
                        val isSelected = prefs.floatingSizePreset == id
                        Surface(
                            onClick = {
                                if (id == "custom") {
                                    store.setFloatingCustomSize(prefs.floatingWidthPct, prefs.floatingHeightPct)
                                } else {
                                    store.setFloatingSizePreset(id)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else BgElevated,
                            border = BorderStroke(
                                0.8.dp,
                                if (isSelected) AccentCyan else BorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                Text(
                                    text = sub,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // B. Custom Sliders (Only if Custom is selected)
        if (prefs.floatingSizePreset == "custom") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "PENYESUAIAN MANUAL UKURAN JENDELA",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.6.sp
                    )

                    // Width Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lebar Jendela (Horizontal)", fontSize = 11.5.sp, color = TextSecondary)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentCyan.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${prefs.floatingWidthPct}%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Slider(
                            value = prefs.floatingWidthPct.toFloat(),
                            onValueChange = { store.setFloatingCustomSize(it.toInt(), prefs.floatingHeightPct) },
                            valueRange = 35f..95f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = BgElevated
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )
                    }

                    // Height Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tinggi Jendela (Vertikal)", fontSize = 11.5.sp, color = TextSecondary)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentCyan.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${prefs.floatingHeightPct}%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Slider(
                            value = prefs.floatingHeightPct.toFloat(),
                            onValueChange = { store.setFloatingCustomSize(prefs.floatingWidthPct, it.toInt()) },
                            valueRange = 35f..90f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = BgElevated
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 5. SUBPAGE 2: DOCK APP MANAGER
// ============================================================
@Composable
private fun FloatingAppsSection(
    prefs: HudPreferences,
    store: HudPreferencesStore
) {
    HudAppManager(
        prefs = prefs,
        store = store,
        modifier = Modifier.fillMaxWidth()
    )
}

// ============================================================
// 6. SUBPAGE 3: GUIDE & TIPS
// ============================================================
@Composable
private fun FloatingGuideSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "PANDUAN & GESTUR PENGGUNAAN",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    letterSpacing = 0.6.sp
                )

                // Gesture 1
                GuideItemRow(
                    icon = Icons.Default.OpenWith,
                    title = "Memindahkan Jendela (Move)",
                    desc = "Sentuh dan tahan bilah judul (header bar) di bagian atas jendela aplikasi, lalu geser ke posisi layar yang diinginkan."
                )

                // Gesture 2
                GuideItemRow(
                    icon = Icons.Default.AspectRatio,
                    title = "Mengubah Ukuran Jendela (Resize)",
                    desc = "Tarik ikon sudut kanan bawah jendela untuk memperbesar atau memperkecil ukuran jendela secara bebas."
                )

                // Gesture 3
                GuideItemRow(
                    icon = Icons.Default.Minimize,
                    title = "Minimalkan Jendela (Minimize)",
                    desc = "Ketuk tombol '-' pada bilah judul untuk menyembunyikan jendela kembali ke dock tanpa mematikan proses aplikasi."
                )

                // Gesture 4
                GuideItemRow(
                    icon = Icons.Default.Close,
                    title = "Menutup Jendela (Close)",
                    desc = "Ketuk tombol 'X' merah pada sudut bilah judul untuk menutup aplikasi dan membebaskan alokasi memori virtual display."
                )

                HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)

                // Performance note
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tips: Setiap jendela aplikasi yang aktif membutuhkan memori RAM sekitar 150-250 MB untuk virtual display. Disarankan untuk meminimalkan atau menutup jendela jika Anda memainkan game kompetitif yang haus memori.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideItemRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentCyan.copy(alpha = 0.12f))
                .border(BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.4f)), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
