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
    val context = LocalContext.current
    var showPermissiveWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshSystemFrameworkSummary()
    }

    // 1. Hero Status Framework & Keamanan OS
    SystemFrameworkHeroCard(
        uiState = uiState,
        onRefresh = { viewModel.refreshSystemFrameworkSummary() }
    )

    Spacer(Modifier.height(10.dp))

    // 2. Keamanan & Penegakan Kernel (SELinux & Printk)
    KernelSecurityCard(
        uiState = uiState,
        onSetSelinux = { viewModel.setSelinuxMode(it) },
        onSetPrintkSilent = { viewModel.setPrintkSilent(it) },
        onShowWarningDialog = { showPermissiveWarningDialog = true }
    )

    Spacer(Modifier.height(10.dp))

    // 3. Penyetelan Kelancaran UI & Framework Android (Animasi, Blurs, Phantom Procs)
    AndroidFrameworkUiCard(
        uiState = uiState,
        onSetAnimationScale = { viewModel.setAnimationScale(it) },
        onSetDisableWindowBlurs = { viewModel.setDisableWindowBlurs(it) },
        onSetDisablePhantomProcesses = { viewModel.setDisablePhantomProcesses(it) }
    )

    Spacer(Modifier.height(10.dp))

    // 4. Kompatibilitas Murni Android GSI & Treble (QEMU Mainkeys, Speed-Profile Dexopt)
    GsiTrebleFrameworkCard(
        uiState = uiState,
        onSetForceShowNavbar = { viewModel.setForceShowNavbar(it) },
        onRunDexopt = { viewModel.runArtSpeedProfileDexopt() }
    )

    Spacer(Modifier.height(10.dp))

    // 5. Diagnostik Daya & Doze Subsystem (Uji Doze Deep Sleep)
    DozePowerDiagnosticCard(
        onTriggerDoze = {
            com.topjohnwu.superuser.Shell.cmd("dumpsys deviceidle force-idle 2>/dev/null; echo ok").submit {
                Toast.makeText(context, "Sistem dipaksa masuk ke status Doze Idle", Toast.LENGTH_SHORT).show()
            }
        },
        onUnforceDoze = {
            com.topjohnwu.superuser.Shell.cmd("dumpsys deviceidle unforce 2>/dev/null; echo ok").submit {
                Toast.makeText(context, "Status Doze dipulihkan ke timer normal", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Modal Peringatan Beralih ke Permissive
    if (showPermissiveWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPermissiveWarningDialog = false },
            title = {
                Text("Peringatan Keamanan SELinux", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Mengubah SELinux ke mode Permissive akan menonaktifkan penegakan akses wajib Android. Modul root dan custom kernel tertentu mungkin membutuhkannya, namun perlindungan partisi sistem akan menurun. Lanjutkan?",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissiveWarningDialog = false
                        viewModel.setSelinuxMode(false)
                    }
                ) {
                    Text("Tetap Ubah ke Permissive", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissiveWarningDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard
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

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    Color(0xFF8B5CF6).copy(alpha = 0.35f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        Surface(shape = CircleShape, color = Color(0xFF8B5CF6).copy(alpha = 0.22f), modifier = Modifier.size(10.dp)) {}
                        Surface(shape = CircleShape, color = Color(0xFF8B5CF6), modifier = Modifier.size(5.dp)) {}
                    }
                    Text(
                        text = "Framework Android & Keamanan OS",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isEnforcing) AccentGreen.copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, if (isEnforcing) AccentGreen.copy(alpha = 0.4f) else AccentOrange.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isEnforcing) "SELinux: Enforcing" else "SELinux: Permissive",
                            color = if (isEnforcing) AccentGreen else AccentOrange,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (fw.isLoading) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, "Segarkan", tint = Color(0xFF8B5CF6), modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Android Version & Framework
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Android ${fw.androidVersion.ifBlank { android.os.Build.VERSION.RELEASE }} (API ${if (fw.apiLevel > 0) fw.apiLevel else android.os.Build.VERSION.SDK_INT})",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${fw.frameworkType} • ${fw.partitionProtection}",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
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
        // SELinux switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Status SELinux: ${uiState.selinuxMode}",
                        color = if (isEnforcing) AccentGreen else AccentOrange,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isEnforcing) AccentGreen.copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, if (isEnforcing) AccentGreen.copy(alpha = 0.4f) else AccentOrange.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isEnforcing) "AMAN" else "PERMISSIVE",
                            color = if (isEnforcing) AccentGreen else AccentOrange,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isEnforcing) {
                        "Enforcing: Kebijakan akses resmi Android aktif melindungi partisi sistem dari modifikasi tidak sah."
                    } else {
                        "Permissive: SELinux mengizinkan seluruh modul root berjalan tanpa batasan kebijakan keamanan."
                    },
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isEnforcing,
                onCheckedChange = { willEnforce ->
                    if (!willEnforce) {
                        onShowWarningDialog()
                    } else {
                        onSetSelinux(true)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = AccentGreen,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgElevated
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
        Spacer(Modifier.height(10.dp))

        // Peredam Log Konsol Kernel (Printk Silent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Peredam Log Konsol Kernel (Printk Silent)",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Meredam pencatatan log debug konsol kernel (0 0 0 0) saat sistem berjalan. Mencegah CPU sering terbangun dari Deep Sleep dan menghemat baterai standby.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = uiState.isPrintkSilent,
                onCheckedChange = onSetPrintkSilent,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgElevated
                )
            )
        }
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
    LynxCard(
        title = "Penyetelan Framework & Kelancaran UI",
        icon = Icons.Default.Speed,
        accentColor = Color(0xFF8B5CF6)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Optimasi rendering jendela dan manajemen proses sistem operasi Android.",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Skala Animasi Sistem 1-Ketukan
            Text(
                text = "Skala Animasi Sistem (Window, Transition, Animator):",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Mengubah durasi seluruh animasi jendela secara sinkron untuk responsivitas instan.",
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(8.dp))

            val animPresets = listOf(
                1.0f to "1.0x (Normal)",
                0.5f to "0.5x (Cepat)",
                0.25f to "0.25x (Kilat)",
                0.0f to "0x (Mati / Instan)"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((scale, label) in animPresets) {
                    val isSelected = kotlin.math.abs(uiState.animationScale - scale) < 0.05f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(
                            0.7.dp,
                            if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.6f) else BorderSubtle
                        ),
                        onClick = { onSetAnimationScale(scale) }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF8B5CF6) else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
            Spacer(Modifier.height(10.dp))

            // Nonaktifkan Window Blurs Sistem
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nonaktifkan Window Blurs Sistem",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Mematikan efek blur Gaussian pada notification shade dan app switcher di Android 12+. Sangat efektif menghilangkan micro-stutter pada GPU.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.5.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = uiState.disableWindowBlurs,
                    onCheckedChange = onSetDisableWindowBlurs,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = Color(0xFF8B5CF6),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgElevated
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
            Spacer(Modifier.height(10.dp))

            // Matikan Pembunuh Proses Phantom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Matikan Pembunuh Proses Phantom",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Menaikkan limit proses phantom menjadi tak terbatas (2147483647). Mencegah Termux, daemon Lynx, dan server lokal dibunuh paksa oleh Android 12+.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.5.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = uiState.disablePhantomProcesses,
                    onCheckedChange = onSetDisablePhantomProcesses,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentCyan,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgElevated
                    )
                )
            }
        }
    }
}

/**
 * 4. Kompatibilitas Murni Android GSI & Treble (QEMU Mainkeys, Speed-Profile Dexopt)
 */
@Composable
fun GsiTrebleFrameworkCard(
    uiState: LynxUiState,
    onSetForceShowNavbar: (Boolean) -> Unit,
    onRunDexopt: () -> Unit
) {
    LynxCard(
        title = "Kompatibilitas Murni Android GSI & Treble",
        icon = Icons.Default.Build,
        accentColor = AccentBlue
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Penyetelan tingkat sistem khusus untuk ROM Generic System Image (GSI) dan Project Treble.",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Bilah Navigasi Layar (QEMU Mainkeys)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Paksa Bilah Navigasi 3-Tombol (QEMU Mainkeys)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Mengontrol kemunculan tombol navigasi software (Back, Home, Recents) pada ROM GSI. Atasi tombol navigasi yang hilang atau dobel.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.5.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = uiState.forceShowNavbar,
                    onCheckedChange = onSetForceShowNavbar,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgElevated
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
            Spacer(Modifier.height(10.dp))

            // Kompilasi ART Speed-Profile Instan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kompilasi ART / Dexopt Cepat (Speed-Profile)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Mengompilasi bytecode aplikasi ke native code secara offline. Mempercepat pembukaan aplikasi dan menghilangkan micro-stutter saat baru ganti ROM.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.5.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onRunDexopt,
                    enabled = !uiState.isDexoptRunning,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue.copy(alpha = 0.18f),
                        contentColor = AccentBlue
                    ),
                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.45f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (uiState.isDexoptRunning) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Kompilasi...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Bolt, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Jalankan Dexopt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            uiState.lastDexoptOutput?.let { out ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = out,
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(8.dp)
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
        icon = Icons.Default.Bedtime,
        accentColor = AccentOrange
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Pengujian status tidur lelap Android (Doze) untuk mengecek apakah aplikasi latar belakang tertidur dengan benar.",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerDoze,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange.copy(alpha = 0.18f),
                        contentColor = AccentOrange
                    ),
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Bedtime, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Trigger Deep Sleep", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onUnforceDoze,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset Standar", fontSize = 11.sp)
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

    // 1. Scanner Hero Ribbon
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Katalog Sysfs Dinamis Universal",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Menampung seluruh parameter kernel & vendor yang tidak ada di halaman tuning khusus.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${uiState.deepTunables.size} Node",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons: Scan + Manual Node Inspector Modal Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.runDeepScan() },
                    enabled = !uiState.isDeepScanning,
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.2f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1.5f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (uiState.isDeepScanning) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Memindai...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (uiState.deepTunables.isEmpty()) "Pindai Sysfs" else "Pindai Ulang",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showManualNodeDialog = true },
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Terminal, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Uji Path", fontSize = 11.5.sp, color = TextPrimary)
                }
            }
        }
    }

    if (uiState.deepTunables.isNotEmpty()) {
        // 2. Filter Kategori Grid Seimbang (Non-Scrollable, BEBAS konflik gesture horizontal)
        val categories = listOf("Semua", "CPU / PPM", "RAM / VM", "GPU & Grafis", "Penyimpanan", "Sistem & Daya")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.chunked(3).forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chunk.forEach { catName ->
                        val isSel = activeCategoryFilter == catName
                        Surface(
                            onClick = {
                                activeCategoryFilter = catName
                                maxVisibleItems = 25
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(0.8.dp, if (isSel) AccentCyan else BorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                            ) {
                                Text(
                                    text = catName,
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

        // 3. Kolom Pencarian Cerdas
        OutlinedTextField(
            value = deepSearchQuery,
            onValueChange = {
                deepSearchQuery = it
                maxVisibleItems = 25
            },
            placeholder = { Text("Cari nama parameter, path, atau deskripsi...", fontSize = 11.sp, color = TextTertiary) },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            },
            trailingIcon = {
                if (deepSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { deepSearchQuery = "" }) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = BgElevated,
                unfocusedContainerColor = BgElevated
            )
        )

        // 4. Daftar Kartu Sysfs yang Difilter
        val displayedTunables = remember(uiState.deepTunables, deepSearchQuery, activeCategoryFilter) {
            uiState.deepTunables.filter { tunable ->
                val matchesCat = when (activeCategoryFilter) {
                    "Semua" -> true
                    "CPU / PPM" -> tunable.category.contains("CPU", true) || tunable.category.contains("PPM", true) || tunable.category.contains("Penjadwal", true)
                    "RAM / VM" -> tunable.category.contains("RAM", true) || tunable.category.contains("Memory", true) || tunable.category.contains("VM", true)
                    "GPU & Grafis" -> tunable.category.contains("GPU", true) || tunable.category.contains("Grafis", true)
                    "Penyimpanan" -> tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) || tunable.category.contains("Penyimpanan", true)
                    "Sistem & Daya" -> tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) || tunable.category.contains("Daya", true) || tunable.category.contains("General", true)
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
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.SearchOff, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tidak ada parameter yang cocok dengan kriteria",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
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
                Button(
                    onClick = { maxVisibleItems += 30 },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgElevated,
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Muat Lebih Banyak (${displayedTunables.size - maxVisibleItems} tersisa)", fontSize = 11.5.sp)
                }
            }
        }
    } else if (!uiState.isDeepScanning) {
        // Empty State saat belum memindai
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Explore, null, tint = AccentCyan, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Belum Ada Hasil Pemindaian Sysfs",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sentuh tombol 'Pindai Sysfs' di atas untuk mendeteksi seluruh parameter kernel, OEM, dan vendor yang tersedia secara adaptif di perangkat Anda.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        }
    }

    // Modal Uji Node Manual (disembunyikan dari tampilan utama)
    if (showManualNodeDialog) {
        AlertDialog(
            onDismissRequest = { showManualNodeDialog = false },
            title = {
                Text("Uji Path Node Sysfs / Procfs", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Ketik path node kernel lengkap untuk diperiksa dan diubah secara langsung oleh Lynx Engine:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = manualNodePath,
                        onValueChange = { manualNodePath = it },
                        placeholder = { Text("/proc/sys/... atau /sys/...", fontSize = 11.sp, color = TextTertiary) },
                        singleLine = true,
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
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentCyan.copy(alpha = 0.35f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        Surface(shape = CircleShape, color = AccentCyan.copy(alpha = 0.18f), modifier = Modifier.size(10.dp)) {}
                        Surface(shape = CircleShape, color = AccentCyan, modifier = Modifier.size(5.dp)) {}
                    }
                    Text(
                        text = "Identitas Kernel & Stabilitas Crash",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summary.isLoading) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, "Segarkan Ringkasan", tint = AccentCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Kernel Version & Architecture Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.3f)),
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
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.kernelRelease.ifBlank { System.getProperty("os.version") ?: "Linux Kernel" },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${summary.compiler} • ${summary.architecture} • Uptime: ${summary.uptimeFormatted.ifBlank { "Aktif" }}",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Crash & Panic Status Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
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
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (summary.hasPanicLog) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (summary.hasPanicLog) AccentRed else AccentGreen,
                        modifier = Modifier.size(16.dp)
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
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // Capability Badges
            if (summary.capabilities.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (badge in summary.capabilities) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (badge.isSupported) AccentCyan.copy(alpha = 0.12f) else BgElevated,
                            border = BorderStroke(
                                0.6.dp,
                                if (badge.isSupported) AccentCyan.copy(alpha = 0.35f) else BorderSubtle
                            )
                        ) {
                            Text(
                                text = (if (badge.isSupported) "✓ " else "✗ ") + badge.label,
                                color = if (badge.isSupported) AccentCyan else TextTertiary,
                                fontSize = 10.sp,
                                fontWeight = if (badge.isSupported) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                            )
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
                    icon = Icons.Default.VolumeUp,
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
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            border = BorderStroke(0.7.dp, tool.accent.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(82.dp),
                            onClick = {
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
