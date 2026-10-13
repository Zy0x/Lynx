package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.*

/**
 * Subscreen: Floating Game HUD & Real-time OSD Overlay
 * Houses all interactive controls, interaction modes, and visual style presets.
 */
@Composable
fun GameHudSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Descriptive Header Card ─────────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Real-Time Gaming Telemetry & OSD",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pantau FPS hardware, clock CPU/GPU, daya baterai (Watt), dan pengalih profil instan langsung di atas game Anda.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // ── 2. Master HUD Controller Card ──────────────────────
        LynxCard(
            title = "Master Sakelar Game HUD",
            icon = Icons.Default.SportsEsports,
            accentColor = AccentCyan
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Overlay Game HUD Real-Time",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        if (uiState.isGameHudActive) "HUD sedang berjalan di layar." else "HUD dalam status nonaktif.",
                        color = if (uiState.isGameHudActive) AccentCyan else TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = uiState.isGameHudActive,
                    onCheckedChange = { viewModel.toggleGameHud(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderGlass
                    )
                )
            }

            if (uiState.isGameHudActive) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "HUD Aktif! Geser bilah samping atau ketuk header HUD untuk interaksi cepat saat berada di dalam game.",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // ── 3. Interaction Mode Card ───────────────────────────
        LynxCard(
            title = "Mode Interaksi OSD",
            icon = Icons.Default.TouchApp,
            accentColor = AccentCyan
        ) {
            Text(
                "Pilih mekanisme tampilan overlay di layar saat game atau aplikasi dibuka:",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option 1: Edge Drawer
                val isDrawerSel = (uiState.hudMode == 0)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDrawerSel) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                    border = BorderStroke(1.dp, if (isDrawerSel) AccentCyan else BorderGlass),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setHudMode(context, 0) }
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isDrawerSel,
                                onClick = { viewModel.setHudMode(context, 0) },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentCyan),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Edge Drawer",
                                color = if (isDrawerSel) AccentCyan else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tersimpan rapi di tepi layar. Swipe untuk membuka bilah kontrol.",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }

                // Option 2: Floating Window
                val isFloatSel = (uiState.hudMode == 1)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFloatSel) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                    border = BorderStroke(1.dp, if (isFloatSel) AccentCyan else BorderGlass),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setHudMode(context, 1) }
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isFloatSel,
                                onClick = { viewModel.setHudMode(context, 1) },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentCyan),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Floating Window",
                                color = if (isFloatSel) AccentCyan else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Jendela melayang bebas di layar dengan koordinat drag & drop bebas.",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // Pin Mini FPS option (Only for Edge Drawer)
            if (uiState.hudMode == 0) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (uiState.hudPinMiniFps) AccentCyan.copy(alpha = 0.10f) else BgElevated,
                    border = BorderStroke(1.dp, if (uiState.hudPinMiniFps) AccentCyan.copy(alpha = 0.6f) else BorderGlass),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setHudPinMiniFps(context, !uiState.hudPinMiniFps) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                tint = if (uiState.hudPinMiniFps) AccentCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Pin Mini FPS di Samping Bilah",
                                    color = if (uiState.hudPinMiniFps) AccentCyan else TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Tampilkan tag angka FPS kecil saat bilah samping tertutup",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Switch(
                            checked = uiState.hudPinMiniFps,
                            onCheckedChange = { viewModel.setHudPinMiniFps(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentCyan,
                                checkedTrackColor = AccentCyan.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }

        // ── 4. Layout Style Selector Card ──────────────────────
        LynxCard(
            title = "Pilihan Gaya Tampilan OSD",
            icon = Icons.Default.Palette,
            accentColor = AccentCyan
        ) {
            Text(
                "Pilih salah satu dari 6 preset tata letak grafis telemetri:",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(10.dp))

            val hudStyleOptions = listOf(
                1 to "1. Vertical Pillar (RTSS)",
                2 to "2. Top Nano-Ribbon",
                3 to "3. Dual-Block Esport",
                4 to "4. Quad-Tiles Modular",
                5 to "5. Steam Deck Banner",
                6 to "6. Ghost Text (Frameless)"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hudStyleOptions.chunked(2).forEach { rowStyles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowStyles.forEach { (sId, sLabel) ->
                            val isSel = (uiState.hudStyle == sId)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) AccentCyan.copy(alpha = 0.18f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSel) AccentCyan else BorderGlass),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setHudStyle(context, sId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) AccentCyan else Color(0x33FFFFFF))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = sLabel,
                                        color = if (isSel) AccentCyan else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (rowStyles.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp).navigationBarsPadding())
    }
}
