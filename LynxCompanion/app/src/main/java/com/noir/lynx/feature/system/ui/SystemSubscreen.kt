package com.noir.lynx.feature.system.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.localizedUiText
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.controls.LynxTabPager
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.*

/**
 * Tab Row Obsidian Pill untuk Subscreen 7: Subsystem & Deep Tunables.
 * 3 segmen mandiri:
 * 0: Sistem & GSI (SELinux, Printk Silent, Android GSI Treble Audio Bluetooth, Opsi Pengembang)
 * 1: Deep Sysfs (Katalog pemindai node kernel, OEM, vendor, dan sysfs universal)
 * 2: Alat Kernel (Pengelola skrip shell boot & penampil log dmesg ring buffer)
 */
@Composable
fun SystemTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tabs = listOf(
        Triple(0, "Sistem & GSI", Icons.Default.Security),
        Triple(1, "Deep Sysfs", Icons.Default.Tune),
        Triple(2, "Alat Kernel", Icons.Default.Terminal)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF10141E),
        border = BorderStroke(0.6.dp, Color(0x1AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan.copy(alpha = 0.22f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "sys_tab_bg_$index"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan else TextSecondary,
                    animationSpec = tween(durationMillis = 200),
                    label = "sys_tab_content_$index"
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(index)
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = bgColor,
                    border = if (isSelected) BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = title,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Konten Terpadu Subscreen Sistem & Deep Tunables yang dibagi menjadi 3 Tab Terfokus.
 */
@Composable
fun SystemSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    targetTab: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (targetTab) {
            0 -> SystemGsiTabContent(uiState = uiState, viewModel = viewModel)
            1 -> DeepSysfsTabContent(uiState = uiState, viewModel = viewModel)
            else -> KernelToolsTabContent(uiState = uiState, viewModel = viewModel)
        }
    }
}

/**
 * TAB 0: SISTEM & GSI
 * Menghadirkan pengaturan keamanan SELinux, Printk Suppressor, Perbaikan cerdas ROM GSI / Treble,
 * serta opsi pengembang dan daya sistem.
 */
@Composable
fun SystemGsiTabContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    var showPermissiveWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshSystemFrameworkSummary()
    }

    // 1. Hero Status Framework & Keamanan OS
    SystemFrameworkHeroCard(
        uiState = uiState,
        onRefresh = { viewModel.refreshSystemFrameworkSummary() }
    )

    // 2. Keamanan & Penegakan Kernel (SELinux & Printk)
    KernelSecurityCard(
        uiState = uiState,
        onSetSelinux = { viewModel.setSelinuxMode(it) },
        onSetPrintkSilent = { viewModel.setPrintkSilent(it) },
        onShowWarningDialog = { showPermissiveWarningDialog = true }
    )

    // 3. Penyetelan Kelancaran UI & Framework Android (Animasi, Blurs, Phantom Procs)
    AndroidFrameworkUiCard(
        uiState = uiState,
        onSetAnimationScale = { viewModel.setAnimationScale(it) },
        onSetDisableWindowBlurs = { viewModel.setDisableWindowBlurs(it) },
        onSetDisablePhantomProcesses = { viewModel.setDisablePhantomProcesses(it) }
    )

    // 4. Kompatibilitas Android GSI & Treble (QEMU Mainkeys, Speed-Profile Dexopt)
    GsiTrebleFrameworkCard(
        uiState = uiState,
        onSetForceShowNavbar = { viewModel.setForceShowNavbar(it) },
        onRunDexopt = { viewModel.runArtSpeedProfileDexopt() },
        onClearDexoptOutput = { viewModel.clearDexoptOutput() }
    )

    // 5. Diagnostik Daya & Doze Subsystem (Uji Doze Deep Sleep)
    DozePowerDiagnosticCard(
        onTriggerDoze = { viewModel.triggerDozeDeepSleep() },
        onUnforceDoze = { viewModel.restoreDozeTimer() }
    )

    // Modal Peringatan Cyberpunk Beralih ke Permissive
    if (showPermissiveWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPermissiveWarningDialog = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xFF141724),
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Peringatan Keamanan SELinux",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Mengubah SELinux ke mode Permissive akan menonaktifkan penegakan akses wajib Android. Modul root atau kernel kustom tertentu mungkin membutuhkannya, namun perlindungan partisi sistem terhadap eksploitasi akan menurun.\n\nApakah Anda yakin ingin melanjutkan?",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissiveWarningDialog = false
                        viewModel.setSelinuxMode(false)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange.copy(alpha = 0.22f),
                        contentColor = AccentOrange
                    ),
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.55f))
                ) {
                    Text("Tetap Ubah ke Permissive", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPermissiveWarningDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Batal", fontSize = 11.5.sp)
                }
            }
        )
    }
}

/**
 * 1. Hero Status Framework & Keamanan OS
 */
@Composable
fun SystemFrameworkHeroCard(
    uiState: LynxUiState,
    onRefresh: () -> Unit
) {
    val fw = uiState.systemFramework

    LaunchedEffect(Unit) {
        if (fw.androidVersion.isBlank()) {
            onRefresh()
        }
    }

    val isEnforcing = uiState.selinuxMode.equals("Enforcing", ignoreCase = true)
    val isTreble = fw.frameworkType.contains("Treble", ignoreCase = true) || fw.frameworkType.contains("GSI", ignoreCase = true)
    val isDmVerityEnforcing = fw.partitionProtection.contains("Aktif", ignoreCase = true) || fw.partitionProtection.contains("Enforcing", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentPurple.copy(alpha = 0.45f),
                    BorderSubtle
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Status Indicator & Refresh Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(shape = CircleShape, color = AccentPurple.copy(alpha = 0.22f), modifier = Modifier.size(10.dp)) {}
                        Surface(shape = CircleShape, color = AccentPurple, modifier = Modifier.size(5.dp)) {}
                    }
                    Text(
                        text = "Framework Android & Keamanan OS",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isEnforcing) AccentGreen.copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, if (isEnforcing) AccentGreen.copy(alpha = 0.45f) else AccentOrange.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (isEnforcing) "SELinux: Enforcing" else "SELinux: Permissive",
                            color = if (isEnforcing) AccentGreen else AccentOrange,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    if (fw.isLoading) {
                        CircularProgressIndicator(color = AccentPurple, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, "Segarkan", tint = AccentPurple, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Main Android Version & Framework Hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentPurple.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.35f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Android ${fw.androidVersion.ifBlank { android.os.Build.VERSION.RELEASE }}",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "API Level ${if (fw.apiLevel > 0) fw.apiLevel else android.os.Build.VERSION.SDK_INT} • Build ${android.os.Build.ID}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4-Bento Telemetry Tiles (2x2 Grid)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tile 1: Tipe Framework
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Layers, null, tint = AccentBlue, modifier = Modifier.size(12.dp))
                                Text("FRAMEWORK", color = TextTertiary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = fw.frameworkType.ifBlank { if (isTreble) "Project Treble" else "Stock Vendor" },
                                color = if (isTreble) AccentBlue else AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Tile 2: Partisi dm-verity
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.VerifiedUser, null, tint = if (isDmVerityEnforcing) AccentGreen else AccentOrange, modifier = Modifier.size(12.dp))
                                Text("DM-VERITY", color = TextTertiary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = fw.partitionProtection.ifBlank { "Terproteksi" },
                                color = if (isDmVerityEnforcing) AccentGreen else AccentOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tile 3: Status Akses SELinux
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Security, null, tint = if (isEnforcing) AccentGreen else AccentOrange, modifier = Modifier.size(12.dp))
                                Text("SELINUX ACCESS", color = TextTertiary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (isEnforcing) "Enforcing (Aman)" else "Permissive (Bypass)",
                                color = if (isEnforcing) AccentGreen else AccentOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Tile 4: Status Kesiapan Kernel & Sistem
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = AccentCyan, modifier = Modifier.size(12.dp))
                                Text("STATUS OS", color = TextTertiary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (isTreble) "Treble Siap Tuning" else "OS Vendor Stabil",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Bottom System Integrity Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AccentPurple.copy(alpha = 0.08f),
                border = BorderStroke(0.6.dp, AccentPurple.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Framework dan partisi sistem siap untuk tuning animasi UI, penjadwalan proses, dan diagnostik daya.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 2. Keamanan & Penegakan Kernel (SELinux & Printk)
 */
@Composable
fun KernelSecurityCard(
    uiState: LynxUiState,
    onSetSelinux: (Boolean) -> Unit,
    onSetPrintkSilent: (Boolean) -> Unit,
    onShowWarningDialog: () -> Unit
) {
    val isEnforcing = uiState.selinuxMode.equals("Enforcing", ignoreCase = true)

    LynxCard(
        title = "Mode SELinux & Keamanan Kernel",
        icon = Icons.Default.Shield,
        accentColor = AccentCyan
    ) {
        // SELinux switch via LynxSwitch
        LynxSwitch(
            label = "Penegakan Akses SELinux",
            subLabel = if (isEnforcing) {
                "Enforcing: Kebijakan akses wajib Android aktif melindungi partisi sistem dari modifikasi proses tidak sah."
            } else {
                "Permissive: SELinux mengizinkan seluruh modul root berjalan tanpa batasan kebijakan keamanan (Akses Bebas)."
            },
            checked = isEnforcing,
            onCheckedChange = { willEnforce ->
                if (!willEnforce) {
                    onShowWarningDialog()
                } else {
                    onSetSelinux(true)
                }
            }
        )

        HorizontalDivider(
            color = BorderGlass.copy(alpha = 0.5f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        // Peredam Log Konsol Kernel (Printk Silent) via LynxSwitch
        LynxSwitch(
            label = "Peredam Log Konsol Kernel (Printk Silent)",
            subLabel = "Meredam pencatatan log debug konsol kernel (0 0 0 0) saat sistem berjalan. Mencegah CPU sering terbangun dari Deep Sleep dan menghemat baterai standby.",
            checked = uiState.isPrintkSilent,
            onCheckedChange = onSetPrintkSilent
        )
    }
}

/**
 * 3. Penyetelan Kelancaran UI & Framework Android (Animasi, Blurs, Phantom Procs)
 */
@Composable
fun AndroidFrameworkUiCard(
    uiState: LynxUiState,
    onSetAnimationScale: (Float) -> Unit,
    onSetDisableWindowBlurs: (Boolean) -> Unit,
    onSetDisablePhantomProcesses: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LynxCard(
        title = "Penyetelan Framework & Kelancaran UI",
        subtitle = "Optimasi durasi animasi, rendering SurfaceFlinger, dan manajemen proses Android.",
        icon = Icons.Default.Speed,
        accentColor = AccentPurple
    ) {
        // Skala Animasi Sistem 1-Ketukan
        Text(
            text = "Skala Animasi Sistem (Window, Transition, Animator)",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Mengatur durasi ketiga parameter animasi sistem secara instan dan harmonis.",
            color = TextSecondary,
            fontSize = 10.5.sp,
            lineHeight = 14.5.sp
        )

        Spacer(Modifier.height(10.dp))

        val animPresets = listOf(
            1.0f to "1.0x (Normal)",
            0.5f to "0.5x (Cepat)",
            0.25f to "0.25x (Kilat)",
            0.0f to "Mati (Instan)"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            animPresets.forEach { (scale, label) ->
                val isSelected = kotlin.math.abs(uiState.animationScale - scale) < 0.05f
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSetAnimationScale(scale)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentPurple.copy(alpha = 0.22f) else BgElevated,
                    border = BorderStroke(
                        if (isSelected) 1.dp else 0.8.dp,
                        if (isSelected) AccentPurple.copy(alpha = 0.65f) else BorderSubtle
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (isSelected) AccentPurple else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = BorderGlass.copy(alpha = 0.5f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        // Nonaktifkan Window Blurs Sistem via LynxSwitch
        LynxSwitch(
            label = "Nonaktifkan Window Blurs Sistem",
            subLabel = "Mematikan efek blur Gaussian pada notification shade dan app switcher di Android 12+. Sangat efektif menghilangkan micro-stutter pada GPU.",
            checked = uiState.disableWindowBlurs,
            onCheckedChange = onSetDisableWindowBlurs
        )

        HorizontalDivider(
            color = BorderGlass.copy(alpha = 0.5f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        // Matikan Pembunuh Proses Phantom via LynxSwitch
        LynxSwitch(
            label = "Matikan Pembunuh Proses Phantom",
            subLabel = "Menaikkan limit proses phantom menjadi tak terbatas (2.147.483.647). Mencegah Termux, daemon Lynx, dan server lokal dibunuh paksa oleh Android 12+.",
            checked = uiState.disablePhantomProcesses,
            onCheckedChange = onSetDisablePhantomProcesses
        )
    }
}

/**
 * 4. Kompatibilitas Android GSI & Treble (QEMU Mainkeys, Speed-Profile Dexopt)
 */
@Composable
fun GsiTrebleFrameworkCard(
    uiState: LynxUiState,
    onSetForceShowNavbar: (Boolean) -> Unit,
    onRunDexopt: () -> Unit,
    onClearDexoptOutput: () -> Unit
) {
    LynxCard(
        title = "Framework GSI & Kompatibilitas Treble",
        subtitle = "Penyetelan bilah navigasi dan optimasi kompilasi Ahead-Of-Time (AOT).",
        icon = Icons.Default.Layers,
        accentColor = AccentBlue
    ) {
        // Bilah Navigasi Layar (QEMU Mainkeys) via LynxSwitch
        LynxSwitch(
            label = "Paksa Bilah Navigasi Layar (QEMU Mainkeys)",
            subLabel = "Mengontrol kemunculan tombol navigasi software (Back, Home, Recents) pada ROM GSI. Memperbaiki tombol navigasi yang hilang atau dobel.",
            checked = uiState.forceShowNavbar,
            onCheckedChange = onSetForceShowNavbar
        )

        HorizontalDivider(
            color = BorderGlass.copy(alpha = 0.5f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        // Kompilasi ART Speed-Profile Instan
        Text(
            text = "Kompilasi ART / Dexopt Cepat (Speed-Profile)",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Mengompilasi bytecode aplikasi ke native code secara offline. Mempercepat pembukaan aplikasi dan melenyapkan micro-stutter saat baru berganti ROM.",
            color = TextSecondary,
            fontSize = 10.5.sp,
            lineHeight = 14.5.sp
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onRunDexopt,
            enabled = !uiState.isDexoptRunning,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue.copy(alpha = 0.20f),
                contentColor = AccentBlue,
                disabledContainerColor = BgElevated,
                disabledContentColor = TextTertiary
            ),
            border = BorderStroke(1.dp, if (!uiState.isDexoptRunning) AccentBlue.copy(alpha = 0.50f) else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            if (uiState.isDexoptRunning) {
                CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sedang Mengompilasi Bytecode Aplikasi...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Jalankan Kompilasi ART Dexopt", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        uiState.lastDexoptOutput?.let { out ->
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF090D14),
                border = BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Terminal, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                            Text("OUTPUT LOG DEXOPT", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onClearDexoptOutput, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, "Tutup Log", tint = TextTertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = out,
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * 5. Diagnostik Daya & Doze Subsystem (Uji Doze Deep Sleep)
 */
@Composable
fun DozePowerDiagnosticCard(
    onTriggerDoze: () -> Unit,
    onUnforceDoze: () -> Unit
) {
    LynxCard(
        title = "Diagnostik Daya & Doze Subsystem",
        subtitle = "Pengujian status tidur lelap Android (Doze) untuk mengevaluasi efisiensi baterai.",
        icon = Icons.Default.Bedtime,
        accentColor = AccentOrange
    ) {
        Text(
            text = "Pengujian status tidur lelap Android (Doze) untuk memastikan konsumsi baterai standby saat layar mati berada dalam tingkat paling efisien.",
            color = TextSecondary,
            fontSize = 10.5.sp,
            lineHeight = 14.5.sp
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onTriggerDoze,
                shape = RoundedCornerShape(10.dp),
                color = AccentOrange.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.45f)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Bedtime, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Paksa Deep Sleep", color = AccentOrange, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text("dumpsys force-idle", color = AccentOrange.copy(alpha = 0.75f), fontSize = 9.sp)
                    }
                }
            }

            Surface(
                onClick = onUnforceDoze,
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Restore, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Pulihkan Normal", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text("dumpsys unforce", color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

/**
 * TAB 1: DEEP SYSFS TUNABLES
 * Mesin pemindai adaptif universal untuk seluruh parameter kernel, OEM, vendor, dan sysfs
 * yang tidak dihadirkan di halaman-halaman tuning khusus lainnya.
 */
@Composable
fun DeepSysfsTabContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    var manualNodePath by remember { mutableStateOf("") }
    var showManualNodeDialog by remember { mutableStateOf(false) }
    var maxVisibleItems by remember { mutableIntStateOf(25) }
    var deepSearchQuery by remember { mutableStateOf("") }
    var activeCategoryFilter by remember { mutableStateOf("Semua") }

    val totalNodes = uiState.deepTunables.size
    val writableCount = remember(uiState.deepTunables) { uiState.deepTunables.count { it.writable } }
    val readOnlyCount = remember(uiState.deepTunables) { uiState.deepTunables.count { !it.writable } }

    // 1. Scanner Hero Bento Card
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentCyan.copy(alpha = 0.5f),
                    BorderSubtle
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Katalog Sysfs Dinamis Universal",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Menampung seluruh parameter kernel & vendor yang tidak ada di halaman tuning khusus.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 4-Tile Bento Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tile 1: Total Node
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL NODE",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$totalNodes",
                            color = AccentCyan,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Tile 2: Dapat Diubah (R/W)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DAPAT DIUBAH",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$writableCount",
                            color = AccentGreen,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Tile 3: Terproteksi (R/O)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "READ-ONLY",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$readOnlyCount",
                            color = AccentOrange,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Tile 4: Filter Aktif
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "KATEGORI",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (activeCategoryFilter == "Semua") "Semua" else activeCategoryFilter.substringBefore(" ").take(6),
                            color = AccentPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons: Scan + Manual Node Inspector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.runDeepScan() },
                    enabled = !uiState.isDeepScanning,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = BgDeepOled,
                        disabledContainerColor = AccentCyan.copy(alpha = 0.3f),
                        disabledContentColor = TextTertiary
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(46.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    if (uiState.isDeepScanning) {
                        CircularProgressIndicator(
                            color = BgDeepOled,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Memindai...", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (uiState.deepTunables.isEmpty()) "Pindai Sysfs" else "Pindai Ulang",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showManualNodeDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = BgElevated,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Terminal, null, tint = AccentCyan, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Uji Path", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
        }
    }

    if (uiState.deepTunables.isNotEmpty()) {
        // Dynamic Category Counts
        val categoryCounts = remember(uiState.deepTunables) {
            mapOf(
                "Semua" to uiState.deepTunables.size,
                "CPU / PPM" to uiState.deepTunables.count { it.category.contains("CPU", true) || it.category.contains("PPM", true) || it.category.contains("Penjadwal", true) || it.category.contains("Scheduler", true) },
                "RAM / VM" to uiState.deepTunables.count { it.category.contains("RAM", true) || it.category.contains("Memory", true) || it.category.contains("VM", true) },
                "GPU & Grafis" to uiState.deepTunables.count { it.category.contains("GPU", true) || it.category.contains("Grafis", true) },
                "Penyimpanan" to uiState.deepTunables.count { it.category.contains("Storage", true) || it.category.contains("I/O", true) || it.category.contains("Penyimpanan", true) || it.category.contains("Block", true) },
                "Sistem & Daya" to uiState.deepTunables.count { it.category.contains("Power", true) || it.category.contains("Thermal", true) || it.category.contains("Daya", true) || it.category.contains("General", true) || it.category.contains("System", true) }
            )
        }

        // 2. Filter Kategori Grid 2x3 Seimbang
        val categories = listOf(
            Triple("Semua", Icons.Default.Tune, categoryCounts["Semua"] ?: 0),
            Triple("CPU / PPM", Icons.Default.Memory, categoryCounts["CPU / PPM"] ?: 0),
            Triple("RAM / VM", Icons.Default.Storage, categoryCounts["RAM / VM"] ?: 0),
            Triple("GPU & Grafis", Icons.Default.VideogameAsset, categoryCounts["GPU & Grafis"] ?: 0),
            Triple("Penyimpanan", Icons.Default.SdStorage, categoryCounts["Penyimpanan"] ?: 0),
            Triple("Sistem & Daya", Icons.Default.Bolt, categoryCounts["Sistem & Daya"] ?: 0)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.chunked(3).forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chunk.forEach { (catName, catIcon, count) ->
                        val isSel = activeCategoryFilter == catName
                        Surface(
                            onClick = {
                                activeCategoryFilter = catName
                                maxVisibleItems = 25
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) AccentCyan.copy(alpha = 0.18f) else BgCard,
                            border = BorderStroke(0.8.dp, if (isSel) AccentCyan else BorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = null,
                                    tint = if (isSel) AccentCyan else TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "$catName ($count)",
                                    color = if (isSel) AccentCyan else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Kolom Pencarian Cerdas & Info Counter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = deepSearchQuery,
                onValueChange = {
                    deepSearchQuery = it
                    maxVisibleItems = 25
                },
                placeholder = { Text("Cari nama parameter, path, atau deskripsi...", fontSize = 11.5.sp, color = TextTertiary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (deepSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { deepSearchQuery = "" }) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard
                )
            )

            // Sub-counter row & Quick Reset Chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val countDisplay = remember(uiState.deepTunables, deepSearchQuery, activeCategoryFilter) {
                    uiState.deepTunables.count { tunable ->
                        val matchesCat = when (activeCategoryFilter) {
                            "Semua" -> true
                            "CPU / PPM" -> tunable.category.contains("CPU", true) || tunable.category.contains("PPM", true) || tunable.category.contains("Penjadwal", true) || tunable.category.contains("Scheduler", true)
                            "RAM / VM" -> tunable.category.contains("RAM", true) || tunable.category.contains("Memory", true) || tunable.category.contains("VM", true)
                            "GPU & Grafis" -> tunable.category.contains("GPU", true) || tunable.category.contains("Grafis", true)
                            "Penyimpanan" -> tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) || tunable.category.contains("Penyimpanan", true) || tunable.category.contains("Block", true)
                            "Sistem & Daya" -> tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) || tunable.category.contains("Daya", true) || tunable.category.contains("General", true) || tunable.category.contains("System", true)
                            else -> tunable.category.equals(activeCategoryFilter, ignoreCase = true)
                        }
                        val matchesSearch = deepSearchQuery.isBlank() ||
                                tunable.name.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.rawName.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.desc.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.path.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.help.contains(deepSearchQuery, ignoreCase = true)

                        matchesCat && matchesSearch
                    }
                }

                Text(
                    text = "Menampilkan $countDisplay dari ${uiState.deepTunables.size} parameter",
                    color = TextTertiary,
                    fontSize = 11.sp
                )

                if (deepSearchQuery.isNotEmpty() || activeCategoryFilter != "Semua") {
                    Surface(
                        onClick = {
                            deepSearchQuery = ""
                            activeCategoryFilter = "Semua"
                            maxVisibleItems = 25
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = AccentCyan.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Reset Filter",
                            color = AccentCyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // 4. Daftar Kartu Sysfs yang Difilter
        val displayedTunables = remember(uiState.deepTunables, deepSearchQuery, activeCategoryFilter) {
            uiState.deepTunables.filter { tunable ->
                val matchesCat = when (activeCategoryFilter) {
                    "Semua" -> true
                    "CPU / PPM" -> tunable.category.contains("CPU", true) || tunable.category.contains("PPM", true) || tunable.category.contains("Penjadwal", true) || tunable.category.contains("Scheduler", true)
                    "RAM / VM" -> tunable.category.contains("RAM", true) || tunable.category.contains("Memory", true) || tunable.category.contains("VM", true)
                    "GPU & Grafis" -> tunable.category.contains("GPU", true) || tunable.category.contains("Grafis", true)
                    "Penyimpanan" -> tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) || tunable.category.contains("Penyimpanan", true) || tunable.category.contains("Block", true)
                    "Sistem & Daya" -> tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) || tunable.category.contains("Daya", true) || tunable.category.contains("General", true) || tunable.category.contains("System", true)
                    else -> tunable.category.equals(activeCategoryFilter, ignoreCase = true)
                }
                val matchesSearch = deepSearchQuery.isBlank() ||
                        tunable.name.contains(deepSearchQuery, ignoreCase = true) ||
                        tunable.rawName.contains(deepSearchQuery, ignoreCase = true) ||
                        tunable.desc.contains(deepSearchQuery, ignoreCase = true) ||
                        tunable.path.contains(deepSearchQuery, ignoreCase = true) ||
                        tunable.help.contains(deepSearchQuery, ignoreCase = true)

                matchesCat && matchesSearch
            }
        }

        if (displayedTunables.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.SearchOff, null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tidak ada parameter yang cocok dengan kriteria",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Coba ubah kata kunci pencarian atau ganti filter kategori.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            deepSearchQuery = ""
                            activeCategoryFilter = "Semua"
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f))
                    ) {
                        Text("Reset Pencarian", color = AccentCyan, fontSize = 11.5.sp)
                    }
                }
            }
        } else {
            val itemsToShow = displayedTunables.take(maxVisibleItems)
            for (tunable in itemsToShow) {
                DeepTunableItemCard(
                    tunable = tunable,
                    onApply = { path, value -> viewModel.applyDeepTunable(path, value) }
                )
            }

            if (displayedTunables.size > maxVisibleItems) {
                Surface(
                    onClick = { maxVisibleItems += 30 },
                    shape = RoundedCornerShape(14.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ExpandMore, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Muat Lebih Banyak (${displayedTunables.size - maxVisibleItems} tersisa)",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    } else if (!uiState.isDeepScanning) {
        // Empty State saat belum memindai
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = AccentCyan.copy(alpha = 0.12f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Explore, null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Katalog Sysfs Belum Dipindai",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sentuh tombol 'Pindai Sysfs' di atas untuk mendeteksi seluruh parameter kernel, OEM, dan vendor yang tersedia secara adaptif di perangkat Anda.",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // Modal Uji Node Manual (dengan preset cepat)
    if (showManualNodeDialog) {
        val quickPresets = listOf(
            "/proc/sys/vm/swappiness",
            "/proc/sys/kernel/sched_latency_ns",
            "/sys/block/mmcblk0/queue/read_ahead_kb",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        )

        AlertDialog(
            onDismissRequest = { showManualNodeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Uji Path Node Sysfs / Procfs", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Ketik path node kernel lengkap untuk diperiksa dan diubah secara langsung oleh Lynx Engine:",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    OutlinedTextField(
                        value = manualNodePath,
                        onValueChange = { manualNodePath = it },
                        placeholder = { Text("/proc/sys/... atau /sys/...", fontSize = 11.sp, color = TextTertiary) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgDeepOled,
                            unfocusedContainerColor = BgDeepOled
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Preset Cepat:",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    quickPresets.forEach { preset ->
                        Surface(
                            onClick = { manualNodePath = preset },
                            shape = RoundedCornerShape(6.dp),
                            color = BgElevated,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = preset,
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualNodePath.isNotBlank()) {
                            viewModel.inspectManualNode(manualNodePath.trim())
                            manualNodePath = ""
                            showManualNodeDialog = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDeepOled)
                ) {
                    Text("Periksa Node", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualNodeDialog = false }) {
                    Text("Tutup", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }
}

/**
 * TAB 2: ALAT KERNEL & LOG
 * Mengintegrasikan:
 * 1. Hero Identitas Kernel & Status Crash
 * 2. Utilitas Pemeliharaan 1-Sentuh
 * 3. Pengelola Skrip Shell & Boot Kustom
 * 4. Konsol Log Kernel Ring Buffer (dmesg)
 */
@Composable
fun KernelToolsTabContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    // 1. Hero Identitas Kernel & Status Crash
    KernelIdentityHeroCard(
        uiState = uiState,
        onRefresh = { viewModel.refreshKernelSummary() }
    )

    Spacer(Modifier.height(10.dp))

    // 2. Utilitas Pemeliharaan 1-Sentuh
    KernelQuickMaintenanceCard(
        uiState = uiState,
        onAction = { viewModel.runQuickMaintenance(it) }
    )

    Spacer(Modifier.height(10.dp))

    // 3. Pengelola Skrip Shell & Boot Kustom
    CustomScriptManagerCard(
        scripts = uiState.customScripts,
        onExecute = { viewModel.executeCustomScript(it) },
        onSave = { viewModel.saveCustomScript(it) },
        onDelete = { viewModel.deleteCustomScript(it) }
    )

    Spacer(Modifier.height(10.dp))

    // 4. Penampil Log dmesg Ring Buffer
    DmesgViewerCard(
        dmesgState = uiState.dmesgState,
        onLoadLogs = { viewModel.loadDmesgLog(it) },
        onExport = { viewModel.exportDmesgLog() }
    )
}

/**
 * 1. Hero Identitas Kernel & Status Crash
 * Dilengkapi Bento Telemetri 4-Tile, Status Crash PStore/Ramoops, dan Grid Kapabilitas Seimbang tanpa gesture trap.
 */
@Composable
fun KernelIdentityHeroCard(
    uiState: LynxUiState,
    onRefresh: () -> Unit
) {
    val summary = uiState.kernelSummary

    LaunchedEffect(Unit) {
        if (summary.kernelRelease.isBlank()) {
            onRefresh()
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = BgCard,
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentCyan.copy(alpha = 0.5f),
                    BorderSubtle
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Status Indicator & Refresh Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentCyan.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Identitas Kernel & Stabilitas Crash",
                            color = TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = summary.kernelRelease.ifBlank { System.getProperty("os.version") ?: "Linux Kernel" },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summary.isLoading) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, "Segarkan Ringkasan", tint = AccentCyan, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 4-Tile Bento Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tile 1: Versi Kernel
                val kernelVerShort = remember(summary.kernelRelease) {
                    summary.kernelRelease.substringBefore("-").ifBlank { "Linux" }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VERSI KERNEL",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = kernelVerShort,
                            color = AccentCyan,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Tile 2: Status Crash
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "STATUS CRASH",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (summary.hasPanicLog) "PANIC" else "STABIL",
                            color = if (summary.hasPanicLog) AccentRed else AccentGreen,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Tile 3: Uptime
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "UPTIME",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = summary.uptimeFormatted.ifBlank { "Aktif" }.take(10),
                            color = AccentBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Tile 4: Kompilator
                val compilerShort = remember(summary.compiler) {
                    when {
                        summary.compiler.contains("Clang", true) -> "CLANG"
                        summary.compiler.contains("GCC", true) -> "GCC"
                        else -> summary.architecture.ifBlank { "ARM64" }.uppercase()
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "KOMPILATOR",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = compilerShort,
                            color = AccentPurple,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Crash & Panic Status Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (summary.hasPanicLog) AccentRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.10f),
                border = BorderStroke(
                    0.6.dp,
                    if (summary.hasPanicLog) AccentRed.copy(alpha = 0.4f) else AccentGreen.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (summary.hasPanicLog) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (summary.hasPanicLog) AccentRed else AccentGreen,
                        modifier = Modifier.size(17.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.panicStatus,
                            color = if (summary.hasPanicLog) AccentRed else AccentGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (summary.hasPanicLog) {
                                "Ditemukan log ramoops/last_kmsg di pstore. Periksa dmesg untuk analisis."
                            } else {
                                "PStore & console-ramoops bersih, kernel berjalan tanpa kegagalan kritis."
                            },
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 13.5.sp
                        )
                    }
                }
            }

            // Capability Badges (Grid 3-Kolom Seimbang TANPA horizontalScroll)
            if (summary.capabilities.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.capabilities.chunked(3).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chunk.forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (badge.isSupported) AccentCyan.copy(alpha = 0.12f) else BgElevated,
                                    border = BorderStroke(
                                        0.6.dp,
                                        if (badge.isSupported) AccentCyan.copy(alpha = 0.35f) else BorderSubtle
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                                    ) {
                                        Text(
                                            text = (if (badge.isSupported) "✓ " else "✗ ") + badge.label,
                                            color = if (badge.isSupported) AccentCyan else TextTertiary,
                                            fontSize = 9.5.sp,
                                            fontWeight = if (badge.isSupported) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (chunk.size < 3) {
                                repeat(3 - chunk.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MaintenanceToolItem(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * 2. Utilitas Pemeliharaan 1-Sentuh
 */
@Composable
fun KernelQuickMaintenanceCard(
    uiState: LynxUiState,
    onAction: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = BgCard,
            title = {
                Text(
                    text = "Kembalikan ke Parameter Kernel Stock?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Text(
                    text = "Tindakan ini akan mengembalikan seluruh governor CPU, frekuensi scaling, limit GPU, dan menghapus kunci watchdog darurat kembali ke setelan aman standar pabrik (OEM).",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onAction("reset_to_stock")
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = TextPrimary)
                ) {
                    Text("Pulihkan ke Stock", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LynxCard(
        title = "Utilitas Pemeliharaan 1-Sentuh",
        icon = Icons.Default.Handyman,
        accentColor = AccentCyan
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Optimasi cepat dan pemulihan instan subsistem kernel tanpa perlu me-reboot perangkat.",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Status output eksekusi terakhir
            uiState.lastMaintenanceOutput?.let { lastOut ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                        Text(
                            text = lastOut,
                            color = TextPrimary,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Grid 2 Kolom untuk 6 utilitas
            val tools = listOf(
                MaintenanceToolItem(
                    id = "drop_caches",
                    title = "Bersihkan Cache RAM",
                    desc = "Drop pagecache & inodes",
                    icon = Icons.Default.CleaningServices,
                    accent = AccentCyan
                ),
                MaintenanceToolItem(
                    id = "fstrim",
                    title = "TRIM Penyimpanan",
                    desc = "Optimasi blok UFS / eMMC",
                    icon = Icons.Default.Storage,
                    accent = AccentBlue
                ),
                MaintenanceToolItem(
                    id = "compact_memory",
                    title = "Kompaksi RAM",
                    desc = "Reduksi fragmentasi kernel",
                    icon = Icons.Default.Compress,
                    accent = AccentGreen
                ),
                MaintenanceToolItem(
                    id = "restart_audioserver",
                    title = "Restart Audioserver",
                    desc = "Atasi audio lag & glitch",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    accent = AccentPurple
                ),
                MaintenanceToolItem(
                    id = "flush_logs",
                    title = "Flush Buffer Log",
                    desc = "Reset dmesg & logcat",
                    icon = Icons.Default.LayersClear,
                    accent = AccentCyan
                ),
                MaintenanceToolItem(
                    id = "reset_to_stock",
                    title = "Reset Kernel Stock",
                    desc = "Kembalikan ke standar OEM",
                    icon = Icons.Default.Restore,
                    accent = AccentOrange
                )
            )

            for (row in tools.chunked(2)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (tool in row) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(0.7.dp, tool.accent.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(86.dp),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (tool.id == "reset_to_stock") {
                                    showResetDialog = true
                                } else {
                                    onAction(tool.id)
                                }
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(tool.icon, null, tint = tool.accent, modifier = Modifier.size(18.dp))
                                    if (uiState.isMaintenanceExecuting) {
                                        CircularProgressIndicator(color = tool.accent, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Column {
                                    Text(
                                        text = tool.title,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = tool.desc,
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 1
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



/**
 * Swipeable Tab Pager untuk Halaman Subsystem & Deep Tunables.
 * Mengintegrasikan LynxTabPager dengan 3 tab (Sistem & GSI, Deep Sysfs, Alat Kernel)
 * dan retensi scroll state mandiri per-halaman.
 */
@Composable
fun SystemSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    var selectedSystemTab by rememberSaveable { mutableIntStateOf(0) }
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page2State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LynxTabPager(
        pageCount = 3,
        selectedPage = selectedSystemTab.coerceIn(0, 2),
        onPageSelected = { selectedSystemTab = it },
        tabRow = { curPage, onSelect ->
            SystemTabRow(
                selectedTab = curPage,
                onSelectTab = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        },
        modifier = modifier
    ) { page ->
        val listState = when (page) {
            0 -> page0State
            1 -> page1State
            else -> page2State
        }
        LazyColumn(
            state = listState,
            flingBehavior = subscreenFling,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "system_p${page}_top") { Spacer(Modifier.height(4.dp)) }
            item(key = "subscreen_system_tab_$page") {
                SystemSubscreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    targetTab = page
                )
            }
            item(key = "system_p${page}_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}

/**
 * Backward compatibility alias untuk legacy call sites.
 */
@Composable
fun TuningSystemCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit = {},
    onEditRuleClick: (AppProfileRule) -> Unit = {},
    modifier: Modifier = Modifier
) {
    SystemSubscreen(
        uiState = uiState,
        viewModel = viewModel,
        targetTab = 0,
        modifier = modifier
    )
}
