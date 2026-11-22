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

    // 1. Hero Card Status Proteksi Sistem & Kernel
    SystemHeroCard(uiState)

    // 2. Kartu SELinux & Keamanan Kernel
    LynxCard(
        title = "Mode SELinux & Keamanan Kernel",
        icon = Icons.Default.Shield,
        accentColor = AccentCyan
    ) {
        val isEnforcing = uiState.selinuxMode.equals("Enforcing", ignoreCase = true)

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
                        "Enforcing: Kebijakan keamanan resmi Android aktif melindungi partisi sistem dari modifikasi tidak sah."
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
                        showPermissiveWarningDialog = true
                    } else {
                        viewModel.setSelinuxMode(true)
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

        // Peredam Log Konsol Kernel (Printk Suppressor)
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
                onCheckedChange = { viewModel.setPrintkSilent(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgElevated
                )
            )
        }
    }

    // 3. Kartu Kompatibilitas Android GSI & Treble
    LynxCard(
        title = "Kompatibilitas Android GSI & Treble",
        icon = Icons.Default.Build,
        accentColor = AccentPurple
    ) {
        val isTrebleDetected = uiState.audioTelemetry.isPhhGsiDetected || uiState.audioTelemetry.isTrebleEnabled

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Deteksi Framework:",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isTrebleDetected) AccentPurple.copy(alpha = 0.18f) else Color(0x15FFFFFF),
                border = BorderStroke(0.6.dp, if (isTrebleDetected) AccentPurple.copy(alpha = 0.45f) else BorderSubtle)
            ) {
                Text(
                    text = if (isTrebleDetected) "Android GSI / Treble Terdeteksi" else "Universal Linux / Stock Framework",
                    color = if (isTrebleDetected) AccentPurple else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
        Spacer(Modifier.height(10.dp))

        // System Bluetooth Audio HAL
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Bluetooth Audio HAL (GSI Fix)",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Mengalihkan audio Bluetooth dari vendor HAL bermasalah ke System HAL bawaan AOSP. Mengatasi masalah headset Bluetooth tidak mengeluarkan suara sama sekali di ROM GSI.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = uiState.state.audio.systemBtAudioHalEnabled,
                onCheckedChange = { viewModel.setAudioSystemBtAudioHal(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = AccentPurple,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgElevated
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
        Spacer(Modifier.height(10.dp))

        // Bypass A2DP Hardware Offload
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bypass A2DP Hardware Offload",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Memindahkan pemrosesan audio Bluetooth dari DSP vendor ke CPU host stack. Mengatasi suara putus-putus, kresek (crackle), dan delay tinggi.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = uiState.state.audio.disableA2dpHwOffload,
                onCheckedChange = { viewModel.setAudioA2dpHwOffloadDisabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgElevated
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
        Spacer(Modifier.height(10.dp))

        // Disable Absolute Volume
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nonaktifkan Volume Mutlak (Absolute Volume)",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Memisahkan kontrol volume Android dari kontrol volume internal headset Bluetooth. Mengatasi suara headset yang melonjak terlalu keras atau tidak bisa dikecilkan.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = uiState.state.audio.disableAbsoluteVolume,
                onCheckedChange = { viewModel.setAudioDisableAbsoluteVolume(it) },
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

        // SBC HD Dual Channel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SBC HD Dual Channel Bitrate",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Menaikkan batas bitrate transmisi codec Bluetooth SBC standar hingga mendekati kualitas aptX/LDAC secara nirkabel.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = uiState.state.audio.sbcDualChannelHd,
                onCheckedChange = { viewModel.setAudioSbcDualChannelHd(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = AccentGreen,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgElevated
                )
            )
        }
    }

    // 4. Kartu Opsi Pengembang & Daya Lanjutan
    LynxCard(
        title = "Opsi Pengembang & Daya Lanjutan",
        icon = Icons.Default.SettingsEthernet,
        accentColor = AccentOrange
    ) {
        // Wireless Debugging (ADB TCP Port 5555)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wireless Debugging (ADB Port ${uiState.adbPort})",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Membuka port TCP 5555 untuk koneksi ADB nirkabel ke PC atau Termux tanpa kabel USB. Status tetap aktif melintasi reboot jika diaktifkan.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = uiState.adbKeepAliveEnabled,
                onCheckedChange = { viewModel.setAdbKeepAlive(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = AccentOrange,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgElevated
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.6.dp)
        Spacer(Modifier.height(10.dp))

        // Pemicu Deep Sleep Instan (Doze Idle Force)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Uji Coba Deep Sleep (Doze Trigger)",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Memaksa sistem Android masuk ke mode idle tidur lelap instan untuk menguji penghentian wakelock latar belakang.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.5.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    com.topjohnwu.superuser.Shell.cmd("dumpsys deviceidle force-idle 2>/dev/null; echo ok").submit {
                        Toast.makeText(context, "Sistem dipaksa masuk ke status Doze Idle", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange.copy(alpha = 0.18f),
                    contentColor = AccentOrange
                ),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Trigger Doze", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

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
 * Mengintegrasikan Pengelola Skrip Shell Boot (Custom Script Manager)
 * dan Penampil Log Ring Buffer Kernel (dmesg).
 */
@Composable
fun KernelToolsTabContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    // 1. Pengelola Skrip Kustom Boot
    CustomScriptManagerCard(
        scripts = uiState.customScripts,
        onExecute = { viewModel.executeCustomScript(it) },
        onSave = { viewModel.saveCustomScript(it) },
        onDelete = { viewModel.deleteCustomScript(it) }
    )

    Spacer(Modifier.height(10.dp))

    // 2. Penampil Log dmesg Ring Buffer
    DmesgViewerCard(
        dmesgState = uiState.dmesgState,
        onLoadLogs = { viewModel.loadDmesgLog(it) },
        onExport = { viewModel.exportDmesgLog() }
    )
}

/**
 * Hero Card Ringkas Status Perlindungan Sistem & Kernel.
 */
@Composable
private fun SystemHeroCard(uiState: LynxUiState) {
    val platformName = when (uiState.gpuInfo.platform) {
        "adreno" -> "Qualcomm Snapdragon"
        "mali_ged", "mali_kbase" -> "MediaTek Dimensity/Helio"
        else -> "Universal Linux Kernel"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    AccentCyan.copy(alpha = 0.28f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        text = "Subsystem & Kernel Safeguards",
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Root Verified",
                        color = AccentCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.10f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.25f)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "Guardrails Aktif • SELinux ${uiState.selinuxMode}",
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
