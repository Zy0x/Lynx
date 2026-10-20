package com.noir.lynx.designsystem.profile

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.AppInfo
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.AppIconImage
import com.noir.lynx.ui.AppIconLoader
import com.noir.lynx.designsystem.theme.rememberFluidFlingBehavior

/**
 * AddAppRuleDialog — Zen-Minimalist App Selection & Registration Dialog.
 *
 * Designed for psychological ease, responsive filtering, and tactile elegance:
 * - Anti-overlap layout across various screen ratios (1080x2400 to 4K).
 * - High-efficiency virtualized list with preloaded icon cache.
 * - Clean category segmentation (Semua, Game, Aplikasi) with stable numeric badges.
 * - Graceful manual package fallback without layout distortion.
 */
@Composable
fun AddAppRuleDialog(
    installedApps: List<AppInfo>,
    configuredRules: List<AppProfileRule>,
    defaultTargetProfile: String = "performance",
    onDismiss: () -> Unit,
    onSelectApp: (AppProfileRule) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var activeCategoryFilter by remember { mutableStateOf("ALL") }

    val configuredPkgs = remember(configuredRules) {
        configuredRules.map { it.packageName }.toSet()
    }

    val availableApps = remember(installedApps, configuredPkgs) {
        installedApps.filter { !configuredPkgs.contains(it.packageName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    val availableGameCount = remember(availableApps) { availableApps.count { it.isGame } }
    val availableRegularCount = remember(availableApps) { availableApps.count { !it.isGame } }

    val q = searchQuery.trim().lowercase()
    val filteredApps = remember(availableApps, q, activeCategoryFilter) {
        availableApps.filter { app ->
            val matchQuery = q.isBlank() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
            val matchCat = when (activeCategoryFilter) {
                "GAME" -> app.isGame
                "APP" -> !app.isGame
                else -> true
            }
            matchQuery && matchCat
        }
    }

    LaunchedEffect(availableApps) {
        val pkgs = availableApps.take(25).map { it.packageName }
        AppIconLoader.preload(context, pkgs)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF10141F),
            border = BorderStroke(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(Color(0x30FFFFFF), Color(0x10FFFFFF))
                )
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .fillMaxHeight(0.82f)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // ── 1. Header (Icon + Title + Close) ───────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141824),
                            border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Pilih Aplikasi",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                if (availableApps.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0C0F17),
                                        border = BorderStroke(0.5.dp, Color(0x18FFFFFF))
                                    ) {
                                        Text(
                                            text = "${filteredApps.size}",
                                            color = TextSecondary.copy(alpha = 0.85f),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Pilih aplikasi untuk konfigurasi profil",
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = Color(0xFF141824),
                        border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── 2. Search Field (Zen Minimalist Capsule) ───────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgDeepOled.copy(alpha = 0.7f),
                    border = BorderStroke(
                        0.8.dp,
                        if (searchQuery.isNotEmpty()) AccentCyan.copy(alpha = 0.5f) else BorderGlass
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (searchQuery.isNotEmpty()) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Cari nama atau paket aplikasi...",
                                    color = TextSecondary.copy(alpha = 0.45f),
                                    fontSize = 11.5.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Default
                                ),
                                cursorBrush = SolidColor(AccentCyan),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus pencarian",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── 3. Category Filter Chips ───────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("ALL", "Semua" to availableApps.size, AccentCyan),
                        Triple("GAME", "Game" to availableGameCount, AccentOrange),
                        Triple("APP", "Aplikasi" to availableRegularCount, AccentCyan),
                    ).forEach { (key, meta, accent) ->
                        val (label, count) = meta
                        val isSel = activeCategoryFilter == key
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeCategoryFilter = key
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) accent.copy(alpha = 0.16f) else BgDeepOled.copy(alpha = 0.5f),
                            border = BorderStroke(0.7.dp, if (isSel) accent.copy(alpha = 0.55f) else BorderGlass),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) accent else TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSel) accent.copy(alpha = 0.22f) else BgDeepOled.copy(alpha = 0.7f),
                                    border = BorderStroke(0.5.dp, if (isSel) accent.copy(alpha = 0.45f) else BorderGlass.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "$count",
                                        color = if (isSel) accent else TextSecondary.copy(alpha = 0.75f),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── 4. Virtualized App List / Empty State ──────────────
                if (filteredApps.isEmpty() && installedApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Memuat daftar aplikasi...",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (availableApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen.copy(alpha = 0.75f),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Semua Aplikasi Terdaftar",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Seluruh aplikasi terpasang telah memiliki konfigurasi profil.",
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.45f),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Aplikasi Tidak Ditemukan",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Coba gunakan kata kunci lain atau ubah filter kategori.",
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                } else {
                    val addAppListState = rememberLazyListState()
                    LazyColumn(
                        state = addAppListState,
                        flingBehavior = rememberFluidFlingBehavior(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            count = filteredApps.size,
                            key = { filteredApps[it].packageName },
                            contentType = { "add_app_item" }
                        ) { i ->
                            val app = filteredApps[i]

                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val newRule = AppProfileRule(
                                        packageName = app.packageName,
                                        appName = app.label,
                                        targetProfile = if (app.isGame) "extreme" else defaultTargetProfile,
                                        enabled = true,
                                        targetRefreshRate = if (app.isGame) 120 else null,
                                        autoFloatingHud = app.isGame,
                                        isGame = app.isGame
                                    )
                                    onSelectApp(newRule)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = BgDeepOled.copy(alpha = 0.5f),
                                            border = BorderStroke(0.6.dp, BorderGlass.copy(alpha = 0.4f)),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                AppIconImage(
                                                    packageName = app.packageName,
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = app.label,
                                                    color = TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (app.isGame) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = AccentOrange.copy(alpha = 0.16f),
                                                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.4f))
                                                    ) {
                                                        Text(
                                                            text = "GAME",
                                                            color = AccentOrange,
                                                            fontSize = 7.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(1.5.dp))
                                            Text(
                                                text = app.packageName,
                                                color = TextSecondary.copy(alpha = 0.65f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = BgDeepOled.copy(alpha = 0.6f),
                                        border = BorderStroke(0.6.dp, BorderGlass),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Pilih",
                                                tint = AccentCyan.copy(alpha = 0.8f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (i < filteredApps.size - 1) {
                                HorizontalDivider(
                                    color = BorderGlass.copy(alpha = 0.15f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }

                // ── 5. Manual Package Input Fallback ───────────────────
                val trimmedInput = searchQuery.trim()
                val isAlreadyConfigured = configuredPkgs.contains(trimmedInput)
                if (trimmedInput.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isAlreadyConfigured) {
                                val existing = configuredRules.find { it.packageName == trimmedInput }
                                if (existing != null) {
                                    onSelectApp(existing)
                                }
                            } else {
                                val pkg = trimmedInput
                                val label = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                                val newRule = AppProfileRule(
                                    packageName = pkg,
                                    appName = label,
                                    targetProfile = defaultTargetProfile,
                                    enabled = true
                                )
                                onSelectApp(newRule)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAlreadyConfigured) AccentOrange.copy(alpha = 0.12f) else AccentCyan.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, if (isAlreadyConfigured) AccentOrange.copy(alpha = 0.4f) else AccentCyan.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isAlreadyConfigured) Icons.Default.Edit else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isAlreadyConfigured) AccentOrange else AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isAlreadyConfigured) "Buka Aturan Terdaftar: $trimmedInput" else "Konfigurasi Manual: $trimmedInput",
                                color = if (isAlreadyConfigured) AccentOrange else AccentCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
