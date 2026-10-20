package com.noir.lynx.designsystem.profile

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setModeFeedbackStyle
import com.noir.lynx.ui.setModeFeedbackDualMorph

/**
 * ProfilesFeedbackBottomSheet — Elegant Dark Minimal ModalBottomSheet for Notification & Feedback Style Selection.
 * Refactored for visual calm, anti-slop clarity, and modular file budget compliance.
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
        containerColor = BgElevated,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = BorderGlass,
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.size(width = 36.dp, height = 3.dp)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 6.dp, bottom = 32.dp)
        ) {
            // ── Header ─────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AccentCyan.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = AccentCyan.copy(alpha = 0.85f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        text = "Gaya Notifikasi & Umpan Balik",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "Pilih indikator visual saat profil performa beralih",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // ── Option List (Zen Minimalist & Truthful Copy) ───
            val feedbackOptions = listOf(
                Triple("jewel_top", "Kapsul Melayang (Atas)", "Indikator dinamis melayang di bagian atas layar"),
                Triple("jewel_bottom", "Kapsul Melayang (Bawah)", "Indikator dinamis melayang di bagian bawah layar"),
                Triple("system_toast", "Toast Standar Android", "Notifikasi teks bawaan sistem operasi"),
                Triple("status_bar", "Status Bar Saja", "Pemberitahuan senyap di bilah status tanpa overlay pop-up"),
                Triple("disabled", "Nonaktifkan Feedback", "Hening total — tanpa notifikasi atau pop-up visual")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                feedbackOptions.forEach { (key, title, subtitle) ->
                    val isSelected = uiState.modeFeedbackStyle == key
                    Surface(
                        onClick = {
                            viewModel.setModeFeedbackStyle(context, key)
                            when (key) {
                                "jewel_top", "jewel_bottom" -> {
                                    viewModel.triggerModeFeedback("balance", "Seimbang", 0xFF00A3FFL)
                                }
                                "system_toast" -> {
                                    viewModel.dismissModeFeedback()
                                    android.widget.Toast.makeText(context, "Lynx: Mode Seimbang", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                "status_bar" -> {
                                    viewModel.dismissModeFeedback()
                                    viewModel.triggerUniversalToast("Status Bar Saja (Hening)", statusTag = "HENING", type = com.noir.lynx.data.ToastType.INFO, accentColorHex = 0xFF00E5FF, durationMs = 1500L)
                                }
                                "disabled" -> {
                                    viewModel.dismissModeFeedback()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.09f) else BgElevated,
                        border = BorderStroke(
                            if (isSelected) 1.dp else 0.6.dp,
                            if (isSelected) AccentCyan.copy(alpha = 0.7f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) AccentCyan else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    if (key == "jewel_top") {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = AccentCyan.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "DEFAULT",
                                                color = AccentCyan.copy(alpha = 0.9f),
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.4.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(1.5.dp))
                                Text(
                                    text = subtitle,
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 9.5.sp,
                                    lineHeight = 13.sp
                                )
                            }

                            // Radio indicator
                            Box(
                                modifier = Modifier.size(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(1.5.dp, AccentCyan),
                                        modifier = Modifier.size(18.dp)
                                    ) {}
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentCyan,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, BorderGlass),
                                        modifier = Modifier.size(18.dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = BorderSubtle,
                thickness = 0.7.dp,
                modifier = Modifier.padding(vertical = 14.dp)
            )

            // ── Dual Morph Switch ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Sinkronisasi Morpher Header",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(1.5.dp))
                    Text(
                        text = "Teks header ikut bertransisi serasi saat notifikasi aktif",
                        color = TextSecondary.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = uiState.modeFeedbackDualMorph,
                        onCheckedChange = { viewModel.setModeFeedbackDualMorph(context, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BgDeepOled,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = BgElevated,
                            uncheckedBorderColor = BorderSubtle
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Live Test Button ───────────────────────────────
            Surface(
                onClick = {
                    val sampleModes = listOf(
                        Triple("powersave", "Hemat", 0xFF00E676L),
                        Triple("balance", "Seimbang", 0xFF00A3FFL),
                        Triple("performance", "Performa", 0xFFFF9100L),
                        Triple("extreme", "Ekstrem", 0xFFFF3D00L)
                    )
                    val randomSample = sampleModes.random()

                    when (uiState.modeFeedbackStyle) {
                        "jewel_top", "jewel_bottom" -> {
                            viewModel.triggerModeFeedback(randomSample.first, randomSample.second, randomSample.third)
                        }
                        "system_toast" -> {
                            android.widget.Toast.makeText(context, "Lynx: Mode ${randomSample.second}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        "status_bar" -> {
                            viewModel.triggerUniversalToast("Status Bar Saja (Hening)", statusTag = "HENING", type = com.noir.lynx.data.ToastType.INFO, accentColorHex = 0xFF00E5FF, durationMs = 1500L)
                        }
                        "disabled" -> {
                            viewModel.triggerUniversalToast("Feedback Dinonaktifkan (Hening Total)", statusTag = "OFF", type = com.noir.lynx.data.ToastType.INFO, accentColorHex = 0xFF888888, durationMs = 1500L)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Uji Coba Animasi Toast (Live Test)",
                        color = AccentCyan,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
