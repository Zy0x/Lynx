package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.profiles.ProfileDomain
import com.noir.lynx.ui.AppIconImage

/**
 * ProfilesCategoryTabRow — High-performance Segmented Tab Pill Row for Kernel Performance Profiles.
 */
@Composable
fun ProfilesCategoryTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(0, "Katalog Profil", Icons.Default.Speed),
        Triple(1, "Otomasi Aplikasi", Icons.Default.SportsEsports)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
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
                val tabAccent = if (index == 0) AccentOrange else AccentGreen
                Surface(
                    onClick = { onSelectTab(index) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) tabAccent.copy(alpha = 0.16f) else Color.Transparent,
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
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * ProfilesFeedbackBottomSheet — Cyberpunk ModalBottomSheet for HUD & Notification Style Selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesFeedbackBottomSheet(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Gaya Notifikasi & Feedback Mode",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Visual umpan balik saat profil performa kernel beralih",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            val feedbackOptions = listOf(
                Triple("jewel_top", "Jewel Dynamic Island (HUD)", "Kapsul permata melayang di atas layar • Cyberpunk HUD"),
                Triple("jewel_bottom", "Jewel Bottom Capsule (HUD)", "Kapsul permata melayang di bawah layar • Cyberpunk HUD"),
                Triple("system_toast", "System Toast (OS Text)", "Gelembung teks standar Android di bawah layar • Ringan (~8 MB)"),
                Triple("status_bar", "Status Bar Saja (0 MB RAM)", "Notifikasi hening di status bar • Tanpa pop-up layar"),
                Triple("disabled", "Nonaktifkan Feedback", "Hening total tanpa notifikasi atau pop-up visual")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                feedbackOptions.forEach { (key, title, subtitle) ->
                    val isSelected = uiState.modeFeedbackStyle == key
                    Surface(
                        onClick = {
                            viewModel.setModeFeedbackStyle(context, key)
                            viewModel.triggerModeFeedback("balance", "Seimbang", 0xFF00A3FFL)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.12f) else BgElevated,
                        border = BorderStroke(
                            if (isSelected) 1.2.dp else 0.8.dp,
                            if (isSelected) AccentCyan else BorderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) AccentCyan else TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (key == "jewel_top") {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AccentCyan.copy(alpha = 0.2f),
                                            border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "DEFAULT",
                                                color = AccentCyan,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 13.5.sp
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) AccentCyan else Color.Transparent,
                                border = BorderStroke(1.5.dp, if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.4f)),
                                modifier = Modifier.size(16.dp)
                            ) {}
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 12.dp))

            // Dual Morph Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "Sinkronisasi Morpher Header Bersamaan",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Teks 'Codename: Deity' ikut bertransisi serasi saat Jewel Toast aktif",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = uiState.modeFeedbackDualMorph,
                    onCheckedChange = { viewModel.setModeFeedbackDualMorph(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderGlass
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // Live Test Button
            Surface(
                onClick = {
                    val sampleModes = listOf(
                        Triple("powersave", "Hemat", 0xFF00E676L),
                        Triple("balance", "Seimbang", 0xFF00A3FFL),
                        Triple("performance", "Performa", 0xFFFF9100L),
                        Triple("extreme", "Extreme", 0xFFFF3D00L)
                    )
                    val randomSample = sampleModes.random()
                    viewModel.triggerModeFeedback(randomSample.first, randomSample.second, randomSample.third)
                },
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Uji Coba Animasi Toast (Live Test)",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * tuningProfilesCategoryItems — Modular LazyListScope items for Kernel Performance Profile Subscreen.
 *
 * Tab 0: Katalog Profil
 * - Active State Hero Card
 * - 2x2 Bento Quick-Switch Grid
 *
 * Tab 1: Otomasi Aplikasi
 * - Master Automation & Cooldown Configuration Card
 * - Virtualized App Profile Rule Items (with one-tap profile cycle pill)
 */
fun LazyListScope.tuningProfilesCategoryItems(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit = {},
    onEditRuleClick: (AppProfileRule) -> Unit = {},
    onExtremeConfirmRequired: () -> Unit
) {
    val state = uiState.state

    when (uiState.selectedProfileTab) {
        0 -> {
            // ══════════════════════════════════════════════════════════
            // TAB 0: KATALOG PROFIL
            // ══════════════════════════════════════════════════════════

            // ── 1. Active Status Hero Card ─────────────────────────
            item(key = "tab0_hero_active_card") {
                val activeProfileId = state.activeProfile
                val activeColor = when (activeProfileId.lowercase()) {
                    "powersave" -> Color(0xFF00E676)
                    "balance" -> AccentBlue
                    "performance" -> AccentOrange
                    "extreme" -> AccentRed
                    else -> AccentCyan
                }
                val activeTitle = when (activeProfileId.lowercase()) {
                    "powersave" -> "Hemat Daya (Powersave)"
                    "balance" -> "Seimbang (Balance)"
                    "performance" -> "Performa Tinggi (Performance)"
                    "extreme" -> "Ekstrem (Extreme)"
                    else -> activeProfileId.replaceFirstChar { it.uppercase() }
                }
                val activeDesc = when (activeProfileId.lowercase()) {
                    "powersave" -> "Efisiensi daya maksimal untuk penggunaan baterai tahan lama"
                    "balance" -> "Responsif, dingin, dan optimal untuk seluruh aktivitas harian"
                    "performance" -> "Prioritas clock tinggi, devfreq responsif, dan frame rate stabil"
                    "extreme" -> "Bypass batasan termal, clock maksimal tanpa batas untuk gaming berat"
                    else -> "Profil aktif saat ini"
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, activeColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = activeColor,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "STATUS AKTIF",
                                        color = activeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                if (state.profileOverrides.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AccentOrange.copy(alpha = 0.18f),
                                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "OVERRIDE AKTIF",
                                            color = AccentOrange,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = activeTitle,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = activeDesc,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            if (state.profileOverrides.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                val overrides = state.profileOverrides.entries
                                    .sortedBy { it.key.storageKey }
                                    .joinToString(" • ") { (domain, profile) -> "${domain.storageKey.uppercase()}: ${profile.uppercase()}" }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Override: $overrides",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "Reset Override",
                                        color = AccentOrange,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.selectKernelPerformanceProfile(activeProfileId, context, true)
                                            }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Profile Catalog Grid Card ───────────────────────
            item(key = "tab0_profiles_catalog_card") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                                val colorHex = when (id.lowercase()) {
                                    "powersave" -> 0xFF00E676L
                                    "balance" -> 0xFF00A3FFL
                                    "performance" -> 0xFFFF9100L
                                    "extreme" -> 0xFFFF3D00L
                                    else -> 0xFF00E5FFL
                                }
                                val labelResource = mapOf(
                                    "powersave" to com.noir.lynx.R.string.audit_mode_powersave,
                                    "balance" to com.noir.lynx.R.string.audit_mode_balance,
                                    "performance" to com.noir.lynx.R.string.audit_mode_performance,
                                    "extreme" to com.noir.lynx.R.string.audit_mode_extreme
                                )[id.lowercase()]
                                val label = labelResource?.let(context::getString) ?: id
                                viewModel.triggerModeFeedback(id, label, colorHex)
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
                    }
                }
            }
        }

        1 -> {
            // ══════════════════════════════════════════════════════════
            // TAB 1: OTOMASI APLIKASI
            // ══════════════════════════════════════════════════════════
            item(key = "tab1_unified_automation_card") {
                UnifiedAppAutomationCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    onAddAppClick = onAddAppClick,
                    onEditRuleClick = onEditRuleClick
                )
            }
        }
    }
}

/**
 * ProfilesAutomationScreen — Dedicated Two-Zone Screen for Kernel Performance App Automation.
 *
 * Zone 1 (Top / Pinned): Modular Service Toggle & Nothing OS Segmented Cooldown Slider Card.
 * Stays stationary so controls are always accessible without scrolling.
 *
 * Zone 2 (Bottom / Weight 1f): In-Place Scrollable Rules Bento Card.
 * Contains pinned Search & Filter toolbar, and an internal virtualized LazyColumn of app rules.
 */
@Composable
fun ProfilesAutomationScreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "game", "active"

    val filteredRules = remember(uiState.appProfileRules, searchQuery, selectedFilter) {
        uiState.appProfileRules.filter { rule ->
            val matchesSearch = searchQuery.isBlank() ||
                rule.appName.contains(searchQuery, ignoreCase = true) ||
                rule.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "game" -> rule.isGame
                "active" -> rule.enabled
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = BgElevated,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = AccentRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.35f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Hapus Semua Aturan Profil?",
                    color = TextPrimary,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDeepOled,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Aturan Terdaftar",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentRed.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${uiState.appProfileRules.size} Aplikasi",
                                    color = AccentRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Tindakan ini akan menghapus seluruh konfigurasi profil performa, target refresh rate, dan floating HUD untuk semua game & aplikasi.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.5.sp,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.08f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Anda dapat memindai ulang game terpasang secara otomatis kapan saja dengan tombol Pindai.",
                                color = TextPrimary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 14.5.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Surface(
                    onClick = {
                        showClearAllDialog = false
                        viewModel.clearAllAppProfileRules(context)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRed.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.6f)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hapus Semua (${uiState.appProfileRules.size})",
                            color = AccentRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = { showClearAllDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    color = BgDeepOled,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Batal",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ══════════════════════════════════════════════════════════════
        // 1. ZONA ATAS (PINNED / TETAP): MODULAR SERVICE & COOLDOWN CARD
        // ══════════════════════════════════════════════════════════════
        AutomationServiceTopSection(
            uiState = uiState,
            viewModel = viewModel,
            context = context
        )

        Spacer(Modifier.height(10.dp))

        // ══════════════════════════════════════════════════════════════
        // 2. ZONA BAWAH (FLEKSIBEL WEIGHT 1F): IN-PLACE SCROLLABLE RULES
        // ══════════════════════════════════════════════════════════════
        AutomationRulesCard(
            uiState = uiState,
            viewModel = viewModel,
            context = context,
            filteredRules = filteredRules,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            selectedFilter = selectedFilter,
            onSelectFilter = { selectedFilter = it },
            showOverflowMenu = showOverflowMenu,
            onOverflowMenuDismiss = { showOverflowMenu = false },
            onOpenOverflowMenu = { showOverflowMenu = true },
            onClearAllClick = { showClearAllDialog = true },
            onAddAppClick = onAddAppClick,
            onEditRuleClick = onEditRuleClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Bottom Navigation Bar spacer to prevent floating dock collision
        Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
    }
}

/**
 * AutomationServiceTopSection — Pinned Top Section for Service Master Switch and Cooldown.
 */
@Composable
fun AutomationServiceTopSection(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Title & Subtitle + Master Switch
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDeepOled.copy(alpha = 0.6f),
                        border = BorderStroke(0.6.dp, BorderGlass),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SportsEsports, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Layanan Otomasi Profil",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Terapkan profil otomatis saat game atau aplikasi dibuka",
                            color = TextSecondary.copy(alpha = 0.75f),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                Switch(
                    checked = uiState.isAppAutomationActive,
                    onCheckedChange = { viewModel.toggleAppAutomation(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderGlass
                    ),
                    modifier = Modifier.scale(0.85f)
                )
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 8.dp))

            // Row 2: Cooldown Slider
            Text(
                text = "Penahanan Mode (Cooldown)",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            val cooldownOptions = listOf(
                5 to "5s",
                30 to "30s",
                60 to "1m",
                180 to "3m",
                0 to "Tetap"
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgDeepOled.copy(alpha = 0.6f),
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cooldownOptions.forEach { (sec, label) ->
                        val isSelected = uiState.appAutomationCooldownSeconds == sec
                        Surface(
                            onClick = { viewModel.setAppAutomationCooldownSeconds(context, sec) },
                            shape = RoundedCornerShape(7.dp),
                            color = if (isSelected) AccentCyan.copy(alpha = 0.22f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (isSelected) AccentCyan else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            Text(
                text = if (uiState.appAutomationCooldownSeconds == 30) {
                    "✓ Rekomendasi: 30 detik menahan profil saat keluar sementara"
                } else if (uiState.appAutomationCooldownSeconds == 0) {
                    "⚡ Profil terkunci aktif hingga manual diubah"
                } else {
                    "Buffer pendinginan: ${uiState.appAutomationCooldownSeconds} detik sebelum kembali ke seimbang"
                },
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * AutomationRulesCard — In-place scrollable container for application rules.
 */
@Composable
fun AutomationRulesCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    filteredRules: List<AppProfileRule>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    showOverflowMenu: Boolean,
    onOverflowMenuDismiss: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onClearAllClick: () -> Unit,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── PINNED HEADER INSIDE CARD ────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Header Row (Title, Count Badge, Action Buttons: Pindai, Tambah, Overflow)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Aturan Aplikasi",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                        if (uiState.appProfileRules.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgDeepOled.copy(alpha = 0.7f),
                                border = BorderStroke(0.6.dp, BorderGlass)
                            ) {
                                Text(
                                    text = "${uiState.appProfileRules.size}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minimal Primary Add Button
                        Surface(
                            onClick = onAddAppClick,
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCyan.copy(alpha = 0.14f),
                            border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Tambah", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Overflow Menu Button (⋮)
                        Box {
                            Surface(
                                onClick = onOpenOverflowMenu,
                                shape = RoundedCornerShape(8.dp),
                                color = BgDeepOled.copy(alpha = 0.6f),
                                border = BorderStroke(0.8.dp, BorderGlass),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uiState.isScanningInstalledGames) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = AccentCyan,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Opsi Lainnya",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = onOverflowMenuDismiss,
                                modifier = Modifier.background(BgElevated)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.isScanningInstalledGames) "Memindai Game..." else "Pindai Game Terpasang",
                                            color = if (uiState.isScanningInstalledGames) AccentCyan else TextPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        if (uiState.isScanningInstalledGames) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = AccentCyan,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = AccentCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    enabled = !uiState.isScanningInstalledGames,
                                    onClick = {
                                        onOverflowMenuDismiss()
                                        viewModel.autoDetectAndSyncInstalledRules(context)
                                    }
                                )
                                if (uiState.appProfileRules.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Hapus Semua Aturan",
                                                color = AccentRed,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = AccentRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            onOverflowMenuDismiss()
                                            onClearAllClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Full-Width Search & Filter Bar
                if (uiState.appProfileRules.isNotEmpty()) {
                    // Full-Width Search Bar
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgDeepOled.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, if (searchQuery.isNotEmpty()) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (searchQuery.isNotEmpty()) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Cari game atau nama paket...",
                                        color = TextSecondary.copy(alpha = 0.45f),
                                        fontSize = 12.5.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Default
                                    ),
                                    cursorBrush = SolidColor(AccentCyan),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus pencarian",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(7.dp))

                    // Minimal Filter Chips (Height 30dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filterOptions = listOf(
                            Triple("all", "Semua", uiState.appProfileRules.size),
                            Triple("game", "Game", uiState.appProfileRules.count { it.isGame }),
                            Triple("active", "Aktif", uiState.appProfileRules.count { it.enabled })
                        )
                        filterOptions.forEach { (filterKey, label, count) ->
                            val isSelected = selectedFilter == filterKey
                            Surface(
                                onClick = { onSelectFilter(filterKey) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else BgDeepOled.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isSelected) AccentCyan.copy(alpha = 0.55f) else BorderGlass
                                ),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) AccentCyan else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) AccentCyan.copy(alpha = 0.22f) else BgDeepOled.copy(alpha = 0.7f),
                                        border = BorderStroke(0.5.dp, if (isSelected) AccentCyan.copy(alpha = 0.45f) else BorderGlass.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "$count",
                                            color = if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.75f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.35f))

            // ── SCROLL-IN-PLACE RULES LIST ───────────────────────────
            if (uiState.appProfileRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentCyan.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Belum Ada Game / Aplikasi Terdaftar",
                            color = TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Pindai otomatis seluruh game yang terpasang di perangkat Anda atau tambahkan aplikasi secara manual.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.5.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { viewModel.autoDetectAndSyncInstalledRules(context) },
                                enabled = !uiState.isScanningInstalledGames,
                                shape = RoundedCornerShape(10.dp),
                                color = AccentCyan.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.55f)),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (uiState.isScanningInstalledGames) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            color = AccentCyan,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Memindai Game...",
                                            color = AccentCyan,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Pindai Game Terpasang",
                                            color = AccentCyan,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Surface(
                                onClick = onAddAppClick,
                                shape = RoundedCornerShape(10.dp),
                                color = BgDeepOled,
                                border = BorderStroke(1.dp, BorderGlass),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Tambah Manual",
                                        color = TextSecondary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (filteredRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, tint = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tidak Ada Aplikasi yang Cocok",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Coba kata kunci pencarian lain atau ubah filter yang dipilih.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                onSearchQueryChange("")
                                onSelectFilter("all")
                            }
                        ) {
                            Text("Reset Filter", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = filteredRules,
                        key = { it.packageName }
                    ) { rule ->
                        AppRuleEmbeddedRow(
                            rule = rule,
                            context = context,
                            viewModel = viewModel,
                            onEditRuleClick = onEditRuleClick
                        )
                        HorizontalDivider(
                            color = BorderGlass.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    item {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * UnifiedAppAutomationCard — Backward-compatible wrapper delegating to ProfilesAutomationScreen.
 */
@Composable
fun UnifiedAppAutomationCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    ProfilesAutomationScreen(
        uiState = uiState,
        viewModel = viewModel,
        context = context,
        onAddAppClick = onAddAppClick,
        onEditRuleClick = onEditRuleClick
    )
}

@Composable
private fun AppRuleEmbeddedRow(
    rule: AppProfileRule,
    context: Context,
    viewModel: LynxViewModel,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    val profDotColor = when (rule.targetProfile.lowercase()) {
        "extreme" -> AccentRed
        "performance" -> AccentOrange
        "powersave" -> AccentGreen
        else -> AccentCyan
    }
    val profLabel = when (rule.targetProfile.lowercase()) {
        "extreme" -> context.getString(com.noir.lynx.R.string.audit_mode_extreme)
        "performance" -> context.getString(com.noir.lynx.R.string.audit_mode_performance)
        "powersave" -> context.getString(com.noir.lynx.R.string.audit_mode_powersave)
        else -> context.getString(com.noir.lynx.R.string.audit_mode_balance)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditRuleClick(rule) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. App Icon (Crisp 38dp)
        AppIconImage(
            packageName = rule.packageName,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(10.dp))

        // 2. Middle Content (2-Line Balanced Column)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            // Line 1: App Title (14sp) + [GAME] Subtle Neutral Badge (9.5sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = rule.appName,
                    color = if (rule.enabled) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (rule.isGame) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BgDeepOled.copy(alpha = 0.7f),
                        border = BorderStroke(0.6.dp, BorderGlass)
                    ) {
                        Text(
                            "GAME",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            // Line 2: Stealth Dot Monokrom Pill (10.5sp) + Telemetry Badges + Package Name (11.5sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Interactive Target Profile Cycle Pill (Height 22dp)
                Surface(
                    onClick = {
                        val nextProfile = when (rule.targetProfile.lowercase()) {
                            "powersave" -> "balance"
                            "balance" -> "performance"
                            "performance" -> "extreme"
                            "extreme" -> "powersave"
                            else -> "balance"
                        }
                        viewModel.addOrUpdateAppProfileRule(context, rule.copy(targetProfile = nextProfile))
                    },
                    shape = RoundedCornerShape(5.dp),
                    color = BgDeepOled.copy(alpha = 0.8f),
                    border = BorderStroke(0.8.dp, BorderGlass)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Status Dot (6dp)
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(profDotColor)
                        )
                        Text(
                            text = profLabel,
                            color = TextPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                        )
                    }
                }

                if (rule.targetRefreshRate != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BgDeepOled.copy(alpha = 0.6f),
                        border = BorderStroke(0.6.dp, BorderGlass)
                    ) {
                        Text(
                            "${rule.targetRefreshRate} Hz",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                }

                if (rule.autoFloatingHud) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BgDeepOled.copy(alpha = 0.6f),
                        border = BorderStroke(0.6.dp, BorderGlass)
                    ) {
                        Text(
                            "OSD",
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                }

                Text(
                    text = "•",
                    color = TextSecondary.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )

                Text(
                    text = rule.packageName,
                    color = TextSecondary.copy(alpha = 0.55f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 3. Right Action: Uniform Lynx Cyan Master Switch
        Switch(
            checked = rule.enabled,
            onCheckedChange = { enabled ->
                viewModel.toggleAppProfileRule(context, rule.packageName, enabled)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgDeepOled,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BgElevated,
                uncheckedBorderColor = BorderGlass
            ),
            modifier = Modifier.scale(0.82f)
        )
    }
}

/**
 * TuningProfilesCategory — Standalone Composable wrapper for Kernel Performance Profiles.
 * Maintained for backward compatibility.
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
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadAppAutomationCooldown(context)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ProfilesCategoryTabRow(
            selectedTab = uiState.selectedProfileTab,
            onSelectTab = { viewModel.selectProfileTab(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            tuningProfilesCategoryItems(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                onAddAppClick = onAddAppClick,
                onEditRuleClick = onEditRuleClick,
                onExtremeConfirmRequired = onExtremeConfirmRequired
            )
        }
    }
}
