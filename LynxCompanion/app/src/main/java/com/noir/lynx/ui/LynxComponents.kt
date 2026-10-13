package com.noir.lynx.ui

import java.util.Locale
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.navigation.model.TuningCategory

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

@Composable
fun CpuClusterTunerCard(
    clusters: List<CpuClusterInfo>,
    governorTunables: Map<Int, List<GovernorTunable>> = emptyMap(),
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit = { _, _ -> },
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit = { _, _, _, _ -> },
    onApplyGovernorPreset: ((policyId: Int, preset: String) -> Unit)? = null,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit = { _, _, _, _ -> },
) {
    com.noir.lynx.feature.cpu.ui.cluster.CpuClusterTunerCard(
        clusters = clusters,
        governorTunables = governorTunables,
        onFreqChange = onFreqChange,
        onGovChange = onGovChange,
        onLoadTunables = onLoadTunables,
        onTunableChange = onTunableChange,
        onApplyGovernorPreset = onApplyGovernorPreset,
        onLockToggle = onLockToggle
    )
}

// ============================================================
//  FlasherCard — AnyKernel3 Kernel Flasher
// ============================================================

@Composable
fun FlasherCard(
    isFlashing: Boolean,
    flashLog: String,
    onFlash: (zipPath: String) -> Unit,
) {
    var zipInput by remember { mutableStateOf("/sdcard/Download/kernel.zip") }

    LynxCard(
        title = "AnyKernel3 Kernel Flasher",
        icon = Icons.Default.Bolt,
        accentColor = AccentCyan
    ) {
        Text(
            text = "Flash kernel kustom (ZIP) langsung dari sistem. Kernel lama akan otomatis dicadangkan sebelum proses penulisan.",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = zipInput,
            onValueChange = { zipInput = it },
            label = { Text("Path File ZIP Kernel") },
            placeholder = { Text("/sdcard/Download/kernel.zip") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BgElevated,
                unfocusedContainerColor = BgElevated,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        LynxActionButton(
            text = "Flash Kernel Sekarang",
            icon = Icons.Default.Bolt,
            isLoading = isFlashing,
            onClick = {
                if (zipInput.isNotBlank()) onFlash(zipInput.trim())
            },
            accentColor = AccentCyan
        )

        if (flashLog.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF090B10),
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
            ) {
                Text(
                    text = flashLog,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AccentCyan,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// ============================================================
//  BootBackupCard — Partition Backup & Restore
// ============================================================

@Composable
fun BootBackupCard(
    backups: List<BootBackupInfo>,
    isBackingUp: Boolean,
    onBackup: () -> Unit,
    onRestore: (path: String) -> Unit,
) {
    var restoreTarget by remember { mutableStateOf<BootBackupInfo?>(null) }

    if (restoreTarget != null) {
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            containerColor = BgCard,
            titleContentColor = AccentOrange,
            textContentColor = TextSecondary,
            title = { Text("Pulihkan Partisi Kernel?") },
            text = { Text("Apakah Anda yakin ingin memulihkan partisi dari backup '${restoreTarget?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreTarget?.let { onRestore(it.path) }
                        restoreTarget = null
                    }
                ) {
                    Text("Pulihkan", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    LynxCard(
        title = "Cadangan Partisi Boot",
        icon = Icons.Default.Storage,
        accentColor = AccentBlue
    ) {
        Text(
            text = "Cadangkan dan pulihkan partisi boot dan init_boot perangkat Anda kapan saja.",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LynxActionButton(
            text = "Buat Backup Boot Baru",
            icon = Icons.Default.Storage,
            isLoading = isBackingUp,
            onClick = onBackup,
            accentColor = AccentBlue
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "RIWAYAT BACKUP TERSIMPAN",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (backups.isEmpty()) {
            Text(
                text = "Belum ada backup partisi tersimpan.",
                fontSize = 12.sp,
                color = TextTertiary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                backups.forEach { b ->
                    val mb = (b.size / (1024f * 1024f))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = b.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = String.format("%.1f MB", mb),
                                    fontSize = 10.5.sp,
                                    color = TextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { restoreTarget = b },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f))
                            ) {
                                Text(text = "Restore", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  StandaloneModuleBanner — Golden Amber Frosted Glass Banner
// ============================================================

@Composable
fun StandaloneModuleBanner(onInstallModule: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1B150A),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    AccentOrange.copy(alpha = 0.6f),
                    AccentOrange.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentOrange.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
                Text(
                    text = "Modul Lynx Deity Belum Terpasang",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Aplikasi sedang berjalan dalam Standalone Root Mode. Seluruh Telemetri, CPU Cluster Tuner, dan AnyKernel Flasher aktif mandiri! Untuk mengaktifkan Smart-AI foreground daemon dan Charging Bypass, Anda dapat memasang modul root Lynx.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            LynxActionButton(
                text = "Pasang Modul Lynx Deity Sekarang",
                icon = Icons.Default.Build,
                onClick = onInstallModule,
                accentColor = AccentOrange
            )
        }
    }
}

// ============================================================
//  FKM PARITY: VOLTAGE CONTROL CARD
// ============================================================
