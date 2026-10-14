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
import com.noir.lynx.core.locale.LynxText as Text
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

@Composable
fun VoltageControlCard(
    voltageInfo: VoltageTableInfo,
    onApplyOffset: (Int) -> Unit,
) {
    if (!voltageInfo.isSupported) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Voltage Control (Undervolting)",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "OEM Locked (Tidak didukung hardware/kernel ini)",
                            color = TextTertiary,
                            fontSize = 9.5.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text(
                        text = "LOCKED",
                        color = TextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
        return
    }

    var offsetMv by remember { mutableIntStateOf(voltageInfo.globalOffsetMv) }
    var lastVoltageTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(voltageInfo.globalOffsetMv) {
        if (System.currentTimeMillis() - lastVoltageTouch > 3500L) {
            offsetMv = voltageInfo.globalOffsetMv
        }
    }

    LynxCard(
        title = "Voltage Control (Undervolting)",
        icon = Icons.Default.Bolt,
        accentColor = AccentCyan
    ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Global Undervolt Offset", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "${if (offsetMv > 0) "+" else ""}$offsetMv mV",
                        color = if (offsetMv < 0) AccentCyan else if (offsetMv > 0) AccentRed else TextSecondary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = offsetMv.toFloat(),
                    onValueChange = {
                        offsetMv = it.toInt()
                        lastVoltageTouch = System.currentTimeMillis()
                    },
                    valueRange = -100f..50f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = BgElevated
                    )
                )
                LynxActionButton(
                    text = "Terapkan Offset Voltase (${offsetMv} mV)",
                    icon = Icons.Default.Check,
                    onClick = {
                        lastVoltageTouch = System.currentTimeMillis()
                        onApplyOffset(offsetMv)
                    },
                    accentColor = AccentCyan
                )
            }
        }
}

// ============================================================
//  FKM PARITY: DISPLAY CALIBRATION CARD (KCAL & HBM)
// ============================================================

// ============================================================
//  GPU MASTER TUNER & TELEMETRY CARD (DUAL-PILL FREQUENCY)
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryCenterBottomSheet(
    recoveryInfo: com.noir.lynx.engine.RecoveryInfo,
    onRestoreLastKnownGood: () -> Unit,
    onRestoreOemDefault: () -> Unit,
    onEmergencyDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333846)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Pusat Pemulihan CPU",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Recovery Center & Safety Layer",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Checkpoint Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("STATUS CHECKPOINT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Profil Terakhir:", fontSize = 12.sp, color = TextSecondary)
                        Text(recoveryInfo.activeProfileName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Checkpoint Tersimpan:", fontSize = 12.sp, color = TextSecondary)
                        Text(recoveryInfo.lastCheckpointTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentGreen)
                    }
                }
            }

            Text("PILIH TINDAKAN PEMULIHAN", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp)

            // Action 1: Last Known Good
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onRestoreLastKnownGood()
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pulihkan Konfigurasi Terakhir", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Kembalikan ke checkpoint profil stabil yang terakhir tersimpan.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // Action 2: Restore OEM Default
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onRestoreOemDefault()
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kembalikan ke Default Pabrik (OEM)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Hapus seluruh modifikasi Lynx dan serahkan kontrol ke OEM.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            // Action 3: Emergency Disable
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentRed.copy(alpha = 0.1f),
                border = BorderStroke(1.2.dp, AccentRed.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onEmergencyDisable()
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nonaktifkan Semua Tweak Darurat", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                        Text("Matikan seluruh intervensi CPU seketika tanpa perlu reboot.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================
//  PROTECTED TASKS DIALOG
// ============================================================

@Composable
fun ProtectedTasksDialog(
    tasks: List<com.noir.lynx.safety.ProtectedProcess>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Proses Sistem Dilindungi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Protected Tasks Guard", fontSize = 11.5.sp, color = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Proses-proses berikut diproteksi secara permanen oleh Lynx Safety Guard. Mereka tidak dapat dibatasi ke Little Core saja demi mencegah crash, lag gesture, atau freeze UI.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary
                )
                tasks.forEach { task ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(task.displayName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (task.isLocked) AccentRed.copy(0.15f) else AccentBlue.copy(0.15f),
                                    border = BorderStroke(0.6.dp, if (task.isLocked) AccentRed.copy(0.4f) else AccentBlue.copy(0.4f))
                                ) {
                                    Text(
                                        text = if (task.isLocked) "TERKUNCI" else "PENTING",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (task.isLocked) AccentRed else AccentBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(task.description, fontSize = 10.5.sp, color = TextSecondary, lineHeight = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Tutup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}




