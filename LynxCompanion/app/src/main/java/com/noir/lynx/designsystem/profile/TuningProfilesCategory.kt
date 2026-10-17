package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * UnifiedAppAutomationCard — All-in-one Bento Card containing automation master toggle,
 * buffer cooldown selection, smart auto-detection, clear-all, and the embedded list of configured rules.
 */
@Composable
fun UnifiedAppAutomationCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Hapus Semua Aturan?",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "Seluruh ${uiState.appProfileRules.size} konfigurasi aturan profil per-aplikasi akan dihapus dari sistem. Anda dapat mendeteksi ulang game terpasang kapan saja.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        viewModel.clearAllAppProfileRules(context)
                    }
                ) {
                    Text("Hapus Semua", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Batal", color = TextSecondary, fontSize = 13.sp)
                }
            },
            containerColor = BgElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // ── 1. Title & Subtitle + Master Switch ─────────────
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentGreen.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SportsEsports, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Layanan Otomasi Game / App",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "0ms responsivitas profil saat game/aplikasi diluncurkan",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.5.sp
                            )
                        }
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

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))

                // ── 2. Buffer Cooldown Selection ────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timelapse,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(com.noir.lynx.R.string.audit_cooldown_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(com.noir.lynx.R.string.audit_cooldown_description),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val cooldownOptions = listOf(
                    Triple(5, stringResource(com.noir.lynx.R.string.audit_five_seconds), stringResource(com.noir.lynx.R.string.audit_cooldown_fast)),
                    Triple(30, stringResource(com.noir.lynx.R.string.audit_thirty_seconds), stringResource(com.noir.lynx.R.string.audit_cooldown_optimal)),
                    Triple(60, stringResource(com.noir.lynx.R.string.audit_one_minute), stringResource(com.noir.lynx.R.string.audit_cooldown_light)),
                    Triple(180, stringResource(com.noir.lynx.R.string.audit_three_minutes), stringResource(com.noir.lynx.R.string.audit_cooldown_heavy)),
                    Triple(0, stringResource(com.noir.lynx.R.string.audit_persistent), stringResource(com.noir.lynx.R.string.audit_in_ram))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cooldownOptions.forEach { (sec, label, _) ->
                        val isSelected = uiState.appAutomationCooldownSeconds == sec
                        Surface(
                            onClick = { viewModel.setAppAutomationCooldownSeconds(context, sec) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AccentGreen.copy(alpha = 0.16f) else BgElevated,
                            border = BorderStroke(
                                if (isSelected) 1.2.dp else 0.8.dp,
                                if (isSelected) AccentGreen else BorderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) AccentGreen else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (sec == 30) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = AccentGreen.copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = stringResource(com.noir.lynx.R.string.audit_recommended),
                                            color = AccentGreen,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))

                // ── 3. App Rules Section Header with Actions ─────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Aturan Profil Aplikasi",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentGreen.copy(alpha = 0.18f),
                                border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "${uiState.appProfileRules.size}",
                                    color = AccentGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            "Ketuk pill profil untuk ganti cepat",
                            color = TextSecondary,
                            fontSize = 9.5.sp
                        )
                    }

                    // Action buttons row: [ Hapus ] [ Deteksi ] [ + Tambah ]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.appProfileRules.isNotEmpty()) {
                            Surface(
                                onClick = { showClearAllDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = AccentRed.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Hapus", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Surface(
                            onClick = { viewModel.autoDetectAndSyncInstalledRules(context) },
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f))
                        ) {
                            Row(
                                Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Deteksi", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            onClick = onAddAppClick,
                            shape = RoundedCornerShape(8.dp),
                            color = AccentGreen.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.5f))
                        ) {
                            Row(
                                Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Tambah", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── 4. Embedded Rules List / Empty State ────────────
                if (uiState.appProfileRules.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDeepOled.copy(alpha = 0.5f),
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SportsEsports, null, tint = AccentGreen.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Belum Ada Game / Aplikasi Terdaftar",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Pindai otomatis game yang terpasang di perangkat Anda atau tambahkan aplikasi secara manual.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.5.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    onClick = { viewModel.autoDetectAndSyncInstalledRules(context) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCyan.copy(alpha = 0.16f),
                                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Refresh, null, tint = AccentCyan, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Deteksi Game Terpasang", color = AccentCyan, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(
                                    onClick = onAddAppClick,
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentGreen.copy(alpha = 0.16f),
                                    border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Tambah Manual", color = AccentGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDeepOled.copy(alpha = 0.45f),
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            uiState.appProfileRules.forEachIndexed { index, rule ->
                                AppRuleEmbeddedRow(
                                    rule = rule,
                                    context = context,
                                    viewModel = viewModel,
                                    onEditRuleClick = onEditRuleClick
                                )
                                if (index < uiState.appProfileRules.size - 1) {
                                    HorizontalDivider(
                                        color = BorderGlass.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 10.dp)
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

@Composable
private fun AppRuleEmbeddedRow(
    rule: AppProfileRule,
    context: Context,
    viewModel: LynxViewModel,
    onEditRuleClick: (AppProfileRule) -> Unit
) {
    val profColor = when (rule.targetProfile.lowercase()) {
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(
                packageName = rule.packageName,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(7.dp))
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.appName,
                        color = if (rule.enabled) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                    if (rule.isGame) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = AccentOrange.copy(alpha = 0.18f)
                        ) {
                            Text(
                                "GAME",
                                color = AccentOrange,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Interactive Target Profile Cycle Pill
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
                        shape = RoundedCornerShape(4.dp),
                        color = profColor.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, profColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = profLabel,
                                color = profColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Ganti Profil",
                                tint = profColor,
                                modifier = Modifier.size(8.5.dp)
                            )
                        }
                    }
                    if (rule.targetRefreshRate != null) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = AccentPurple.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "${rule.targetRefreshRate} Hz",
                                color = AccentPurple,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (rule.autoFloatingHud) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = AccentCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "OSD",
                                color = AccentCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
                    viewModel.toggleAppProfileRule(context, rule.packageName, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = profColor,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = BgElevated
                ),
                modifier = Modifier.scale(0.72f)
            )
            IconButton(
                onClick = { onEditRuleClick(rule) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(
                onClick = { viewModel.deleteAppProfileRule(context, rule.packageName) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = AccentRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
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
