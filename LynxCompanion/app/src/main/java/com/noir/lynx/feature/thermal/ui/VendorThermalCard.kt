package com.noir.lynx.feature.thermal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.hardware.VendorThermalConfigManager
import com.noir.lynx.hardware.VendorThermalStudioState
import kotlinx.coroutines.launch

/**
 * VendorThermalCard — Clean, non-redundant Vendor Thermal Config Status Card.
 * Replaces the overcrowded 1600+ line studio with a calm, streamlined overview
 * of live bind mount status, target vendor INI file, and quick raw editor trigger.
 */
@Composable
fun VendorThermalCard(
    currentTripTempC: Int,
    onOpenRawEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var studioState by remember { mutableStateOf<VendorThermalStudioState?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    fun refresh() {
        coroutineScope.launch {
            studioState = VendorThermalConfigManager.detectState()
        }
    }

    LaunchedEffect(currentTripTempC) {
        refresh()
    }

    val state = studioState

    LynxCard(
        title = "Konfigurasi Termal Vendor",
        icon = Icons.Default.Layers,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        if (state == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = AccentCyan
                )
            }
            return@LynxCard
        }

        if (!state.isSupported) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Arsitektur perangkat ini menggunakan Generic Linux CPUFreq governor tanpa berkas konfigurasi termal vendor eksternal.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            return@LynxCard
        }

        val archName = when (state.vendorArchitecture) {
            "qcom" -> "Qualcomm Snapdragon"
            "mtk" -> "MediaTek Dimensity / Helio"
            else -> state.vendorArchitecture.uppercase()
        }

        val filePath = state.stockConfigPath.ifEmpty { state.overlayConfigPath }
        val isMounted = state.isLiveBindMounted || state.isOverlayActive

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Status & Info Surface
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, if (isMounted) AccentCyan.copy(alpha = 0.4f) else BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isMounted) AccentCyan else AccentGreen)
                            )
                            Text(
                                text = archName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isMounted) AccentCyan.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, if (isMounted) AccentCyan.copy(alpha = 0.5f) else AccentGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (state.isLiveBindMounted) "LIVE BIND MOUNT" else if (state.isOverlayActive) "OVERLAY AKTIF" else "BASELINE OEM",
                                color = if (isMounted) AccentCyan else AccentGreen,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Target path display
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgSurfaceLowest.copy(alpha = 0.6f),
                        border = BorderStroke(0.5.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = filePath,
                            color = TextSecondary.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    // Reassurance note
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Trip point vendor otomatis disinkronkan ke ${currentTripTempC}°C di latar belakang.",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            AnimatedVisibility(visible = actionMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = actionMessage.orEmpty(),
                        color = AccentCyan,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isOperating = true
                        coroutineScope.launch {
                            val res = VendorThermalConfigManager.resetToStockBaseline()
                            actionMessage = if (res.isSuccess) "Baseline partisi vendor dipulihkan." else "Gagal mereset: ${res.exceptionOrNull()?.message}"
                            refresh()
                            isOperating = false
                        }
                    },
                    enabled = !isOperating,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.7.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Reset OEM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        onOpenRawEditor(state.rawContent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.18f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Editor Raw INI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
