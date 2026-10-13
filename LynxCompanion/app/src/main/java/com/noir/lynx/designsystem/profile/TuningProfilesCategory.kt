package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.AppIconImage

/**
 * TuningProfilesCategory — Full Kernel Performance Profile Management Subscreen.
 *
 * Provides a dedicated space within the Tuning Hub for:
 * - Viewing all built-in and user-created custom profiles (Katalog Profil Kernel)
 * - Creating, duplicating, editing, and deleting profiles
 * - Managing domain overrides and restoring default configurations
 * - Per-App Profiling & Automation Rules (Otomasi Profil Per-Aplikasi & Game)
 */
@Composable
fun TuningProfilesCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit = {},
    onEditRuleClick: (AppProfileRule) -> Unit = {},
    onExtremeConfirmRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
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
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Katalog & Editor Preset Performa",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Sesuaikan domain CPU, GPU, Thermal, dan Pengisian Daya per-profil serta tautkan ke game/aplikasi favorit Anda.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // ── 2. Main Profile Catalog Card ───────────────────────
        LynxCard(
            title = "Katalog Profil Kernel",
            icon = Icons.Default.Speed,
            accentColor = AccentOrange
        ) {
            ProfileGrid(
                catalog = uiState.kernelProfileCatalog,
                currentProfile = state.activeProfile,
                hasOverrides = state.profileOverrides.isNotEmpty(),
                onProfileSelected = { id, clearOverrides ->
                    viewModel.selectKernelPerformanceProfile(id, context, clearOverrides)
                },
                onExtremeConfirmRequired = onExtremeConfirmRequired,
                onProfileSaved = { profile, replaceBuiltIn, replaceCustom ->
                    viewModel.saveKernelPerformanceProfile(profile, replaceBuiltIn, replaceCustom)
                },
                onProfileDeleted = { id ->
                    viewModel.deleteKernelPerformanceProfile(id)
                },
                onProfileDuplicated = { id ->
                    viewModel.duplicateKernelPerformanceProfile(id)
                },
                onRestoreBuiltIn = { id ->
                    viewModel.restoreBuiltInKernelProfile(id, context)
                }
            )

            if (state.profileOverrides.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val overrides = state.profileOverrides.entries
                    .sortedBy { it.key.storageKey }
                    .joinToString(" • ") { (domain, profile) -> "${domain.storageKey.uppercase()}: ${profile.uppercase()}" }
                Text(
                    text = "Induk ${state.activeProfile.uppercase()} · override lokal $overrides",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // ── 3. Otomasi Profil Per-Aplikasi Card ─────────────────
        LynxCard(
            title = "Otomasi Profil Per-Aplikasi",
            icon = Icons.Default.SportsEsports,
            accentColor = AccentGreen
        ) {
            // Master Service Toggle
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Layanan Otomasi Latar Belakang",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Pantau aplikasi aktif dan alihkan profil CPU/GPU seketika (0ms boost saat launch)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = uiState.isAppAutomationActive,
                    onCheckedChange = { viewModel.toggleAppAutomation(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderGlass
                    )
                )
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))

            // Header: Rule Count & Add Button
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${uiState.appProfileRules.size} Aturan Profil Dikonfigurasi",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        "Profil khusus diterapkan otomatis saat aplikasi berada di foreground",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
                Surface(
                    onClick = onAddAppClick,
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // App Profile Rules List
            if (uiState.appProfileRules.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Tune, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Belum ada aturan khusus",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Klik tombol 'Tambah' untuk mengunci profil tertentu (Performa, Extreme, Balance, Hemat) ke game atau app favorit.",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                uiState.appProfileRules.forEach { rule ->
                    val profColor = when (rule.targetProfile.lowercase()) {
                        "extreme" -> AccentRed
                        "performance" -> AccentOrange
                        "powersave" -> AccentGreen
                        else -> AccentCyan
                    }
                    val profLabel = when (rule.targetProfile.lowercase()) {
                        "extreme" -> "Extreme"
                        "performance" -> "Performa"
                        "powersave" -> "Hemat"
                        else -> "Balance"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, if (rule.enabled) profColor.copy(alpha = 0.35f) else BorderGlass),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onEditRuleClick(rule) }
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                AppIconImage(
                                    packageName = rule.packageName,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            rule.appName,
                                            color = if (rule.enabled) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        if (rule.isGame) {
                                            Spacer(Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentOrange.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    "GAME",
                                                    color = AccentOrange,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = profColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, profColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                profLabel,
                                                color = profColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (rule.targetRefreshRate != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentPurple.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "${rule.targetRefreshRate} Hz",
                                                    color = AccentPurple,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (rule.autoFloatingHud) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentCyan.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "OSD",
                                                    color = AccentCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        val updated = rule.copy(enabled = enabled)
                                        viewModel.addOrUpdateAppProfileRule(context, updated)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = profColor,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated
                                    ),
                                    modifier = Modifier.scale(0.75f)
                                )
                                IconButton(
                                    onClick = { onEditRuleClick(rule) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
